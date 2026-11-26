import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_BASE } from '../config';
import { JobInput, JobPosting } from '../models';

@Injectable({ providedIn: 'root' })
export class JobApi {
  private http = inject(HttpClient);

  create(input: JobInput): Observable<JobPosting> {
    return this.http.post<JobPosting>(`${API_BASE}/jobs`, input);
  }

  /** Sends the PDF/DOCX itself; the backend extracts the text. */
  upload(cvId: string, file: File): Observable<JobPosting> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<JobPosting>(`${API_BASE}/jobs/upload`, body, { params: { cvId } });
  }

  get(id: string): Observable<JobPosting> {
    return this.http.get<JobPosting>(`${API_BASE}/jobs/${id}`);
  }
}
