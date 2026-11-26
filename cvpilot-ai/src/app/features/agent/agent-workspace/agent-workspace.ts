import { DatePipe } from '@angular/common';
import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AcceptEdit, AgentProposal, AgentSessionSummary, AgentWorkspace } from '../../../core/agent-models';
import { AgentApi } from '../../../core/api/agent-api.service';
import { ConfirmService } from '../../../core/confirm.service';
import { saveBlob } from '../../../core/download';
import { messageFrom, messageFromBlobError } from '../../../core/http-error';
import { ExportFormat } from '../../../core/models';
import { ToastService } from '../../../core/toast.service';
import { CvPreview } from '../../../shared/cv-preview/cv-preview';
import { Icon } from '../../../shared/icon/icon';
import { ProposalCard } from '../proposal-card/proposal-card';

type Pane = 'chat' | 'changes' | 'preview';

const INTENT_LABELS: Record<string, string> = {
  tailor_to_job: 'Tailor to job',
  optimize_ats: 'ATS optimization',
  rewrite: 'Rewrite',
  add_content: 'Add content',
  remove_content: 'Remove content',
  reorder: 'Reorder',
  question: 'Question',
  other: 'General',
};

const TOOL_LABELS: Record<string, string> = {
  analyze_job_description: 'Analyzed the job',
  record_user_fact: 'Recorded your fact',
  update_headline: 'Headline',
  rewrite_summary: 'Summary',
  set_skills: 'Skills',
  update_experience: 'Experience',
  add_experience: 'New experience',
  remove_experience: 'Removal',
  update_project: 'Project',
  add_project: 'New project',
  remove_project: 'Removal',
  reorder_sections: 'Section order',
  replace_technology: 'Technology swap',
};

@Component({
  selector: 'app-agent-workspace',
  imports: [DatePipe, FormsModule, RouterLink, Icon, ProposalCard, CvPreview],
  templateUrl: './agent-workspace.html',
  styleUrl: './agent-workspace.css',
})
export class AgentWorkspacePage implements OnInit {
  private api = inject(AgentApi);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  protected readonly ws = signal<AgentWorkspace | null>(null);
  protected readonly sessions = signal<AgentSessionSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly pendingText = signal<string | null>(null);
  protected readonly busyId = signal<string | null>(null);
  protected readonly busyAction = signal<string | null>(null);
  protected readonly draft = signal('');
  /** Mobile/tablet: which pane is visible. Desktop shows all. */
  protected readonly pane = signal<Pane>('chat');
  protected readonly side = signal<'changes' | 'preview'>('changes');
  protected readonly filter = signal<'pending' | 'all'>('pending');
  protected readonly showSessions = signal(false);
  protected readonly exportFormat = signal<ExportFormat>('pdf');

  protected readonly intentLabels = INTENT_LABELS;
  protected readonly suggestions = [
    'Tailor my CV for this job: (paste the job description)',
    'Rewrite my professional summary to emphasize backend development',
    'Rewrite my experience descriptions with strong action verbs',
    'Optimize my CV for ATS systems',
  ];

  protected readonly visibleProposals = computed(() => {
    const all = this.ws()?.proposals ?? [];
    const list = this.filter() === 'pending' ? all.filter((p) => p.status === 'pending') : all;
    return [...list].reverse();
  });
  protected readonly working = computed(() => this.busyAction() !== null || this.sending());

  private readonly log = viewChild<ElementRef<HTMLDivElement>>('log');
  private id = '';

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.id = params.get('id') ?? '';
      this.load();
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.get(this.id).subscribe({
      next: (ws) => {
        this.ws.set(ws);
        this.loading.set(false);
        this.loadSessions(ws.session.cvId);
        this.scrollToEnd();
      },
      error: (error: unknown) => {
        this.error.set(messageFrom(error));
        this.loading.set(false);
      },
    });
  }

  private loadSessions(cvId: string): void {
    this.api.list(cvId).subscribe({ next: (page) => this.sessions.set(page.items), error: () => this.sessions.set([]) });
  }

  // ------------------------------------------------------------------ conversation

  protected onKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send();
    }
  }

  protected useSuggestion(text: string): void {
    this.draft.set(text.replace(' (paste the job description)', '\n\n'));
    document.getElementById('agent-input')?.focus();
  }

  protected send(): void {
    const text = this.draft().trim();
    if (!text || this.working()) return;
    this.draft.set('');
    this.sending.set(true);
    this.pendingText.set(text);
    this.pane.set('chat');
    this.scrollToEnd();
    this.api.send(this.id, text).subscribe({
      next: (ws) => {
        const before = this.ws()?.stats.pending ?? 0;
        this.apply(ws);
        this.sending.set(false);
        this.pendingText.set(null);
        const created = ws.stats.pending - before;
        if (created > 0) this.toast.success(`${created} change${created > 1 ? 's' : ''} ready to review.`);
        this.scrollToEnd();
      },
      error: (error: unknown) => {
        this.sending.set(false);
        this.pendingText.set(null);
        this.draft.set(text);
        this.toast.error(messageFrom(error));
      },
    });
  }

  // ------------------------------------------------------------------ proposals

  protected acceptProposal(p: AgentProposal, edit?: AcceptEdit): void {
    this.decide(p.id, this.api.accept(p.id, edit), edit ? 'Your version was applied.' : 'Change applied to the working copy.');
  }

  protected rejectProposal(p: AgentProposal): void {
    this.decide(p.id, this.api.reject(p.id), 'Change rejected.');
  }

  protected regenerateProposal(p: AgentProposal): void {
    this.sending.set(true);
    this.pendingText.set(`Regenerate: ${p.change.label}`);
    this.decide(p.id, this.api.regenerate(p.id), 'Here is a new version.', () => {
      this.sending.set(false);
      this.pendingText.set(null);
    });
  }

  private decide(id: string, call: ReturnType<AgentApi['accept']>, success: string, done?: () => void): void {
    this.busyId.set(id);
    call.subscribe({
      next: (ws) => {
        this.apply(ws);
        this.busyId.set(null);
        done?.();
        this.toast.success(success);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        done?.();
        this.toast.error(messageFrom(error));
        this.load();
      },
    });
  }

  protected acceptAll(): void {
    const count = this.ws()?.stats.pending ?? 0;
    this.run('accept-all', this.api.acceptAll(this.id), `${count} change${count === 1 ? '' : 's'} applied.`);
  }

  protected async saveToCv(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Save these changes to your CV?',
      message: `The accepted changes will replace the profile of ${this.ws()?.session.cvFileName}. Other customizations will use the updated CV from now on. Your original uploaded file is kept unchanged.`,
      confirmLabel: 'Save to CV',
    });
    if (ok) this.run('save', this.api.save(this.id), 'Your CV was updated.');
  }

  protected async reset(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Discard accepted changes?',
      message: 'The working copy goes back to the CV as it was when this session started. Pending changes are discarded too.',
      confirmLabel: 'Discard changes',
      danger: true,
    });
    if (ok) this.run('reset', this.api.reset(this.id), 'Working copy restored.');
  }

  protected async deleteSession(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Delete this session?',
      message: 'The conversation and its proposals are deleted. Your CV is not affected.',
      confirmLabel: 'Delete session',
      danger: true,
    });
    if (!ok) return;
    this.api.delete(this.id).subscribe({
      next: () => {
        this.toast.success('Session deleted.');
        this.router.navigate(['/agent']);
      },
      error: (error: unknown) => this.toast.error(messageFrom(error)),
    });
  }

  protected download(): void {
    if (this.working()) return;
    const format = this.exportFormat();
    this.busyAction.set('export');
    this.api.export(this.id, format).subscribe({
      next: (blob) => {
        const base = (this.ws()?.session.cvFileName ?? 'CV').replace(/\.[^.]+$/, '').replace(/[^\w-]+/g, '_');
        saveBlob(blob, `${base}_AI_Agent.${format}`);
        this.busyAction.set(null);
        this.toast.success(`Your ${format.toUpperCase()} is ready.`);
      },
      error: async (error: unknown) => {
        this.busyAction.set(null);
        this.toast.error(await messageFromBlobError(error));
      },
    });
  }

  private run(action: string, call: ReturnType<AgentApi['save']>, success: string): void {
    if (this.working()) return;
    this.busyAction.set(action);
    call.subscribe({
      next: (ws) => {
        this.apply(ws);
        this.busyAction.set(null);
        this.toast.success(success);
      },
      error: (error: unknown) => {
        this.busyAction.set(null);
        this.toast.error(messageFrom(error));
      },
    });
  }

  private apply(ws: AgentWorkspace): void {
    this.ws.set(ws);
    this.sessions.update((list) => list.map((s) => (s.id === ws.session.id ? ws.session : s)));
  }

  // ------------------------------------------------------------------ view helpers

  protected toolLabels(tools: string[]): string[] {
    return [...new Set(tools.map((t) => TOOL_LABELS[t] ?? t))];
  }

  protected openSession(id: string): void {
    this.showSessions.set(false);
    if (id !== this.id) this.router.navigate(['/agent', id]);
  }

  private scrollToEnd(): void {
    setTimeout(() => {
      const el = this.log()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
