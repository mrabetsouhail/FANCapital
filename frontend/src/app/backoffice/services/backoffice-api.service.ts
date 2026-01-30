import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import type { FiscalDashboardResponse, TxResponse, WithdrawRequest } from '../models/fiscal.models';

@Injectable({ providedIn: 'root' })
export class BackofficeApiService {
  private readonly http = inject(HttpClient);

  getFiscalSummary() {
    return this.http.get<FiscalDashboardResponse>('/api/backoffice/fiscal/summary');
  }

  withdrawToFisc(req: WithdrawRequest) {
    return this.http.post<TxResponse>('/api/backoffice/fiscal/withdraw', req);
  }
}

