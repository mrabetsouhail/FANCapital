export interface InvestorProfileResponse {
  user: string;
  whitelisted: boolean;
  kycLevel: number; // 0..2
  resident: boolean;
  score: number;
  tier: number; // 0..4
  feeLevel: number; // 0..4 (effective tier = min(score_tier, kyc_tier))
  subscriptionActive: boolean;
}

/** SCI v4.5 - résultat du calcul du score. */
export interface SciScoreResult {
  walletAddress: string;
  score: number;
  aumPoints: number;
  kycPoints: number;
  behaviorPoints: number;
  riskPoints: number;
  tierFromScore: number;  // 0=BRONZE, 1=SILVER, 2=GOLD, 3=PLATINUM, 4=DIAMOND
  kycTierCap: number;
  effectiveTier: number;
}

/** SCI v4.5 - résultat du push on-chain. */
export interface SciPushResult {
  walletAddress: string;
  score: number;
  tierFromScore: number;
  effectiveTier: number;
  kycTierCap: number;
  pushed: boolean;
}

