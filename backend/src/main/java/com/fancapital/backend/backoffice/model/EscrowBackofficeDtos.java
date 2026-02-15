package com.fancapital.backend.backoffice.model;

import java.math.BigInteger;
import java.time.Instant;

/**
 * DTOs pour le backoffice Escrow & Libération au Prorata.
 */
public class EscrowBackofficeDtos {

  /** Ligne du registre des actifs bloqués par utilisateur. */
  public record LockedAssetRow(
      String userId,
      String email,
      String fullName,
      String walletAddress,
      String atlasLocked1e8,
      String didonLocked1e8,
      String atlasBalance1e8,
      String didonBalance1e8
  ) {}

  public record LockedAssetsResponse(
      java.util.List<LockedAssetRow> rows,
      int totalCount
  ) {}

  /** Ligne du suivi des remboursements (prêt actif). */
  public record RepaymentTrackingRow(
      String loanId,
      String userWallet,
      String userEmail,
      String userFullName,
      String tokenSymbol,
      String originalCollateral1e8,
      String currentLocked1e8,
      String originalPrincipalTnd1e8,
      String remainingDebtTnd1e8,
      String repaidPercent,
      String collateralReleasedPercent,
      long durationDays,
      long startAt,
      String scheduleLabel,      // "3 mois", "4 mois", "5 mois", "12 mois"
      int status
  ) {}

  public record RepaymentTrackingResponse(
      java.util.List<RepaymentTrackingRow> rows,
      int totalCount
  ) {}
}
