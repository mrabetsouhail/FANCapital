export interface FundTaxSummary {
  fundId: number;
  name: string;
  symbol: string;
  token: string;
  rasCollectedTnd: string;
  rasEvents: number;
}

export interface FiscalDashboardResponse {
  deploymentsPath: string;
  taxVault: string;
  cashToken: string;
  fiscAddress: string;
  taxVaultBalanceTnd: string;
  byFund: FundTaxSummary[];
}

export interface WithdrawRequest {
  amount: string;
}

export interface TxResponse {
  status: string;
  txHash?: string;
  message?: string;
}

