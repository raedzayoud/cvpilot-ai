import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';

import { CvApi } from '../../../core/api/cv-api.service';
import { ConfirmService } from '../../../core/confirm.service';
import { saveBlob } from '../../../core/download';
import { messageFrom, messageFromBlobError } from '../../../core/http-error';
import { CvSummary } from '../../../core/models';
import { ToastService } from '../../../core/toast.service';
import { Dropzone } from '../../../shared/dropzone/dropzone';
import { Icon } from '../../../shared/icon/icon';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-cv-list',
  imports: [DatePipe, FormsModule, RouterLink, Dropzone, Icon],
  templateUrl: './cv-list.html',
  styleUrl: './cv-list.css',
})
export class CvList implements OnInit, OnDestroy {
  private cvApi = inject(CvApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  protected readonly cvs = signal<CvSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly search = signal('');
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly uploading = signal(false);
  protected readonly showUpload = signal(false);
  protected readonly mode = signal<'file' | 'latex'>('file');
  protected readonly latexName = signal('My_CV.tex');
  protected readonly latexSource = signal('');
  protected readonly deletingId = signal<string | null>(null);

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

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.cvApi.list({ search: this.search().trim(), page: 0, size: PAGE_SIZE }).subscribe({
      next: (result) => {
        this.cvs.set(result.items);
        this.total.set(result.totalItems);
        this.page.set(0);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(messageFrom(error));
        this.loading.set(false);
      },
    });
  }

  protected loadMore(): void {
    const next = this.page() + 1;
    this.loadingMore.set(true);
    this.cvApi.list({ search: this.search().trim(), page: next, size: PAGE_SIZE }).subscribe({
      next: (result) => {
        this.cvs.update((list) => [...list, ...result.items]);
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

  protected upload(file: File): void {
    this.uploading.set(true);
    this.cvApi.upload(file).subscribe({
      next: (cv) => this.finishUpload(cv.id, cv.extractionNote),
      error: (error: unknown) => {
        this.uploading.set(false);
        this.toast.error(messageFrom(error));
      },
    });
  }

  protected uploadTex(): void {
    const source = this.latexSource().trim();
    if (!source) {
      this.toast.error('Paste the LaTeX source first.');
      return;
    }
    this.uploading.set(true);
    this.cvApi.uploadText({ fileName: this.latexName().trim() || 'CV.tex', source }).subscribe({
      next: (cv) => {
        this.latexSource.set('');
        this.finishUpload(cv.id, cv.extractionNote);
      },
      error: (error: unknown) => {
        this.uploading.set(false);
        this.toast.error(messageFrom(error));
      },
    });
  }

  private finishUpload(id: string, note: string | null): void {
    this.uploading.set(false);
    if (note) this.toast.show('CV saved. Some fields need your input.');
    else this.toast.success('CV uploaded. Review the extracted profile.');
    this.router.navigate(['/cvs', id]);
  }

  protected async deleteCv(cv: CvSummary): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: `Delete ${cv.fileName}?`,
      message:
        'This also deletes every job description, customization and chat linked to this CV. This cannot be undone.',
      confirmLabel: 'Delete CV',
      danger: true,
    });
    if (!confirmed) return;

    this.deletingId.set(cv.id);
    this.cvApi.delete(cv.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.cvs.update((list) => list.filter((item) => item.id !== cv.id));
        this.total.update((n) => Math.max(0, n - 1));
        this.toast.success('CV deleted.');
      },
      error: (error: unknown) => {
        this.deletingId.set(null);
        this.toast.error(messageFrom(error));
      },
    });
  }

  protected downloadOriginal(cv: CvSummary): void {
    this.cvApi.downloadOriginal(cv.id).subscribe({
      next: (blob) => saveBlob(blob, cv.fileName),
      error: async (error: unknown) => this.toast.error(await messageFromBlobError(error)),
    });
  }

  protected extension(fileName: string): string {
    return fileName.split('.').pop()?.toUpperCase() ?? 'FILE';
  }

  protected formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
