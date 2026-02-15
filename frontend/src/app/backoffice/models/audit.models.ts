export interface AuditRegistryRow {
  userId: string;
  type: string;
  email: string;
  resident?: boolean | null;
  cinOrPassportOrFiscalId?: string | null;
  fullNameOrCompany?: string | null;
  walletAddress?: string | null;
  atlasBalanceToken1e8: string;
  didonBalanceToken1e8: string;
  atlasLocked1e8?: string; // tokens bloqués pour AST
  didonLocked1e8?: string; // tokens bloqués pour AST
}

export interface AuditRegistryResponse {
  generatedAtSec: number;
  atBlockNumber?: number | null;
  rows: AuditRegistryRow[];
}

export interface AuditLogRow {
  id: string;
  createdAt?: string | null;
  action: string;
  actorEmail?: string | null;
  actorUserId?: string | null;
  targetUserId?: string | null;
  ip?: string | null;
  userAgent?: string | null;
  previousHash?: string | null;
  entryHash: string;
  details?: string | null;
}

export interface AuditLogsResponse {
  items: AuditLogRow[];
}

