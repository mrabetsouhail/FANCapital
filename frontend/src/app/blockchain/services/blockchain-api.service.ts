import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { BLOCKCHAIN_API_BASE_URL } from '../blockchain-api.tokens';
import type { Fund, FundsListResponse } from '../models/fund.models';
import type {
  AdvanceRequest,
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
import type {
  SubmitOrderRequest,
  SubmitOrderResponse,
  OrdersListResponse,
  Order,
  CancelOrderResponse,
} from '../models/orderbook.models';
import type { PortfolioResponse } from '../models/portfolio.models';
import type { InvestorProfileResponse, SciScoreResult, SciPushResult } from '../models/investor.models';
import type { TxHistoryResponse } from '../models/tx-history.models';

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

  // ---------- Portfolio ----------
  getPortfolio(user: string) {
    return this.http.get<PortfolioResponse>(`${this.baseUrl}/portfolio`, { params: { user } });
  }

  // ---------- Investor / KYC ----------
  getInvestorProfile(user: string) {
    return this.http.get<InvestorProfileResponse>(`${this.baseUrl}/investor/profile`, { params: { user } });
  }

  /** SCI v4.5: calcul du score et tier effectif. */
  getSciScore(user: string) {
    return this.http.get<SciScoreResult>(`${this.baseUrl}/investor/sci`, { params: { user } });
  }

  /** SCI v4.5: push score + tier effectif vers la blockchain. */
  pushSciScore(user: string) {
    return this.http.post<SciPushResult>(`${this.baseUrl}/investor/sci/push`, null, { params: { user } });
  }

  /** Alimente la Cash Wallet (dev/test). Mint TND on-chain. */
  seedCash(user: string, amount: number = 5000) {
    return this.http.post<{ status: string; message: string }>(`${this.baseUrl}/seed/cash`, null, {
      params: { user, amount },
    });
  }

  // ---------- Tx History ----------
  getTxHistory(user: string, limit: number = 150) {
    return this.http.get<TxHistoryResponse>(`${this.baseUrl}/tx/history`, { params: { user, limit } as any });
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

  /** Demande d'avance sur titres (AST). Token: Atlas ou Didon. */
  requestAdvance(req: AdvanceRequest) {
    return this.http.post<TxResponse>(`${this.baseUrl}/advance/request`, req);
  }

  // ---------- P2P ----------
  p2pSettle(req: P2PSettleRequest) {
    return this.http.post<P2PSettleResponse>(`${this.baseUrl}/p2p/settle`, req);
  }

  // ---------- P2P Order Book ----------
  submitOrder(req: SubmitOrderRequest) {
    return this.http.post<SubmitOrderResponse>(`${this.baseUrl}/p2p/order`, req);
  }

  listOrders(token?: string, side?: 'buy' | 'sell') {
    const params: any = {};
    if (token) params.token = token;
    if (side) params.side = side;
    return this.http.get<OrdersListResponse>(`${this.baseUrl}/p2p/orders`, { params });
  }

  getOrder(orderId: string) {
    return this.http.get<Order>(`${this.baseUrl}/p2p/order/${orderId}`);
  }

  cancelOrder(orderId: string, maker: string) {
    return this.http.delete<CancelOrderResponse>(`${this.baseUrl}/p2p/order/${orderId}`, {
      params: { maker },
    });
  }
}

