import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';

import { AnalysisApi } from '../../core/api/analysis-api.service';
import { ConfirmService } from '../../core/confirm.service';
import { messageFrom } from '../../core/http-error';
import { AnalysisStatusFilter, AnalysisSummary } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon/icon';
import { ScoreRing } from '../../shared/score-ring/score-ring';

const PAGE_SIZE = 15;

@Component({
  selector: 'app-history',
  imports: [DatePipe, FormsModule, RouterLink, Icon, ScoreRing],
  templateUrl: './history.html',
  styleUrl: './history.css',
})
export class History implements OnInit, OnDestroy {
  private analysisApi = inject(AnalysisApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  protected readonly items = signal<AnalysisSummary[] | null>(null);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly search = signal('');
  protected readonly status = signal<AnalysisStatusFilter>('all');
  protected readonly sort = signal('createdAt,desc');
  protected readonly error = signal<string | null>(null);
  protected readonly loadingMore = signal(false);
  protected readonly busyId = signal<string | null>(null);

  protected readonly sorts = [
    { value: 'createdAt,desc', label: 'Newest first' },
    { value: 'createdAt,asc', label: 'Oldest first' },
    { value: 'score,desc', label: 'Best match' },
    { value: 'jobTitle,asc', label: 'Job title A–Z' },
  ];

  private searches = new Subject<string>();
  private subscription?: Subscription;

  ngOnInit(): void {
    this.subscription = this.searches
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.load());
    this.load();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.searches.next(value.trim());
  }

  protected setStatus(value: AnalysisStatusFilter): void {
    this.status.set(value);
    this.load();
  }

  protected setSort(value: string): void {
    this.sort.set(value);
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.items.set(null);
    this.fetch(0).subscribe({
      next: (result) => {
        this.items.set(result.items);
        this.total.set(result.totalItems);
        this.page.set(0);
      },
      error: (error: unknown) => {
        this.items.set([]);
        this.error.set(messageFrom(error));
      },
    });
  }

  protected loadMore(): void {
    const next = this.page() + 1;
    this.loadingMore.set(true);
    this.fetch(next).subscribe({
      next: (result) => {
        this.items.update((items) => [...(items ?? []), ...result.items]);
        this.total.set(result.totalItems);
        this.page.set(next);
        this.loadingMore.set(false);
      },
      error: (error: unknown) => {
        this.loadingMore.set(false);
        this.toast.error(messageFrom(error));
      },
    });
  }

  private fetch(page: number) {
    return this.analysisApi.list({
      search: this.search().trim(),
      status: this.status(),
      sort: this.sort(),
      page,
      size: PAGE_SIZE,
    });
  }

  protected regenerate(item: AnalysisSummary): void {
    if (this.busyId()) return;
    this.busyId.set(item.id);
    this.analysisApi.regenerate(item.id).subscribe({
      next: (analysis) => {
        this.busyId.set(null);
        this.toast.success('Fresh customization created from your latest CV profile.');
        this.router.navigate(['/analyses', analysis.id, 'review']);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.toast.error(messageFrom(error));
      },
    });
  }

  protected async remove(item: AnalysisSummary): Promise<void> {
    if (this.busyId()) return;
    const confirmed = await this.confirm.ask({
      title: 'Delete this customization?',
      message: `“${item.jobTitle}” and its chat history will be permanently deleted. Your CV is not affected.`,
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!confirmed) return;

    this.busyId.set(item.id);
    this.analysisApi.delete(item.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.items.update((items) => (items ?? []).filter((entry) => entry.id !== item.id));
        this.total.update((n) => Math.max(0, n - 1));
        this.toast.success('Customization deleted.');
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.toast.error(messageFrom(error));
      },
    });
  }
}
