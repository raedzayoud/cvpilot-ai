import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { CvApi } from '../../../core/api/cv-api.service';
import { ConfirmService } from '../../../core/confirm.service';
import { saveBlob } from '../../../core/download';
import { messageFrom, messageFromBlobError } from '../../../core/http-error';
import {
  CertificationItem,
  CvRecord,
  EducationItem,
  Experience,
  LanguageItem,
  ProjectItem,
} from '../../../core/models';
import { ToastService } from '../../../core/toast.service';
import { Icon } from '../../../shared/icon/icon';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-cv-editor',
  imports: [FormsModule, RouterLink, Icon],
  templateUrl: './cv-editor.html',
  styleUrl: './cv-editor.css',
})
export class CvEditor implements OnInit {
  private route = inject(ActivatedRoute);
  private cvApi = inject(CvApi);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);

  protected readonly cv = signal<CvRecord | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly dirty = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly emailInvalid = computed(() => {
    const email = this.cv()?.profile.personalInfo.email.trim() ?? '';
    return email.length > 0 && !EMAIL.test(email);
  });

  /** Used by the route guard so unsaved edits are never lost silently. */
  canLeave(): Promise<boolean> | boolean {
    if (!this.dirty()) return true;
    return this.confirm.ask({
      title: 'Leave without saving?',
      message: 'Your profile changes have not been saved and will be lost.',
      confirmLabel: 'Discard changes',
      cancelLabel: 'Keep editing',
      danger: true,
    });
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.loading.set(true);
    this.error.set(null);
    this.cvApi.get(id).subscribe({
      next: (cv) => {
        this.cv.set(cv);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(messageFrom(error));
        this.loading.set(false);
      },
    });
  }

  protected touch(): void {
    const current = this.cv();
    if (!current) return;
    this.dirty.set(true);
    this.cv.set({ ...current });
  }

  protected addSkill(input: HTMLInputElement): void {
    const skill = input.value.trim();
    if (!skill) return;
    this.mutate((cv) => {
      if (!cv.profile.skills.includes(skill)) cv.profile.skills.push(skill);
    });
    input.value = '';
  }

  protected removeSkill(index: number): void {
    this.mutate((cv) => cv.profile.skills.splice(index, 1));
  }

  protected setLinks(value: string): void {
    this.mutate((cv) => {
      cv.profile.personalInfo.links = value.split(',');
    });
  }

  protected setExperienceTechnologies(experienceId: string, value: string): void {
    this.mutate((cv) => {
      const experience = cv.profile.experiences.find((item) => item.id === experienceId);
      if (experience) experience.technologies = value.split(',');
    });
  }

  protected addAchievement(): void {
    this.mutate((cv) => cv.profile.achievements.push(''));
  }

  protected removeAchievement(index: number): void {
    this.mutate((cv) => cv.profile.achievements.splice(index, 1));
  }

  protected setTechnologies(projectId: string, value: string): void {
    this.mutate((cv) => {
      const project = cv.profile.projects.find((item) => item.id === projectId);
      if (project) project.technologies = value.split(',');
    });
  }

  protected addExperience(): void {
    this.mutate((cv) => cv.profile.experiences.push(blankExperience()));
  }

  protected removeExperience(id: string): void {
    this.mutate((cv) => {
      cv.profile.experiences = cv.profile.experiences.filter((item) => item.id !== id);
    });
  }

  protected addBullet(experience: Experience): void {
    this.mutate((cv) => {
      const target = cv.profile.experiences.find((item) => item.id === experience.id);
      target?.bullets.push('');
    });
  }

  protected removeBullet(experience: Experience, index: number): void {
    this.mutate((cv) => {
      const target = cv.profile.experiences.find((item) => item.id === experience.id);
      target?.bullets.splice(index, 1);
    });
  }

  protected addEducation(): void {
    this.mutate((cv) => cv.profile.education.push(blankEducation()));
  }

  protected removeEducation(id: string): void {
    this.mutate((cv) => {
      cv.profile.education = cv.profile.education.filter((item) => item.id !== id);
    });
  }

  protected addProject(): void {
    this.mutate((cv) => cv.profile.projects.push(blankProject()));
  }

  protected removeProject(id: string): void {
    this.mutate((cv) => {
      cv.profile.projects = cv.profile.projects.filter((item) => item.id !== id);
    });
  }

  protected addCertification(): void {
    this.mutate((cv) => cv.profile.certifications.push(blankCertification()));
  }

  protected removeCertification(id: string): void {
    this.mutate((cv) => {
      cv.profile.certifications = cv.profile.certifications.filter((item) => item.id !== id);
    });
  }

  protected addLanguage(): void {
    this.mutate((cv) => cv.profile.languages.push(blankLanguage()));
  }

  protected removeLanguage(id: string): void {
    this.mutate((cv) => {
      cv.profile.languages = cv.profile.languages.filter((item) => item.id !== id);
    });
  }

  protected save(): void {
    const current = this.cv();
    if (!current) return;

    if (this.emailInvalid()) {
      this.toast.error('Enter a valid email address, or leave it empty.');
      return;
    }
    const payload = structuredClone(current.profile);
    payload.skills = clean(payload.skills);
    payload.personalInfo.links = clean(payload.personalInfo.links);
    payload.experiences.forEach((item) => (item.bullets = clean(item.bullets)));
    payload.projects.forEach((item) => (item.technologies = clean(item.technologies)));
    payload.experiences.forEach((item) => (item.technologies = clean(item.technologies)));
    payload.achievements = clean(payload.achievements);

    this.saving.set(true);
    this.cvApi.updateProfile(current.id, payload).subscribe({
      next: (updated) => {
        this.cv.set(updated);
        this.dirty.set(false);
        this.saving.set(false);
        this.toast.success('Profile saved. New customizations will use it.');
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.toast.error(messageFrom(error));
      },
    });
  }

  protected downloadOriginal(): void {
    const current = this.cv();
    if (!current) return;
    this.cvApi.downloadOriginal(current.id).subscribe({
      next: (blob) => saveBlob(blob, current.fileName),
      error: async (error: unknown) => this.toast.error(await messageFromBlobError(error)),
    });
  }

  private mutate(fn: (cv: CvRecord) => void): void {
    const current = this.cv();
    if (!current) return;
    const next = structuredClone(current);
    fn(next);
    this.cv.set(next);
    this.dirty.set(true);
  }
}

function clean(values: string[]): string[] {
  return values.map((value) => value.trim()).filter(Boolean);
}

function uid(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 9)}`;
}

function blankExperience(): Experience {
  return {
    id: uid('exp'),
    role: '',
    company: '',
    location: '',
    start: '',
    end: '',
    bullets: [''],
    technologies: [],
  };
}

function blankEducation(): EducationItem {
  return {
    id: uid('edu'),
    degree: '',
    school: '',
    location: '',
    start: '',
    end: '',
    details: '',
  };
}

function blankProject(): ProjectItem {
  return { id: uid('prj'), name: '', description: '', technologies: [], link: '' };
}

function blankCertification(): CertificationItem {
  return { id: uid('cert'), name: '', issuer: '', year: '' };
}

function blankLanguage(): LanguageItem {
  return { id: uid('lang'), name: '', level: '' };
}
