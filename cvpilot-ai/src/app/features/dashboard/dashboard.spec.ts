import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { API_BASE } from '../../core/config';
import { DashboardPage } from './dashboard';

describe('DashboardPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders live stats and the score chart', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    http.expectOne(`${API_BASE}/dashboard`).flush({
      stats: { cvCount: 3, analysisCount: 2, generatedCount: 1, averageScore: 64, bestScore: 81 },
      recentJobs: [],
      recentAnalyses: [
        {
          id: 'a1',
          jobTitle: 'Java Developer',
          cvFileName: 'cv.docx',
          createdAt: '2026-11-20T10:00:00Z',
          exported: false,
          sourceFormat: 'docx',
          score: 81,
        },
      ],
      activity: [],
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('CVs uploaded');
    expect(text).toContain('Best: 81/100');
    expect(text).toContain('Java Developer');
    expect(text).toContain('No job descriptions yet');
  });

  it('shows onboarding for a brand-new workspace', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    http.expectOne(`${API_BASE}/dashboard`).flush({
      stats: { cvCount: 0, analysisCount: 0, generatedCount: 0, averageScore: null, bestScore: null },
      recentJobs: [],
      recentAnalyses: [],
      activity: [],
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Welcome to CVPilot AI');
  });

  it('offers a retry when the API fails', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();

    http
      .expectOne(`${API_BASE}/dashboard`)
      .flush({ message: 'Database unavailable.' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Database unavailable.');
    expect(text).toContain('Retry');
  });
});
