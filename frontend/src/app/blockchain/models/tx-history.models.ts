export type TxKind = 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW';

export interface TxRow {
  id: string;
  kind: TxKind;
  fundId: number; // -1 for cash
  fundName: string;
  fundSymbol: string;
  tokenAddress: string;
  amountTnd1e8: string;
  amountToken1e8: string;
  priceClient1e8: string;
  blockNumber: number;
  timestampSec: number;
  txHash: string;
}

export interface TxHistoryResponse {
  user: string;
  items: TxRow[];
}

