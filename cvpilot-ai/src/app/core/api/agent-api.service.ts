import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { AcceptEdit, AgentSessionSummary, AgentWorkspace } from '../agent-models';
import { API_BASE } from '../config';
import { ExportFormat, Page } from '../models';
import { toParams } from './query';

@Injectable({ providedIn: 'root' })
export class AgentApi {
  private http = inject(HttpClient);
  private base = `${API_BASE}/agent`;

  list(cvId?: string, size = 30): Observable<Page<AgentSessionSummary>> {
    return this.http.get<Page<AgentSessionSummary>>(`${this.base}/sessions`, { params: toParams({ cvId, size }) });
  }

  create(cvId: string): Observable<AgentWorkspace> {
    return this.http.post<AgentWorkspace>(`${this.base}/sessions`, { cvId });
  }

  get(id: string): Observable<AgentWorkspace> {
    return this.http.get<AgentWorkspace>(`${this.base}/sessions/${id}`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/sessions/${id}`);
  }

  send(id: string, text: string): Observable<AgentWorkspace> {
    return this.http.post<AgentWorkspace>(`${this.base}/sessions/${id}/messages`, { text });
  }

  accept(proposalId: string, edit?: AcceptEdit): Observable<AgentWorkspace> {
    return this.http.post<AgentWorkspace>(`${this.base}/proposals/${proposalId}/accept`, edit ?? {});
  }

  reject(proposalId: string): Observable<AgentWorkspace> {
    return this.http.post<AgentWorkspace>(`${this.base}/proposals/${proposalId}/reject`, {});
  }

  regenerate(proposalId: string): Observable<AgentWorkspace> {
    return this.http.post<AgentWorkspace>(`${this.base}/proposals/${proposalId}/regenerate`, {});
  }

  acceptAll(id: string): Observable<AgentWorkspace> {
    return this.http.post<AgentWorkspace>(`${this.base}/sessions/${id}/accept-all`, {});
  }

  save(id: string): Observable<AgentWorkspace> {
    return this.http.post<AgentWorkspace>(`${this.base}/sessions/${id}/save`, {});
  }

  reset(id: string): Observable<AgentWorkspace> {
    return this.http.post<AgentWorkspace>(`${this.base}/sessions/${id}/reset`, {});
  }

  export(id: string, format: ExportFormat): Observable<Blob> {
    return this.http.post(`${this.base}/sessions/${id}/export`, { format }, { responseType: 'blob' });
  }
}
