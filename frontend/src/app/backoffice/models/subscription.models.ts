export type SubscriptionRow = {
  userId: string;
  email: string;
  fullName: string;
  walletAddress: string;
  duration: string;
  startAt: string | null;   // ISO
  expiresAt: string | null; // ISO
  daysRemaining: number | null;
  expiringSoon: boolean;
};

export type SubscriptionsMonitorResponse = {
  subscriptions: SubscriptionRow[];
  totalCount: number;
  trimestrielCount: number;
  semestrielCount: number;
  annuelCount: number;
  expiringSoonCount: number;
};

export type ExpiringSubscriptionRow = {
  userId: string;
  email: string;
  fullName: string;
  expiresAt: string;
  daysRemaining: number;
  duration: string;
};

export type ExpiringSubscriptionsResponse = {
  subscriptions: ExpiringSubscriptionRow[];
  totalCount: number;
};
