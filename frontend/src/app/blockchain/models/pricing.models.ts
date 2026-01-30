import type { Address } from './fund.models';

export interface QuoteBuyRequest {
  token: Address;
  user: Address;
  tndIn: string; // 8 decimals as string (backend decides)
}

export interface QuoteSellRequest {
  token: Address;
  user: Address;
  tokenAmount: string; // 8 decimals as string
}

export interface QuoteBuyResponse {
  priceClient: string; // TND per token (1e8)
  minted: string; // token units (1e8)
  feeBase: string; // TND (1e8)
  vat: string; // TND (1e8)
  totalFee: string; // TND (1e8)
}

export interface QuoteSellResponse {
  priceClient: string; // TND per token (1e8)
  tndOut: string; // TND (1e8)
  feeBase: string; // TND (1e8)
  vat: string; // TND (1e8)
  totalFee: string; // TND (1e8)
  tax: string; // TND (1e8)
}

export interface BuyRequest {
  token: Address;
  user: Address;
  tndIn: string;
}

export interface SellRequest {
  token: Address;
  user: Address;
  tokenAmount: string;
}

export interface TxResponse {
  txHash?: string;
  status: 'submitted' | 'mined' | 'failed';
  message?: string;
}

