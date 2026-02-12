export interface MultiSigInfo {
  councilAddress: string;
  owners: string[];
  threshold: string;
  totalOwners: string;
  transactionsCount: string;
}

export interface MultiSigTransaction {
  txId: string;
  to: string;
  value: string;
  data: string;
  executed: boolean;
  confirmations: string;
  threshold: string;
  canExecute: boolean;
}

export interface MultiSigTransactionsList {
  transactions: MultiSigTransaction[];
}

export interface SubmitTransactionRequest {
  to: string;
  value: string;
  data: string;
}
