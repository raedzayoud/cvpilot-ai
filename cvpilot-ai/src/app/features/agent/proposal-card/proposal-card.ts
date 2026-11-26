import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AcceptEdit, AgentProposal } from '../../../core/agent-models';
import { diffWords } from '../../../core/text-diff';
import { Icon } from '../../../shared/icon/icon';

const OPERATION_LABELS: Record<string, string> = {
  update_headline: 'Headline',
  update_summary: 'Summary',
  update_skills: 'Skills',
  add_experience: 'New experience',
  update_experience: 'Experience',
  remove_experience: 'Removal',
  add_project: 'New project',
  update_project: 'Project',
  remove_project: 'Removal',
  reorder_sections: 'Layout',
  replace_technology: 'Technology',
  remove_section: 'Section removal',
};

/** One explainable change: original vs updated, why, job requirements, keyword deltas and evidence. */
@Component({
  selector: 'app-proposal-card',
  imports: [FormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './proposal-card.html',
  styleUrl: './proposal-card.css',
})
export class ProposalCard {
  proposal = input.required<AgentProposal>();
  busy = input(false);
  /** True while another action (e.g. an agent turn) runs, to avoid conflicting edits. */
  locked = input(false);

  accept = output<AcceptEdit | undefined>();
  reject = output<void>();
  regenerate = output<void>();

  protected readonly view = signal<'diff' | 'side'>('diff');
  protected readonly editing = signal(false);
  protected readonly draft = signal('');

  protected readonly change = computed(() => this.proposal().change);
  protected readonly pending = computed(() => this.proposal().status === 'pending');
  protected readonly kind = computed(() => OPERATION_LABELS[this.change().operation] ?? 'Change');
  protected readonly removal = computed(() => this.change().operation.startsWith('remove_'));
  protected readonly editable = computed(
    () => !this.removal() && this.change().operation !== 'reorder_sections' && this.change().operation !== 'replace_technology',
  );
  protected readonly diff = computed(() => diffWords(this.change().originalText, this.change().updatedText));
  protected readonly editHint = computed(() => {
    const op = this.change().operation;
    if (op === 'update_skills') return 'Comma-separated skills, in order.';
    if (op.endsWith('_experience')) return 'One bullet point per line.';
    if (op.endsWith('_project')) return 'Project description.';
    return 'Your wording.';
  });

  protected startEdit(): void {
    const c = this.change();
    const op = c.operation;
    let text = c.text ?? c.updatedText;
    if (op === 'update_skills') text = (c.list ?? []).join(', ');
    else if (op.endsWith('_experience')) text = (c.experience?.bullets ?? []).join('\n');
    else if (op.endsWith('_project')) text = c.project?.description ?? '';
    this.draft.set(text);
    this.editing.set(true);
  }

  protected saveEdit(): void {
    const value = this.draft().trim();
    if (!value) return;
    const op = this.change().operation;
    let edit: AcceptEdit;
    if (op === 'update_skills') edit = { list: value.split(',').map((s) => s.trim()).filter(Boolean) };
    else if (op.endsWith('_experience')) edit = { list: value.split('\n').map((s) => s.replace(/^[•\-*]\s*/, '').trim()).filter(Boolean) };
    else if (op.endsWith('_project')) edit = { description: value };
    else edit = { text: value };
    this.editing.set(false);
    this.accept.emit(edit);
  }

  protected statusLabel(): string {
    const status = this.proposal().status;
    return status === 'stale' ? 'Outdated' : status.charAt(0).toUpperCase() + status.slice(1);
  }
}
