export interface InvestorProfileResponse {
  user: string;
  whitelisted: boolean;
  kycLevel: number; // 0..2
  resident: boolean;
  score: number;
  tier: number; // 0..2
  feeLevel: number; // 0..4
  subscriptionActive: boolean;
}

