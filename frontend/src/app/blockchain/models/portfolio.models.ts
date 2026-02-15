import type { Address, Fund } from './fund.models';

export interface PortfolioPosition {
  fundId: number;
  name: string;
  symbol: string;
  token: Address;
  pool: Address;
  oracle: Address;

  balanceTokens: string; // 1e8
  lockedTokens1e8: string; // 1e8 - tokens bloqués pour l'avance sur titres
  vni: string; // 1e8 (TND per token)
  prm: string; // 1e8 (TND per token)
  positionValueTnd: string; // 1e8
  unrealizedGainTnd: string; // 1e8
}

export interface PortfolioResponse {
  user: Address;
  positions: PortfolioPosition[];
  cashBalanceTnd: string; // 1e8
  creditLineTnd: string; // 1e8 - plafond: KYC1=5000, KYC2=10000
  creditDebtTnd: string; // 1e8 - avance créditée (0 si pas d'avance AST)
  totalValueTnd: string; // 1e8
  totalUnrealizedGainTnd: string; // 1e8
}

