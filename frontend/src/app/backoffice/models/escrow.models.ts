export type LockedAssetRow = {
  userId: string;
  email: string;
  fullName: string;
  walletAddress: string;
  atlasLocked1e8: string;
  didonLocked1e8: string;
  atlasBalance1e8: string;
  didonBalance1e8: string;
};

export type LockedAssetsResponse = {
  rows: LockedAssetRow[];
  totalCount: number;
};

export type RepaymentTrackingRow = {
  loanId: string;
  userWallet: string;
  userEmail: string;
  userFullName: string;
  tokenSymbol: string;
  originalCollateral1e8: string;
  currentLocked1e8: string;
  originalPrincipalTnd1e8: string;
  remainingDebtTnd1e8: string;
  repaidPercent: string;
  collateralReleasedPercent: string;
  durationDays: number;
  startAt: number;
  scheduleLabel: string;
  status: number;
};

export type RepaymentTrackingResponse = {
  rows: RepaymentTrackingRow[];
  totalCount: number;
};
