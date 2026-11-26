import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CvProfile } from '../../core/models';

const LABELS: Record<string, string> = {
  summary: 'Summary',
  skills: 'Technical Skills',
  experience: 'Professional Experience',
  projects: 'Technical Projects',
  education: 'Education',
  certifications: 'Certifications',
  languages: 'Languages',
  achievements: 'Achievements',
};

/** Document-style preview of a CV, laid out like the exported LaTeX template. */
@Component({
  selector: 'app-cv-preview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cv-preview.html',
  styleUrl: './cv-preview.css',
})
export class CvPreview {
  profile = input.required<CvProfile>();
  order = input<string[]>(Object.keys(LABELS));

  protected readonly labels = LABELS;

  protected readonly sections = computed(() => {
    const p = this.profile();
    return this.order().filter((key) => {
      switch (key) {
        case 'summary':
          return !!p.summary;
        case 'skills':
          return p.skills.length > 0;
        case 'experience':
          return p.experiences.length > 0;
        case 'projects':
          return p.projects.length > 0;
        case 'education':
          return p.education.length > 0;
        case 'certifications':
          return p.certifications.length > 0;
        case 'languages':
          return p.languages.length > 0;
        case 'achievements':
          return (p.achievements ?? []).length > 0;
        default:
          return false;
      }
    });
  });

  protected contact(p: CvProfile): string[] {
    const info = p.personalInfo;
    return [info.email && `Email: ${info.email}`, info.phone && `Mobile: ${info.phone}`, info.location].filter(
      (v): v is string => !!v,
    );
  }

  protected linkLabel(link: string): string {
    const known: [RegExp, string][] = [
      [/linkedin\./i, 'LinkedIn'],
      [/github\./i, 'GitHub'],
      [/gitlab\./i, 'GitLab'],
      [/leetcode\./i, 'LeetCode'],
      [/codeforces\./i, 'Codeforces'],
    ];
    if (!/[./]/.test(link)) return link;
    return known.find(([re]) => re.test(link))?.[1] ?? 'Portfolio';
  }

  protected href(link: string): string | null {
    if (!/[./]/.test(link)) return null;
    return /^https?:\/\//i.test(link) ? link : `https://${link}`;
  }

  protected dates(start: string, end: string): string {
    return [start, end].filter(Boolean).join(' – ');
  }
}
