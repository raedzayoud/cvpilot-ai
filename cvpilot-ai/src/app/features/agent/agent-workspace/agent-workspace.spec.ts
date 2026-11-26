import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AgentProposal, AgentWorkspace } from '../../../core/agent-models';
import { API_BASE } from '../../../core/config';
import { CvProfile } from '../../../core/models';
import { AgentWorkspacePage } from './agent-workspace';

const SID = 's1';

const profile = (summary: string): CvProfile => ({
  personalInfo: { fullName: 'Sami Ben Ali', email: 'sami@example.com', phone: '', location: 'Tunis', links: [] },
  professionalTitle: 'Software Engineer',
  summary,
  skills: ['Java', 'Spring Boot'],
  experiences: [
    { id: 'exp-1', role: 'Engineer', company: 'Acme', location: '', start: '2022', end: 'Present', bullets: ['Built APIs'], technologies: ['Java'] },
  ],
  education: [],
  projects: [],
  certifications: [],
  languages: [],
  achievements: [],
});

const proposal = (status: AgentProposal['status']): AgentProposal => ({
  id: 'p1',
  status,
  createdAt: '2026-11-25T10:00:00Z',
  decidedAt: null,
  change: {
    operation: 'update_summary',
    section: 'summary',
    label: 'Professional summary',
    originalText: 'Engineer.',
    updatedText: 'Backend engineer building REST APIs with Java.',
    reason: 'Emphasize backend work',
    jobRequirements: ['Java'],
    keywordsAdded: ['Java'],
    keywordsRemoved: [],
    evidence: [{ term: 'Java', source: 'cv' }],
    text: 'Backend engineer building REST APIs with Java.',
  },
});

function workspace(over: Partial<AgentWorkspace> = {}): AgentWorkspace {
  return {
    session: { id: SID, cvId: 'cv1', cvFileName: 'cv.pdf', title: 'cv.pdf', updatedAt: '2026-11-25T10:00:00Z', savedAt: null },
    messages: [{ id: '1', role: 'agent', text: "I've read cv.pdf.", at: '', meta: null }],
    proposals: [proposal('pending')],
    working: profile('Engineer.'),
    original: profile('Engineer.'),
    sectionOrder: ['summary', 'skills', 'experience'],
    jobTarget: null,
    confirmedFacts: [],
    stats: { pending: 1, accepted: 0, rejected: 0 },
    unsavedChanges: false,
    aiAvailable: true,
    ...over,
  };
}

describe('AgentWorkspacePage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgentWorkspacePage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: SID })) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function render(ws = workspace()) {
    const fixture = TestBed.createComponent(AgentWorkspacePage);
    fixture.detectChanges();
    http.expectOne(`${API_BASE}/agent/sessions/${SID}`).flush(ws);
    http.expectOne((r) => r.url === `${API_BASE}/agent/sessions`).flush({ items: [ws.session], page: 0, size: 30, totalItems: 1, totalPages: 1 });
    fixture.detectChanges();
    return fixture;
  }

  it('shows the conversation and an explainable proposal', () => {
    const root = render().nativeElement as HTMLElement;
    expect(root.textContent).toContain("I've read cv.pdf.");
    expect(root.textContent).toContain('Professional summary');
    expect(root.textContent).toContain('Emphasize backend work');
    expect(root.querySelector('ins')?.textContent).toContain('Backend');
    expect(root.textContent).toContain('+ Java');
  });

  it('sends instructions without auth headers and shows blocked claims', () => {
    const fixture = render();
    const component = fixture.componentInstance as unknown as { draft: { set(v: string): void }; send(): void };
    component.draft.set('Tailor my CV for .NET');
    component.send();

    const req = http.expectOne(`${API_BASE}/agent/sessions/${SID}/messages`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(
      workspace({
        messages: [
          { id: '1', role: 'agent', text: "I've read cv.pdf.", at: '', meta: null },
          { id: '2', role: 'user', text: 'Tailor my CV for .NET', at: '', meta: null },
          {
            id: '3',
            role: 'agent',
            text: 'No confirmed .NET experience in your CV.',
            at: '',
            meta: { intent: 'tailor_to_job', toolsUsed: ['analyze_job_description'], blockedClaims: ['".NET" is not in the CV'], proposalIds: [] },
          },
        ],
      }),
    );
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('No confirmed .NET experience');
    expect(text).toContain('Tailor to job');
    expect(text).toContain('Not added: 1 unverified claim');
  });

  it('accepts a change and reflects the new status', () => {
    const fixture = render();
    const root = fixture.nativeElement as HTMLElement;
    const accept = Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Accept');
    accept!.click();

    http.expectOne(`${API_BASE}/agent/proposals/p1/accept`).flush(
      workspace({ proposals: [proposal('accepted')], stats: { pending: 0, accepted: 1, rejected: 0 }, unsavedChanges: true }),
    );
    fixture.detectChanges();

    expect(root.textContent).toContain('No changes waiting');
    expect(root.textContent).toContain('accepted changes not saved to your CV yet');
  });
});
