import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { DashboardApi } from '../../core/api/dashboard-api.service';
import { messageFrom } from '../../core/http-error';
import { ActivityType, Dashboard, JobPosting } from '../../core/models';
import { Icon } from '../../shared/icon/icon';
import { ScoreRing } from '../../shared/score-ring/score-ring';

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, RouterLink, Icon, ScoreRing],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardPage implements OnInit {
  private dashboardApi = inject(DashboardApi);

  protected readonly dashboard = signal<Dashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly isNew = computed(() => {
    const d = this.dashboard();
    return !!d && d.stats.cvCount === 0 && d.stats.analysisCount === 0;
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.dashboardApi.get().subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(messageFrom(error));
        this.loading.set(false);
      },
    });
  }

  protected jobTitle(job: JobPosting): string {
    return job.positions[0]?.title ?? 'Job description';
  }

  protected positionLabel(job: JobPosting): string {
    const count = job.positions.length;
    return count === 1 ? '1 position' : `${count} positions`;
  }

  protected band(score: number): string {
    return score >= 75 ? 'Strong' : score >= 50 ? 'Fair' : 'Low';
  }

  protected tone(type: ActivityType): string {
    if (type === 'cv_deleted' || type === 'analysis_deleted') return 'muted';
    if (type === 'cv_uploaded' || type === 'analysis_exported') return 'success';
    return 'primary';
  }

  protected activityIcon(type: ActivityType): string {
    if (type.startsWith('cv_')) return type === 'cv_deleted' ? 'trash' : 'file';
    if (type === 'job_created') return 'briefcase';
    if (type.startsWith('agent_')) return 'bot';
    if (type === 'analysis_exported') return 'download';
    if (type === 'analysis_deleted') return 'trash';
    return 'sparkles';
  }
}
