import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

import type { FiscalDashboardResponse, TxResponse, WithdrawRequest } from '../models/fiscal.models';
import type { KycUserRow, SetInvestorScoreRequest, SetKycLevelRequest } from '../models/kyc.models';

@Injectable({ providedIn: 'root' })
export class BackofficeApiService {
  private readonly http = inject(HttpClient);

  getFiscalSummary() {
    return this.http.get<FiscalDashboardResponse>('/api/backoffice/fiscal/summary');
  }

  withdrawToFisc(req: WithdrawRequest) {
    return this.http.post<TxResponse>('/api/backoffice/fiscal/withdraw', req);
  }

  listKycUsers(q?: string) {
    let params = new HttpParams();
    const needle = q?.trim();
    if (needle) params = params.set('q', needle);
    return this.http.get<KycUserRow[]>('/api/backoffice/kyc/users', { params });
  }

  setKycLevel(req: SetKycLevelRequest) {
    return this.http.post<any>('/api/backoffice/kyc/set-level', req);
  }

  setInvestorScore(req: SetInvestorScoreRequest) {
    return this.http.post<any>('/api/backoffice/kyc/set-score', req);
  }
}

