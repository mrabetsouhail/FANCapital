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

export interface AdvanceRequest {
  user: string;   // wallet address
  token: string;  // Atlas ou Didon
  collateralAmount: number;
  durationDays: number;
  model?: 'A' | 'B';  // A = taux fixe, B = PGP
}

/** Avance active (prêt en cours) — pour calendrier réel. */
export interface ActiveLoanInfo {
  loanId: string;
  user: string;
  token: string;
  collateralAmount: string;
  vniAtStart: string;
  principalTnd: string;  // 1e8
  startAt: number;       // epoch seconds
  durationDays: number;
  status: number;
  model?: 'A' | 'B';    // A = taux fixe, B = PGP
}

export interface TxResponse {
  txHash?: string;
  status: 'submitted' | 'mined' | 'failed';
  message?: string;
}

