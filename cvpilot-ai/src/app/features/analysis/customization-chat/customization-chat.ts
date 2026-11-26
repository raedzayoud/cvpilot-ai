import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { AnalysisApi } from '../../../core/api/analysis-api.service';
import { saveBlob } from '../../../core/download';
import { messageFrom, messageFromBlobError } from '../../../core/http-error';
import {
  Analysis,
  AnalysisInsights,
  ChatMessage,
  CvDecision,
  Customization,
  DecisionAction,
  ExportFormat,
  Relevance,
  SectionProposal,
} from '../../../core/models';
import { ToastService } from '../../../core/toast.service';
import { AutoGrow } from '../../../shared/auto-grow/auto-grow';
import { Icon } from '../../../shared/icon/icon';
import { ScoreRing } from '../../../shared/score-ring/score-ring';
import { Step, StepRail } from '../../../shared/step-rail/step-rail';

const MAX_MESSAGE = 2000;

@Component({
  selector: 'app-customization-chat',
  imports: [FormsModule, RouterLink, StepRail, Icon, ScoreRing, AutoGrow],
  templateUrl: './customization-chat.html',
  styleUrl: './customization-chat.css',
})
export class CustomizationChat implements OnInit {
  private route = inject(ActivatedRoute);
  private analysisApi = inject(AnalysisApi);
  private toast = inject(ToastService);

  protected readonly maxMessage = MAX_MESSAGE;
  protected readonly analysis = signal<Analysis | null>(null);
  protected readonly steps = computed<Step[]>(() => {
    const exported = this.analysis()?.exported ?? false;
    return [
      { label: 'CV', state: 'done' },
      { label: 'Job', state: 'done' },
      { label: 'Chat', state: exported ? 'done' : 'current' },
      { label: 'Export', state: exported ? 'done' : 'upcoming' },
    ];
  });
  protected readonly customization = signal<Customization | null>(null);
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly insights = signal<AnalysisInsights | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly draft = signal('');
  protected readonly savingKey = signal<string | null>(null);
  protected readonly savingDecision = signal<string | null>(null);
  protected readonly format = signal<ExportFormat>('pdf');
  protected readonly exporting = signal(false);
  protected readonly suggestions = [
    'Why did I get this score?',
    'What are my biggest gaps?',
    'Which skills should I develop?',
  ];
  protected readonly actions: { value: DecisionAction; label: string }[] = [
    { value: 'highlight', label: 'Highlight' },
    { value: 'keep', label: 'Keep' },
    { value: 'deprioritize', label: 'Lower' },
    { value: 'remove', label: 'Remove' },
  ];
  protected readonly groups = computed(() => {
    const decisions = this.customization()?.decisions ?? [];
    const defs: { suggestion: Relevance; label: string }[] = [
      { suggestion: 'highlight', label: 'Keep & highlight' },
      { suggestion: 'keep', label: 'Keep' },
      { suggestion: 'deprioritize', label: 'Deprioritize' },
      { suggestion: 'suggest_remove', label: 'Suggested to remove' },
    ];
    return defs.map((def) => ({
      ...def,
      items: decisions.filter((decision) => decision.suggestion === def.suggestion),
    }));
  });
  protected readonly exportOptions: { value: ExportFormat; title: string; hint: string }[] = [
    { value: 'pdf', title: 'PDF', hint: 'Compiled from the LaTeX template, ready to send.' },
    { value: 'tex', title: 'LaTeX (.tex)', hint: 'The source code, to edit or compile in Overleaf.' },
    { value: 'docx', title: 'Word (.docx)', hint: 'Editable copy with the same structure.' },
  ];
  protected readonly canExport = computed(() => {
    const customization = this.customization();
    if (!customization) return false;
    return customization.sections.every(
      (section) => section.locked || (section.approved && section.violations.length === 0),
    );
  });
  protected readonly readyCount = computed(
    () =>
      this.customization()?.sections.filter((section) => section.locked || section.approved)
        .length ?? 0,
  );

  /** Last customization the server returned, used to tell local unsaved edits apart. */
  private serverCustomization: Customization | null = null;
  private readonly log = viewChild<ElementRef<HTMLDivElement>>('log');
  private analysisId = '';

  ngOnInit(): void {
    this.analysisId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      analysis: this.analysisApi.get(this.analysisId),
      chat: this.analysisApi.chat(this.analysisId),
    }).subscribe({
      next: ({ analysis, chat }) => {
        this.analysis.set(analysis);
        this.format.set('pdf');
        this.messages.set(chat.messages);
        this.serverCustomization = chat.customization;
        this.customization.set(chat.customization);
        this.insights.set(chat.insights);
        this.loading.set(false);
        this.scrollToEnd();
      },
      error: (error: unknown) => {
        this.error.set(messageFrom(error));
        this.loading.set(false);
      },
    });
  }

  protected onComposerKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send();
    }
  }

  protected ask(text: string): void {
    this.draft.set(text);
    this.send();
  }

  protected send(): void {
    const text = this.draft().trim();
    if (!text || this.sending()) return;
    if (text.length > MAX_MESSAGE) {
      this.toast.error(`Messages are limited to ${MAX_MESSAGE.toLocaleString()} characters.`);
      return;
    }

    this.draft.set('');
    this.sending.set(true);
    // Show the user's message immediately; the server response replaces the whole history.
    this.messages.update((list) => [
      ...list,
      { id: `pending-${Date.now()}`, role: 'user', text, at: new Date().toISOString() },
    ]);
    this.scrollToEnd();

    this.analysisApi.sendChat(this.analysisId, text).subscribe({
      next: (turn) => {
        this.messages.set(turn.messages);
        this.applyServer(turn.customization);
        this.insights.set(turn.insights);
        this.sending.set(false);
        this.scrollToEnd();
      },
      error: (error: unknown) => {
        this.sending.set(false);
        this.messages.update((list) => list.filter((m) => !m.id.startsWith('pending-')));
        this.draft.set(text);
        this.toast.error(messageFrom(error));
      },
    });
  }

  protected editSection(key: string, text: string): void {
    const current = this.customization();
    if (!current) return;
    const next = structuredClone(current);
    const section = next.sections.find((item) => item.key === key);
    if (!section) return;
    section.proposed = text;
    section.approved = false;
    section.violations = [];
    this.customization.set(next);
  }

  protected decide(decision: CvDecision, action: DecisionAction): void {
    if (this.savingDecision() || decision.action === action) return;
    this.savingDecision.set(decision.id);
    this.analysisApi.setDecision(this.analysisId, decision.id, action).subscribe({
      next: (customization) => {
        this.applyServer(customization);
        this.savingDecision.set(null);
      },
      error: (error: unknown) => {
        this.savingDecision.set(null);
        this.toast.error(messageFrom(error));
      },
    });
  }

  private applyServer(next: Customization): void {
    this.customization.set(this.keepUnsavedEdits(this.serverCustomization, this.customization(), next));
    this.serverCustomization = next;
  }

  /**
   * Re-applies text the user typed but has not yet checked, for sections whose server text did not
   * change. Otherwise an unrelated plan change would silently discard the edit.
   */
  private keepUnsavedEdits(
    before: Customization | null,
    local: Customization | null,
    next: Customization,
  ): Customization {
    if (!before || !local) return next;
    const result = structuredClone(next);
    for (const section of result.sections) {
      const was = before.sections.find((s) => s.key === section.key);
      const mine = local.sections.find((s) => s.key === section.key);
      if (!was || !mine || section.locked) continue;
      const editedLocally = mine.proposed !== was.proposed;
      const unchangedOnServer = section.proposed === was.proposed;
      if (editedLocally && unchangedOnServer) {
        section.proposed = mine.proposed;
        section.approved = false;
        section.violations = [];
      }
    }
    return result;
  }

  protected kindLabel(kind: CvDecision['kind']): string {
    return kind === 'skill' ? 'Skill' : kind === 'experience' ? 'Experience' : 'Project';
  }

  protected approve(section: SectionProposal): void {
    if (this.savingKey()) return;
    this.savingKey.set(section.key);
    this.analysisApi.approveSection(this.analysisId, section.key, section.proposed).subscribe({
      next: (customization) => {
        this.applyServer(customization);
        this.savingKey.set(null);
        const updated = customization.sections.find((item) => item.key === section.key);
        if (updated && updated.violations.length > 0) {
          this.toast.error('That text names something your CV does not show. Edit it and check again.');
        } else {
          this.toast.success(`${section.label} approved.`);
        }
      },
      error: (error: unknown) => {
        this.savingKey.set(null);
        this.toast.error(messageFrom(error));
      },
    });
  }

  protected download(): void {
    if (!this.canExport() || this.exporting()) return;
    const format = this.format();
    this.exporting.set(true);
    this.analysisApi.export(this.analysisId, format).subscribe({
      next: (blob) => {
        saveBlob(blob, this.fileName(format));
        this.exporting.set(false);
        this.analysis.update((a) => (a ? { ...a, exported: true } : a));
        this.toast.success(`Your ${format.toUpperCase()} is ready.`);
      },
      error: async (error: unknown) => {
        this.exporting.set(false);
        this.toast.error(await messageFromBlobError(error));
      },
    });
  }

  protected statusLabel(section: SectionProposal): string {
    if (section.locked) return 'Locked';
    if (section.approved) return 'Approved';
    if (section.violations.length > 0) return 'Needs edits';
    return 'Needs approval';
  }

  private fileName(format: ExportFormat): string {
    const safe = (this.analysis()?.jobTitle ?? 'CV')
      .replace(/[^\w-]+/g, '_')
      .replace(/^_+|_+$/g, '');
    return `${safe || 'CV'}_CVPilot.${format}`;
  }

  private scrollToEnd(): void {
    setTimeout(() => {
      const element = this.log()?.nativeElement;
      if (element) element.scrollTop = element.scrollHeight;
    });
  }
}
