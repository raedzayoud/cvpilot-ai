import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';

import { AnalysisApi } from '../../../core/api/analysis-api.service';
import { CvApi } from '../../../core/api/cv-api.service';
import { JobApi } from '../../../core/api/job-api.service';
import { messageFrom } from '../../../core/http-error';
import { CvSummary, JobPosting } from '../../../core/models';
import { Dropzone } from '../../../shared/dropzone/dropzone';
import { Icon } from '../../../shared/icon/icon';
import { Step, StepRail } from '../../../shared/step-rail/step-rail';

const MIN_CHARS = 20;
const MAX_CHARS = 50_000;

@Component({
  selector: 'app-job-input',
  imports: [FormsModule, RouterLink, Dropzone, StepRail, Icon],
  templateUrl: './job-input.html',
  styleUrl: './job-input.css',
})
export class JobInput implements OnInit {
  private cvApi = inject(CvApi);
  private jobApi = inject(JobApi);
  private analysisApi = inject(AnalysisApi);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  protected readonly steps: Step[] = [
    { label: 'CV', state: 'done' },
    { label: 'Job', state: 'current' },
    { label: 'Chat', state: 'upcoming' },
    { label: 'Export', state: 'upcoming' },
  ];
  protected readonly minChars = MIN_CHARS;
  protected readonly maxChars = MAX_CHARS;

  protected readonly cvs = signal<CvSummary[]>([]);
  protected readonly cvId = signal('');
  protected readonly mode = signal<'paste' | 'upload'>('paste');
  protected readonly text = signal('');
  protected readonly touched = signal(false);
  protected readonly jobFile = signal<File | null>(null);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly textLength = computed(() => this.text().trim().length);
  protected readonly textError = computed(() => {
    if (!this.touched() || this.mode() !== 'paste') return null;
    const length = this.textLength();
    if (length === 0) return 'Paste the job description.';
    if (length < MIN_CHARS) return `Add a bit more detail (at least ${MIN_CHARS} characters).`;
    if (length > MAX_CHARS) return `Keep it under ${MAX_CHARS.toLocaleString()} characters.`;
    return null;
  });
  protected readonly canSubmit = computed(
    () =>
      !!this.cvId() &&
      !this.submitting() &&
      (this.mode() === 'paste'
        ? this.textLength() >= MIN_CHARS && this.textLength() <= MAX_CHARS
        : this.jobFile() !== null),
  );

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.cvApi.list({ size: 100, sort: 'updatedAt,desc' }).subscribe({
      next: (page) => {
        this.cvs.set(page.items);
        const wanted = this.route.snapshot.queryParamMap.get('cv');
        const preselect = page.items.find((cv) => cv.id === wanted) ?? page.items[0];
        if (preselect) this.cvId.set(preselect.id);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(messageFrom(error));
        this.loading.set(false);
      },
    });
  }

  protected chooseFile(file: File): void {
    this.jobFile.set(file);
  }

  protected submit(): void {
    this.touched.set(true);
    if (!this.canSubmit()) return;

    const cvId = this.cvId();
    const file = this.jobFile();
    const job$: Observable<JobPosting> =
      this.mode() === 'paste' || !file
        ? this.jobApi.create({ cvId, text: this.text().trim() })
        : this.jobApi.upload(cvId, file);

    this.submitting.set(true);
    this.error.set(null);
    job$.pipe(switchMap((job) => this.analysisApi.create({ cvId, jobId: job.id }))).subscribe({
      next: (analysis) => {
        this.submitting.set(false);
        this.router.navigate(['/analyses', analysis.id, 'review']);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.error.set(messageFrom(error));
      },
    });
  }
}
