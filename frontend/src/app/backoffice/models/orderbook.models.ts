export type OrderRow = {
  orderId: string;
  maker: string;
  side: 'BUY' | 'SELL';
  token: string;
  tokenSymbol: string;
  tokenAmount: string;
  pricePerToken: string;
  status: 'PENDING' | 'MATCHED' | 'SETTLED' | 'CANCELLED' | 'EXPIRED';
  createdAt: string;  // ISO
  deadline: number;
  ttLSeconds: number | null;
  matchedOrderId: string | null;
  settlementTxHash: string | null;
  filledTokenAmount: string | null;
  fallbackSuccess: boolean | null;  // true = piscine OK, false = fallback échoué, null = non fallback
};

export type PendingOrdersResponse = { orders: OrderRow[]; totalCount: number };
export type MatchedOrdersResponse = { orders: OrderRow[]; totalCount: number };
export type FallbackAuditResponse = { orders: OrderRow[]; totalCount: number };
