import { TestBed } from '@angular/core/testing';

import { CvProfile } from '../../core/models';
import { CvPreview } from './cv-preview';

const profile = (skills: string[]): CvProfile => ({
  personalInfo: { fullName: 'Sami Ben Ali', email: '', phone: '', location: '', links: [] },
  professionalTitle: 'Engineer',
  summary: 'Backend engineer.',
  skills,
  experiences: [
    { id: 'exp-1', role: 'Engineer', company: 'Acme', location: '', start: '2022', end: 'Present', bullets: ['Built APIs'], technologies: [] },
  ],
  education: [],
  projects: [],
  certifications: [],
  languages: [],
  achievements: [],
});

describe('CvPreview', () => {
  function render(p: CvProfile) {
    const fixture = TestBed.createComponent(CvPreview);
    fixture.componentRef.setInput('profile', p);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders the Technical Skills section when there are skills', () => {
    const root = render(profile(['Java', 'Spring Boot']));
    expect(root.textContent).toContain('Technical Skills');
    expect(root.textContent).toContain('Java, Spring Boot');
  });

  it('renders no Technical Skills heading, content or spacing when the list is empty', () => {
    const root = render(profile([]));
    const headings = Array.from(root.querySelectorAll('h2')).map((h) => h.textContent ?? '');
    expect(headings.some((h) => h.includes('Technical Skills'))).toBe(false);
    expect(headings.some((h) => h.includes('Professional Experience'))).toBe(true);
    expect(root.querySelectorAll('section.doc-section')).toHaveLength(2);
  });
});
