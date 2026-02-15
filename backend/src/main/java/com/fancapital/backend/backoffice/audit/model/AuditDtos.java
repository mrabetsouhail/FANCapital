package com.fancapital.backend.backoffice.audit.model;

import java.util.List;

public class AuditDtos {
  public record AuditRegistryRow(
      String userId,
      String type,
      String email,
      Boolean resident,
      String cinOrPassportOrFiscalId,
      String fullNameOrCompany,
      String walletAddress,
      String atlasBalanceToken1e8,
      String didonBalanceToken1e8,
      String atlasLocked1e8,
      String didonLocked1e8
  ) {}

  public record AuditRegistryResponse(
      long generatedAtSec,
      Long atBlockNumber,
      List<AuditRegistryRow> rows
  ) {}

  public record AuditLogRow(
      String id,
      String createdAt,
      String action,
      String actorEmail,
      String actorUserId,
      String targetUserId,
      String ip,
      String userAgent,
      String previousHash,
      String entryHash,
      String details
  ) {}

  public record AuditLogsResponse(
      List<AuditLogRow> items
  ) {}

  public record AuditAlertRow(
      String id,
      String createdAt,
      String severity,
      String kind,
      String userId,
      String walletAddress,
      String tokenAddress,
      String expectedBalance1e8,
      String onchainBalance1e8,
      long checkedAtBlock,
      String details,
      String resolvedAt
  ) {}

  public record AuditAlertsResponse(
      List<AuditAlertRow> items
  ) {}

  public record ReconcileResponse(
      long latestBlock,
      int tokensSynced,
      long transfersProcessed,
      int alertsCreated
  ) {}

  public record CheckpointResponse(
      String id,
      String createdAt,
      long blockNumber,
      String blockHash,
      String tokenAddress,
      String totalSupply1e8,
      String proofHash,
      String previousCheckpointHash,
      String metadata
  ) {}

  public record CheckpointVerifyResponse(
      boolean valid,
      String message
  ) {}

  public record BusinessContextResponse(
      String id,
      String createdAt,
      String transactionHash,
      String businessContextId,
      String contractAddress,
      String operationType,
      String description,
      String accountingDocumentId
  ) {}
}

