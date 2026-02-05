import type { Address } from './fund.models';
import type { TxResponse } from './pricing.models';

export type OrderSide = 'BUY' | 'SELL';
export type OrderStatus = 'PENDING' | 'MATCHED' | 'SETTLED' | 'CANCELLED' | 'EXPIRED';

export interface SubmitOrderRequest {
  maker?: Address; // Ignoré par le backend - remplacé par l'utilisateur connecté (WaaS)
  side: 'buy' | 'sell';
  token: Address;
  tokenAmount: string; // 1e8
  pricePerToken: string; // 1e8
  nonce?: string; // uint256 as string (optional, auto-generated if not provided)
  deadline?: string; // unix seconds as string (optional, defaults to +1 hour)
  signature?: string; // Ignoré - pas nécessaire avec WaaS
}

export interface Order {
  orderId: string;
  maker: Address;
  side: OrderSide;
  token: Address;
  tokenAmount: string; // 1e8
  pricePerToken: string; // 1e8
  nonce: string;
  deadline: number; // unix timestamp
  status: OrderStatus;
  createdAt: string; // ISO timestamp
  signature?: string;
  matchedOrderId?: string;
  settlementTxHash?: string;
}

export interface SubmitOrderResponse {
  orderId: string;
  status: OrderStatus;
  message: string;
  matchedOrder: Order | null; // Si l'ordre a été immédiatement matché
}

export interface OrdersListResponse {
  orders: Order[];
  totalCount: number;
}

export interface CancelOrderResponse {
  orderId: string;
  cancelled: boolean;
  message: string;
}
