import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_BASE } from '../config';
import { SystemStatus } from '../models';

@Injectable({ providedIn: 'root' })
export class SystemApi {
  private http = inject(HttpClient);

  status(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>(`${API_BASE}/system/status`);
  }
}
