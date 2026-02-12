package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.blockchain.model.PortfolioDtos.PortfolioResponse;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Calcul du Score de Confiance Institutionnelle (SCI) v4.5.
 * <p>
 * Formule: SCI = (AUM_90j × 0.5) + (KYC_depth × 0.2) + (Behavior × 0.15) + (Risk × 0.15)
 * <ul>
 *   <li>AUM (50%): Volume moyen des actifs sur 90j. Favorise le réinvestissement via Credit Wallet.</li>
 *   <li>KYC Depth (20%): 10 pts Niveau 1, 20 pts Niveau 2.</li>
 *   <li>Behavior (15%): Récurrence transactionnelle (bonus fidélité).</li>
 *   <li>Risk (15%): Surveillance ratio d'endettement Avance sur Titres.</li>
 * </ul>
 * <p>
 * Loi du minimum (conformité LBA/FT): Level_Effective = min(Level_Score, Level_KYC)
 */
@Service
public class SciScoreService {

  private static final double WEIGHT_AUM = 0.50;
  private static final double WEIGHT_KYC = 0.20;
  private static final double WEIGHT_BEHAVIOR = 0.15;
  private static final double WEIGHT_RISK = 0.15;

  private static final int KYC_DEPTH_L1 = 10;
  private static final int KYC_DEPTH_L2 = 20;

  /** AUM max pour normalisation 0-100 (ex: 1M TND). */
  private static final BigDecimal AUM_NORMALIZATION_TND = BigDecimal.valueOf(1_000_000);

  /** Nombre de tx pour Behavior max (100 pts). */
  private static final int BEHAVIOR_TX_THRESHOLD = 50;

  private final BlockchainReadService blockchainRead;
  private final AppUserRepository userRepo;
  private final DebtManager debtManager;
  private final AumSnapshotService aumSnapshotService;

  public SciScoreService(BlockchainReadService blockchainRead, AppUserRepository userRepo, DebtManager debtManager,
      AumSnapshotService aumSnapshotService) {
    this.blockchainRead = blockchainRead;
    this.userRepo = userRepo;
    this.debtManager = debtManager;
    this.aumSnapshotService = aumSnapshotService;
  }

  /**
   * Calcule le score SCI brut (0-100) pour une adresse wallet.
   */
  public SciScoreResult computeScore(String walletAddress) {
    // AUM (50%) - utilisation du portefeuille actuel comme proxy AUM_90j jusqu'à historique disponible
    double aumPoints = computeAumPoints(walletAddress);

    // KYC Depth (20%)
    double kycPoints = computeKycPoints(walletAddress);

    // Behavior (15%) - récurrence via historique de transactions
    double behaviorPoints = computeBehaviorPoints(walletAddress);

    // Risk (15%) - ratio d'endettement AST (placeholder 100 si pas d'AST)
    double riskPoints = computeRiskPoints(walletAddress);

    double sciRaw = (aumPoints * WEIGHT_AUM) + (kycPoints * WEIGHT_KYC)
        + (behaviorPoints * WEIGHT_BEHAVIOR) + (riskPoints * WEIGHT_RISK);

    int score = (int) Math.round(Math.min(100, Math.max(0, sciRaw)));
    int tierScore = tierFromScore(score);
    int kycCap = kycTierCapFromWallet(walletAddress);
    int effective = effectiveTier(score, userRepo.findByWalletAddressIgnoreCase(walletAddress).map(AppUser::getKycLevel).orElse(0));

    return new SciScoreResult(
        walletAddress,
        score,
        aumPoints,
        kycPoints,
        behaviorPoints,
        riskPoints,
        tierScore,
        kycCap,
        effective
    );
  }

  /**
   * AUM (50%): Inertie sur 90 jours (Spécifications Financières v4.7).
   * Si historique disponible → moyenne AUM_90j ; sinon → portefeuille actuel comme proxy.
   */
  private double computeAumPoints(String walletAddress) {
    BigDecimal aum1e8 = aumSnapshotService.getAum90DaysAvgTnd(walletAddress);
    if (aum1e8 == null) {
      PortfolioResponse port = blockchainRead.portfolio(walletAddress);
      BigDecimal total = new BigDecimal(port.totalValueTnd()).add(new BigDecimal(port.cashBalanceTnd()));
      aum1e8 = total;
    }
    BigDecimal aumTnd = aum1e8.divide(BigDecimal.valueOf(100_000_000), 8, RoundingMode.HALF_UP);
    BigDecimal ratio = aumTnd.min(AUM_NORMALIZATION_TND).divide(AUM_NORMALIZATION_TND, 8, RoundingMode.HALF_UP);
    return ratio.doubleValue() * 100;
  }

  private double computeKycPoints(String walletAddress) {
    return userRepo.findByWalletAddressIgnoreCase(walletAddress)
        .map(u -> u.getKycLevel() >= 2 ? KYC_DEPTH_L2 : (u.getKycLevel() >= 1 ? KYC_DEPTH_L1 : 0))
        .orElse(0);
  }

  private double computeBehaviorPoints(String walletAddress) {
    var txHistory = blockchainRead.txHistory(walletAddress, 500);
    int count = txHistory.items() != null ? txHistory.items().size() : 0;
    double ratio = Math.min(1.0, (double) count / BEHAVIOR_TX_THRESHOLD);
    return ratio * 100;
  }

  /**
   * Risk (15%): Surveillance ratio d'endettement AST (Spécifications Financières v4.7).
   * LTV = Dette / Valeur collatéral. Si pas d'avance active → 100 pts (sans risque).
   */
  private double computeRiskPoints(String walletAddress) {
    var loan = debtManager.getActiveLoanForUser(walletAddress);
    if (loan == null) return 100.0;
    BigInteger collateralValueTnd = loan.collateralAmount()
        .multiply(loan.vniAtStart())
        .divide(BigInteger.valueOf(100_000_000));
    if (collateralValueTnd.signum() <= 0) return 100.0;
    double ltv = loan.principalTnd().doubleValue() / collateralValueTnd.doubleValue() * 100;
    if (ltv >= 85) return 0.0;   // Liquidation
    if (ltv >= 75) return 50.0;  // Margin Call
    return 100.0;                // Sécurisé
  }

  /** Tiers SCI v4.5: BRONZE 0-15, SILVER 16-35, GOLD 36-55, PLATINUM 56-84, DIAMOND 85+ */
  public static int tierFromScore(int score) {
    if (score <= 15) return 0; // BRONZE
    if (score <= 35) return 1; // SILVER
    if (score <= 55) return 2; // GOLD
    if (score <= 84) return 3; // PLATINUM
    return 4; // DIAMOND
  }

  /**
   * Tier maximal autorisé par KYC (loi du minimum - Spécifications Financières v4.7).
   * Gold et supérieurs nécessitent impérativement KYC Niveau 2. KYC 1 = Silver max.
   */
  public int kycTierCapFromWallet(String walletAddress) {
    return userRepo.findByWalletAddressIgnoreCase(walletAddress)
        .map(this::kycTierCap)
        .orElse(0);
  }

  public int kycTierCap(AppUser user) {
    if (user == null || user.getKycLevel() < 1) return 0; // BRONZE
    if (user.getKycLevel() < 2) return 1; // KYC1 → Silver max (Gold+ nécessite KYC2)
    return 4; // KYC2 → full tier (Diamond)
  }

  /**
   * Tier effectif = min(tier_score, tier_kyc). Spécifications Financières v4.7.
   * KYC 1 → Silver max ; KYC 2 → full (Gold+ nécessite KYC 2).
   */
  public static int effectiveTier(int score, int kycLevel) {
    int tierScore = tierFromScore(score);
    int tierKycCap = kycLevel >= 2 ? 4 : (kycLevel >= 1 ? 1 : 0);
    return Math.min(tierScore, tierKycCap);
  }

  public record SciScoreResult(
      String walletAddress,
      int score,
      double aumPoints,
      double kycPoints,
      double behaviorPoints,
      double riskPoints,
      int tierFromScore,
      int kycTierCap,
      int effectiveTier
  ) {}
}
