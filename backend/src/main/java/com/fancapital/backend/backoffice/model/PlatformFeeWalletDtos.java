package com.fancapital.backend.backoffice.model;

import java.util.List;

/**
 * DTOs pour le wallet des frais de la plateforme.
 */
public class PlatformFeeWalletDtos {
  /**
   * Résumé des frais collectés par fond.
   */
  public record FundFeeSummary(
      int fundId,
      String fundName,
      String fundSymbol,
      String tokenAddress,
      String poolAddress,
      String totalFeesTnd,      // 1e8 (feesBase + VAT)
      String feesBaseTnd,       // 1e8 (frais de base avant TVA)
      String vatTnd,            // 1e8 (TVA 19%)
      long transactionCount     // Nombre de transactions ayant généré des frais
  ) {}

  /**
   * Dashboard complet du wallet des frais.
   */
  public record FeeWalletDashboard(
      String treasuryAddress,   // Adresse du wallet treasury
      String cashTokenAddress, // Adresse du CashTokenTND
      String balanceTnd,       // 1e8 - Solde actuel du wallet
      List<FundFeeSummary> feesByFund // Frais agrégés par fond
  ) {}
}
