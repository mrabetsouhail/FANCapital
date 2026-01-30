import type { Address } from './fund.models';
import type { TxResponse } from './pricing.models';

export interface P2PSettleRequest {
  token: Address;
  seller: Address;
  buyer: Address;
  tokenAmount: string; // 1e8
  pricePerToken: string; // TND per token (1e8)
}

export type P2PSettleResponse = TxResponse;

