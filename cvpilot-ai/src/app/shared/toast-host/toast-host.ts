import { Component, inject } from '@angular/core';

import { ToastService } from '../../core/toast.service';
import { Icon } from '../icon/icon';

@Component({
  selector: 'app-toast-host',
  imports: [Icon],
  templateUrl: './toast-host.html',
  styleUrl: './toast-host.css',
})
export class ToastHost {
  protected readonly toastService = inject(ToastService);

  protected icon(kind: string): string {
    return kind === 'success' ? 'check' : kind === 'error' ? 'alert' : 'info';
  }
}
