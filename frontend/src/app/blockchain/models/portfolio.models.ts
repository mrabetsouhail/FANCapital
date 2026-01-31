import type { Address, Fund } from './fund.models';

export interface PortfolioPosition {
  fundId: number;
  name: string;
  symbol: string;
  token: Address;
  pool: Address;
  oracle: Address;

  balanceTokens: string; // 1e8
  vni: string; // 1e8 (TND per token)
  prm: string; // 1e8 (TND per token)
  positionValueTnd: string; // 1e8
  unrealizedGainTnd: string; // 1e8
}

export interface PortfolioResponse {
  user: Address;
  positions: PortfolioPosition[];
  cashBalanceTnd: string; // 1e8
  totalValueTnd: string; // 1e8
  totalUnrealizedGainTnd: string; // 1e8
}

