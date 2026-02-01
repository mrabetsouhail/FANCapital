export type KycLevel = 0 | 1 | 2;

export interface KycUserRow {
  id: string;
  email: string;
  type: string | null;
  kycLevel: number;
  resident: boolean | null;
  walletAddress: string | null;
  createdAt: string | null;
  kycValidatedAt: string | null;
}

export interface SetKycLevelRequest {
  userId: string;
  level: KycLevel;
}

export interface SetInvestorScoreRequest {
  userId: string;
  score: number; // 0..100
}

