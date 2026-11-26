import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { API_BASE } from '../../../core/config';
import { Customization, SectionProposal } from '../../../core/models';
import { CustomizationChat } from './customization-chat';

const ID = 'a1';

function section(key: string, locked = false): SectionProposal {
  return {
    key,
    label: key,
    original: `${key} original`,
    proposed: `${key} proposed`,
    sources: [],
    violations: [],
    approved: locked,
    locked,
  };
}

function customization(approved: string[] = []): Customization {
  const sections = [section('summary'), section('skills'), section('education', true)];
  sections.forEach((s) => (s.approved = s.locked || approved.includes(s.key)));
  return { analysisId: ID, language: 'English', sections, decisions: [] };
}

const insights = {
  analysisId: ID,
  score: 72,
  criteria: [],
  strengths: [],
  weaknesses: [],
  improvements: [],
  skillRecommendations: [],
  disclaimer: 'Estimate.',
};

const buttons = (root: HTMLElement) => Array.from(root.querySelectorAll<HTMLButtonElement>('button'));
const downloadButton = (root: HTMLElement) =>
  buttons(root).find((b) => b.textContent?.trim().startsWith('Download'));
const approveButtons = (root: HTMLElement) =>
  buttons(root).filter((b) => b.textContent?.trim() === 'Check and approve');

describe('CustomizationChat', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CustomizationChat],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: ID }), queryParamMap: convertToParamMap({}) },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render() {
    const fixture = TestBed.createComponent(CustomizationChat);
    fixture.detectChanges();
    http.expectOne(`${API_BASE}/analyses/${ID}`).flush({
      id: ID,
      cvId: 'cv1',
      jobId: 'job1',
      jobTitle: 'Java Developer',
      language: 'English',
      sourceFormat: 'docx',
      score: 72,
      exported: false,
      createdAt: '2026-11-20T10:00:00Z',
    });
    http.expectOne(`${API_BASE}/analyses/${ID}/chat`).flush({
      messages: [{ id: '1', role: 'bot', text: 'Hi — I read your CV.', at: '2026-11-20T10:00:00Z' }],
      customization: customization(),
      insights,
    });
    fixture.detectChanges();
    return fixture;
  }

  it('enables download only after every editable section is approved', () => {
    const fixture = render();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Hi — I read your CV.');
    expect(downloadButton(root)?.disabled).toBe(true);

    approveButtons(root)[0].click();
    http.expectOne(`${API_BASE}/analyses/${ID}/sections/summary`).flush(customization(['summary']));
    fixture.detectChanges();
    expect(downloadButton(root)?.disabled).toBe(true);

    approveButtons(root)[0].click();
    http.expectOne(`${API_BASE}/analyses/${ID}/sections/skills`).flush(customization(['summary', 'skills']));
    fixture.detectChanges();

    expect(downloadButton(root)?.disabled).toBe(false);
  });

  it('keeps an unsaved edit when another section is approved', () => {
    const fixture = render();
    const root = fixture.nativeElement as HTMLElement;
    const component = fixture.componentInstance as unknown as {
      editSection(key: string, text: string): void;
      customization(): Customization | null;
    };

    component.editSection('summary', 'My own wording.');
    fixture.detectChanges();
    approveButtons(root)[1].click();
    http.expectOne(`${API_BASE}/analyses/${ID}/sections/skills`).flush(customization(['skills']));
    fixture.detectChanges();

    const summary = component.customization()!.sections.find((s) => s.key === 'summary')!;
    expect(summary.proposed).toBe('My own wording.');
    expect(summary.approved).toBe(false);
  });

  it('sends a chat message and renders the reply', () => {
    const fixture = render();
    const root = fixture.nativeElement as HTMLElement;
    const component = fixture.componentInstance as unknown as { ask(text: string): void };

    component.ask('Why did I get this score?');
    fixture.detectChanges();
    expect(root.textContent).toContain('Why did I get this score?');

    const request = http.expectOne(`${API_BASE}/analyses/${ID}/chat`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ text: 'Why did I get this score?' });
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({
      messages: [
        { id: '1', role: 'bot', text: 'Hi — I read your CV.', at: '' },
        { id: '2', role: 'user', text: 'Why did I get this score?', at: '' },
        { id: '3', role: 'bot', text: 'You scored 72/100.', at: '' },
      ],
      customization: customization(),
      insights,
    });
    fixture.detectChanges();

    expect(root.textContent).toContain('You scored 72/100.');
  });
});
