import { Component, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { SystemApi } from './core/api/system-api.service';
import { SystemStatus } from './core/models';
import { ConfirmDialog } from './shared/confirm-dialog/confirm-dialog';
import { Icon } from './shared/icon/icon';
import { ToastHost } from './shared/toast-host/toast-host';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  exact: boolean;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastHost, ConfirmDialog, Icon],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  private systemApi = inject(SystemApi);
  private router = inject(Router);
  private browser = isPlatformBrowser(inject(PLATFORM_ID));

  protected readonly nav: NavItem[] = [
    { path: '/', label: 'Dashboard', icon: 'dashboard', exact: true },
    { path: '/cvs', label: 'My CVs', icon: 'file', exact: false },
    { path: '/agent', label: 'AI CV Agent', icon: 'bot', exact: false },
    { path: '/jobs', label: 'New customization', icon: 'sparkles', exact: false },
    { path: '/analyses', label: 'History', icon: 'history', exact: true },
  ];

  protected readonly menuOpen = signal(false);
  /** null while loading; 'offline' when the API cannot be reached. */
  protected readonly status = signal<SystemStatus | 'offline' | null>(null);

  constructor() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.menuOpen.set(false));
  }

  ngOnInit(): void {
    if (!this.browser) return;
    this.systemApi.status().subscribe({
      next: (status) => this.status.set(status),
      error: () => this.status.set('offline'),
    });
  }

  protected statusLabel(status: SystemStatus | 'offline'): string {
    if (status === 'offline') return 'Server offline';
    return status.aiConfigured ? 'AI assistant ready' : 'AI not configured';
  }

  protected statusTone(status: SystemStatus | 'offline'): string {
    if (status === 'offline') return 'bad';
    return status.aiConfigured ? 'good' : 'warn';
  }
}
