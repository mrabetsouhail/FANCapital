export type Address = `0x${string}`;

export interface Fund {
  id: number;
  name: string;
  symbol: string;
  token: Address;
  pool: Address;
  oracle: Address;
  createdAt?: string; // ISO or unix string depending backend
}

export interface FundsListResponse {
  funds: Fund[];
}

