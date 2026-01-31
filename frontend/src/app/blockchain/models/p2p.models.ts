import type { Address } from './fund.models';
import type { TxResponse } from './pricing.models';

export interface P2PSettleRequest {
  token: Address;
  seller: Address;
  buyer: Address;
  tokenAmount: string; // 1e8
  pricePerToken: string; // TND per token (1e8)

  // --- MVP for "atomic order" signing (off-chain) ---
  // These fields allow us to attach a wallet signature to the request.
  // Backend/chain verification will be implemented later.
  maker?: Address;
  side?: 'buy' | 'sell';
  nonce?: string; // uint256 as string
  deadline?: string; // unix seconds as string
  signature?: string; // 0x... personal_sign signature
}

export type P2PSettleResponse = TxResponse;

