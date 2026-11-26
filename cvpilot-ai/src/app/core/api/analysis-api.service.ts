import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_BASE } from '../config';
import {
  Analysis,
  AnalysisStatusFilter,
  AnalysisSummary,
  ChatTurn,
  Customization,
  DecisionAction,
  ExportFormat,
  Page,
  PageQuery,
} from '../models';
import { toParams } from './query';

export interface CreateAnalysisInput {
  cvId: string;
  jobId: string;
}

@Injectable({ providedIn: 'root' })
export class AnalysisApi {
  private http = inject(HttpClient);

  list(query: PageQuery & { status?: AnalysisStatusFilter } = {}): Observable<Page<AnalysisSummary>> {
    return this.http.get<Page<AnalysisSummary>>(`${API_BASE}/analyses`, {
      params: toParams({
        search: query.search,
        status: query.status,
        page: query.page,
        size: query.size ?? 20,
        sort: query.sort,
      }),
    });
  }

  create(input: CreateAnalysisInput): Observable<Analysis> {
    return this.http.post<Analysis>(`${API_BASE}/analyses`, input);
  }

  get(id: string): Observable<Analysis> {
    return this.http.get<Analysis>(`${API_BASE}/analyses/${id}`);
  }

  regenerate(id: string): Observable<Analysis> {
    return this.http.post<Analysis>(`${API_BASE}/analyses/${id}/regenerate`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/analyses/${id}`);
  }

  customization(id: string): Observable<Customization> {
    return this.http.get<Customization>(`${API_BASE}/analyses/${id}/customization`);
  }

  chat(id: string): Observable<ChatTurn> {
    return this.http.get<ChatTurn>(`${API_BASE}/analyses/${id}/chat`);
  }

  sendChat(id: string, text: string): Observable<ChatTurn> {
    return this.http.post<ChatTurn>(`${API_BASE}/analyses/${id}/chat`, { text });
  }

  approveSection(id: string, key: string, text: string): Observable<Customization> {
    return this.http.post<Customization>(`${API_BASE}/analyses/${id}/sections/${encodeURIComponent(key)}`, { text });
  }

  setDecision(id: string, decisionId: string, action: DecisionAction): Observable<Customization> {
    return this.http.post<Customization>(`${API_BASE}/analyses/${id}/decisions/${encodeURIComponent(decisionId)}`, {
      action,
    });
  }

  export(id: string, format: ExportFormat): Observable<Blob> {
    return this.http.post(
      `${API_BASE}/analyses/${id}/export`,
      { format },
      { responseType: 'blob' },
    );
  }
}
