import type { Address } from './fund.models';

export interface VniResponse {
  token: Address;
  vni: string; // 1e8
  updatedAt: string; // unix seconds as string
  volatilityBps?: number;
}

