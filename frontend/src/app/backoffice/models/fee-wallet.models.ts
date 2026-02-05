export interface FundFeeSummary {
  fundId: number;
  fundName: string;
  fundSymbol: string;
  tokenAddress: string;
  poolAddress: string;
  totalFeesTnd: string;      // 1e8 (feesBase + VAT)
  feesBaseTnd: string;       // 1e8 (frais de base avant TVA)
  vatTnd: string;            // 1e8 (TVA 19%)
  transactionCount: number;  // Nombre de transactions ayant généré des frais
}

export interface FeeWalletDashboard {
  treasuryAddress: string;   // Adresse du wallet treasury
  cashTokenAddress: string;  // Adresse du CashTokenTND
  balanceTnd: string;       // 1e8 - Solde actuel du wallet
  feesByFund: FundFeeSummary[]; // Frais agrégés par fond
}
