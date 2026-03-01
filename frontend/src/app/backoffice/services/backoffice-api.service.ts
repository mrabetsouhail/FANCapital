import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

import type { FiscalDashboardResponse, TxResponse, WithdrawRequest } from '../models/fiscal.models';
import type { KycUserRow, SetInvestorScoreRequest, SetKycLevelRequest } from '../models/kyc.models';
import type { AuditLogsResponse, AuditRegistryResponse } from '../models/audit.models';
import type { FeeWalletDashboard } from '../models/fee-wallet.models';
import type { MultiSigInfo, MultiSigTransactionsList, SubmitTransactionRequest } from '../models/multisig.models';
import type { PendingOrdersResponse, MatchedOrdersResponse, FallbackAuditResponse } from '../models/orderbook.models';
import type { SubscriptionsMonitorResponse, ExpiringSubscriptionsResponse } from '../models/subscription.models';
import type { LockedAssetsResponse, RepaymentTrackingResponse } from '../models/escrow.models';
import type { MatriceInfo } from '../models/compartments.models';

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

  getAuditRegistry(q?: string) {
    let params = new HttpParams();
    const needle = q?.trim();
    if (needle) params = params.set('q', needle);
    return this.http.get<AuditRegistryResponse>('/api/backoffice/audit/registry', { params });
  }

  getAuditLogs(limit: number = 200) {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<AuditLogsResponse>('/api/backoffice/audit/logs', { params });
  }

  exportAuditCsv() {
    return this.http.get('/api/backoffice/audit/export/csv', { responseType: 'blob', observe: 'response' });
  }

  exportAuditPdf() {
    return this.http.get('/api/backoffice/audit/export/pdf', { responseType: 'blob', observe: 'response' });
  }

  getFeeWallet() {
    return this.http.get<FeeWalletDashboard>('/api/backoffice/fees/wallet');
  }

  // Multi-Sig Governance
  getMultiSigInfo() {
    return this.http.get<MultiSigInfo>('/api/backoffice/multisig/info');
  }

  listMultiSigTransactions() {
    return this.http.get<MultiSigTransactionsList>('/api/backoffice/multisig/transactions');
  }

  getMultiSigTransaction(txId: string) {
    return this.http.get<any>(`/api/backoffice/multisig/transactions/${txId}`);
  }

  submitMultiSigTransaction(req: SubmitTransactionRequest) {
    return this.http.post<TxResponse>('/api/backoffice/multisig/submit', req);
  }

  confirmMultiSigTransaction(txId: string) {
    return this.http.post<TxResponse>(`/api/backoffice/multisig/confirm/${txId}`, {});
  }

  executeMultiSigTransaction(txId: string) {
    return this.http.post<TxResponse>(`/api/backoffice/multisig/execute/${txId}`, {});
  }

  // Order Book P2P & Fallback
  listPendingOrders() {
    return this.http.get<PendingOrdersResponse>('/api/backoffice/orderbook/pending');
  }

  listMatchedOrders() {
    return this.http.get<MatchedOrdersResponse>('/api/backoffice/orderbook/matched');
  }

  listFallbackAudit() {
    return this.http.get<FallbackAuditResponse>('/api/backoffice/orderbook/fallback');
  }

  // Abonnements Premium
  getSubscriptionsMonitor() {
    return this.http.get<SubscriptionsMonitorResponse>('/api/backoffice/subscriptions/monitor');
  }

  getExpiringSubscriptions() {
    return this.http.get<ExpiringSubscriptionsResponse>('/api/backoffice/subscriptions/expiring-soon');
  }

  // Escrow & Libération au Prorata
  getLockedAssets() {
    return this.http.get<LockedAssetsResponse>('/api/backoffice/escrow/locked-assets');
  }

  getRepaymentTracking() {
    return this.http.get<RepaymentTrackingResponse>('/api/backoffice/escrow/repayment-tracking');
  }

  /** Architecture des Compartiments (La Matrice) */
  getCompartments() {
    return this.http.get<MatriceInfo>('/api/backoffice/compartments');
  }
}

