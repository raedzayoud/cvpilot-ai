import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';

import { ConfirmService } from '../../core/confirm.service';
import { Icon } from '../icon/icon';

/** Accessible modal built on the native <dialog>: focus trap, Esc to cancel, backdrop click to cancel. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [Icon],
  template: `
    <dialog
      #dialog
      class="dialog"
      aria-labelledby="confirm-title"
      aria-describedby="confirm-message"
      (cancel)="$event.preventDefault(); confirm.close(false)"
      (click)="onBackdrop($event)"
    >
      @if (confirm.pending(); as p) {
        <div class="dialog-body">
          <span class="dialog-icon" [class.danger]="p.danger" aria-hidden="true">
            <app-icon [name]="p.danger ? 'alert' : 'info'" [size]="20" />
          </span>
          <div>
            <h2 id="confirm-title">{{ p.title }}</h2>
            <p id="confirm-message">{{ p.message }}</p>
          </div>
        </div>
        <div class="dialog-actions">
          <button type="button" class="btn" (click)="confirm.close(false)">
            {{ p.cancelLabel ?? 'Cancel' }}
          </button>
          <button
            type="button"
            class="btn"
            [class.btn-primary]="!p.danger"
            [class.btn-danger-solid]="p.danger"
            (click)="confirm.close(true)"
            autofocus
          >
            {{ p.confirmLabel ?? 'Confirm' }}
          </button>
        </div>
      }
    </dialog>
  `,
  styles: `
    .dialog {
      width: min(440px, calc(100vw - 32px));
      padding: 0;
      border: 1px solid var(--line);
      border-radius: var(--radius-lg);
      background: var(--surface);
      color: var(--ink);
      box-shadow: var(--shadow-lg);
    }
    .dialog::backdrop {
      background: rgb(15 23 42 / 0.45);
      backdrop-filter: blur(2px);
    }
    .dialog[open] {
      animation: pop 0.16s ease-out;
    }
    .dialog-body {
      display: flex;
      gap: 14px;
      padding: 22px 22px 8px;
    }
    .dialog-icon {
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      flex: none;
      border-radius: 999px;
      background: var(--primary-soft);
      color: var(--primary);
    }
    .dialog-icon.danger {
      background: var(--error-soft);
      color: var(--error);
    }
    h2 {
      font-size: 16px;
      font-weight: 700;
      margin-bottom: 6px;
    }
    p {
      color: var(--muted);
      font-size: 14px;
    }
    .dialog-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      padding: 16px 22px 20px;
    }
    @keyframes pop {
      from {
        opacity: 0;
        transform: translateY(6px) scale(0.98);
      }
    }
  `,
})
export class ConfirmDialog {
  protected readonly confirm = inject(ConfirmService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      const open = this.confirm.pending() !== null;
      if (open && !element.open) element.showModal?.();
      if (!open && element.open) element.close();
    });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) this.confirm.close(false);
  }
}
