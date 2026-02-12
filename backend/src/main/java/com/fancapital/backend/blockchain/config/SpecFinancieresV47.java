package com.fancapital.backend.blockchain.config;

/**
 * Constantes issues des Spécifications Financières v4.7.
 * Alignement avec Table 2 (Taux et Durées de l'AST).
 * Sans frais de dossier (supprimé v4.7).
 */
public final class SpecFinancieresV47 {

  private SpecFinancieresV47() {}

  /** LTV max à l'émission */
  public static final int LTV_MAX_BPS = 70;

  /** Margin Call LTV */
  public static final int MARGIN_CALL_BPS = 75;

  /** Liquidation LTV */
  public static final int LIQUIDATION_BPS = 85;

  /** Durée max avance Diamond (v4.7: 12 mois) */
  public static final int DIAMOND_MAX_DAYS = 365;

  /** Tiers et durées max (jours): SILVER 3 mois, GOLD 4 mois, PLATINUM 5 mois, DIAMOND 12 mois */
  public static final int[] TIER_MAX_DURATION_DAYS = {0, 90, 120, 150, 365};

  /** Taux annuel Modèle A (%): BRONZE N/A, SILVER 5, GOLD 4.5, PLATINUM 3.5, DIAMOND 3 */
  public static final double[] TIER_INTEREST_RATES = {0, 5.0, 4.5, 3.5, 3.0};

  /** Table 1: Grille tarifaire abonnements (TND). [tier][duration] 0=trim, 1=sem, 2=annuel. -1 = non dispo. */
  public static final int[][] TIER_SUBSCRIPTION_TND = {
      {-1, -1, -1},    // BRONZE
      {45, 80, 150},   // SILVER
      {75, 135, 250},  // GOLD
      {150, 270, 500}, // PLATINUM
      {300, 540, 1000} // DIAMOND
  };
}
