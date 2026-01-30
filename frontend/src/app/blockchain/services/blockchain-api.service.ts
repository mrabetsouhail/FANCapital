import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { BLOCKCHAIN_API_BASE_URL } from '../blockchain-api.tokens';
import type { Fund, FundsListResponse } from '../models/fund.models';
import type {
  BuyRequest,
  QuoteBuyRequest,
  QuoteBuyResponse,
  QuoteSellRequest,
  QuoteSellResponse,
  SellRequest,
  TxResponse,
} from '../models/pricing.models';
import type { VniResponse } from '../models/oracle.models';
import type { P2PSettleRequest, P2PSettleResponse } from '../models/p2p.models';

@Injectable({ providedIn: 'root' })
export class BlockchainApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(BLOCKCHAIN_API_BASE_URL);

  // ---------- Funds registry ----------
  listFunds() {
    return this.http.get<FundsListResponse>(`${this.baseUrl}/funds`);
  }

  getFund(id: number) {
    return this.http.get<Fund>(`${this.baseUrl}/funds/${id}`);
  }

  // ---------- Oracle ----------
  getVni(token: string) {
    return this.http.get<VniResponse>(`${this.baseUrl}/oracle/vni`, { params: { token } });
  }

  // ---------- Liquidity Pool ----------
  quoteBuy(req: QuoteBuyRequest) {
    return this.http.post<QuoteBuyResponse>(`${this.baseUrl}/pool/quote-buy`, req);
  }

  quoteSell(req: QuoteSellRequest) {
    return this.http.post<QuoteSellResponse>(`${this.baseUrl}/pool/quote-sell`, req);
  }

  buy(req: BuyRequest) {
    return this.http.post<TxResponse>(`${this.baseUrl}/pool/buy`, req);
  }

  sell(req: SellRequest) {
    return this.http.post<TxResponse>(`${this.baseUrl}/pool/sell`, req);
  }

  // ---------- P2P ----------
  p2pSettle(req: P2PSettleRequest) {
    return this.http.post<P2PSettleResponse>(`${this.baseUrl}/p2p/settle`, req);
  }
}

