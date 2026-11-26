import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_BASE } from '../config';
import { CvProfile, CvRecord, CvSummary, CvTextInput, Page, PageQuery } from '../models';
import { toParams } from './query';

@Injectable({ providedIn: 'root' })
export class CvApi {
  private http = inject(HttpClient);

  list(query: PageQuery = {}): Observable<Page<CvSummary>> {
    return this.http.get<Page<CvSummary>>(`${API_BASE}/cvs`, {
      params: toParams({ search: query.search, page: query.page, size: query.size ?? 20, sort: query.sort }),
    });
  }

  get(id: string): Observable<CvRecord> {
    return this.http.get<CvRecord>(`${API_BASE}/cvs/${id}`);
  }

  updateProfile(id: string, profile: CvProfile): Observable<CvRecord> {
    return this.http.put<CvRecord>(`${API_BASE}/cvs/${id}/profile`, profile);
  }

  upload(file: File): Observable<CvRecord> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<CvRecord>(`${API_BASE}/cvs`, body);
  }

  uploadText(input: CvTextInput): Observable<CvRecord> {
    return this.http.post<CvRecord>(`${API_BASE}/cvs/text`, input);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/cvs/${id}`);
  }

  downloadOriginal(id: string): Observable<Blob> {
    return this.http.get(`${API_BASE}/cvs/${id}/original`, { responseType: 'blob' });
  }
}
