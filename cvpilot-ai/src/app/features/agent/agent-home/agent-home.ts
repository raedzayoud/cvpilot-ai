import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { AgentSessionSummary } from '../../../core/agent-models';
import { AgentApi } from '../../../core/api/agent-api.service';
import { CvApi } from '../../../core/api/cv-api.service';
import { messageFrom } from '../../../core/http-error';
import { CvSummary } from '../../../core/models';
import { ToastService } from '../../../core/toast.service';
import { Icon } from '../../../shared/icon/icon';

@Component({
  selector: 'app-agent-home',
  imports: [DatePipe, FormsModule, RouterLink, Icon],
  templateUrl: './agent-home.html',
  styleUrl: './agent-home.css',
})
export class AgentHome implements OnInit {
  private agentApi = inject(AgentApi);
  private cvApi = inject(CvApi);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private toast = inject(ToastService);

  protected readonly cvs = signal<CvSummary[]>([]);
  protected readonly sessions = signal<AgentSessionSummary[]>([]);
  protected readonly cvId = signal('');
  protected readonly loading = signal(true);
  protected readonly starting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly capabilities = [
    { icon: 'briefcase', title: 'Tailor to a job', text: 'Paste a posting: the agent matches requirements to your real experience.' },
    { icon: 'edit', title: 'Rewrite & restructure', text: 'Summary, bullets with action verbs, skills order, section order.' },
    { icon: 'shield', title: 'No invented facts', text: 'Skills, numbers, companies and dates are checked against your CV.' },
    { icon: 'check', title: 'You approve everything', text: 'Compare original vs updated, then accept, edit or reject each change.' },
  ];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ cvs: this.cvApi.list({ size: 100 }), sessions: this.agentApi.list(undefined, 50) }).subscribe({
      next: ({ cvs, sessions }) => {
        this.cvs.set(cvs.items);
        this.sessions.set(sessions.items);
        const wanted = this.route.snapshot.queryParamMap.get('cv');
        const preselect = cvs.items.find((c) => c.id === wanted) ?? cvs.items[0];
        if (preselect) this.cvId.set(preselect.id);
        this.loading.set(false);
        if (wanted && preselect?.id === wanted) this.start();
      },
      error: (error: unknown) => {
        this.error.set(messageFrom(error));
        this.loading.set(false);
      },
    });
  }

  protected start(): void {
    if (!this.cvId() || this.starting()) return;
    this.starting.set(true);
    this.agentApi.create(this.cvId()).subscribe({
      next: (ws) => this.router.navigate(['/agent', ws.session.id]),
      error: (error: unknown) => {
        this.starting.set(false);
        this.toast.error(messageFrom(error));
      },
    });
  }
}
