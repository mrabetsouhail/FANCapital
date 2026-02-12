package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import org.springframework.stereotype.Service;

/**
 * Pousse le score SCI et le tier effectif (feeLevel) vers InvestorRegistry on-chain.
 * Conformité LBA/FT: feeLevel = min(tier_score, tier_kyc).
 */
@Service
public class SciScorePushService {

  private final SciScoreService sciScore;
  private final InvestorRegistryWriteService registryWrite;
  private final AppUserRepository userRepo;

  public SciScorePushService(
      SciScoreService sciScore,
      InvestorRegistryWriteService registryWrite,
      AppUserRepository userRepo
  ) {
    this.sciScore = sciScore;
    this.registryWrite = registryWrite;
    this.userRepo = userRepo;
  }

  /**
   * Recalcule le SCI, détermine le tier effectif (loi du minimum) et pousse vers la blockchain.
   */
  public SciPushResult pushForWallet(String walletAddress) {
    var result = sciScore.computeScore(walletAddress);

    registryWrite.setScore(walletAddress, result.score());
    registryWrite.setFeeLevel(walletAddress, result.effectiveTier());

    // Sync premium (AppUser.premium) → subscriptionActive on-chain (Mod AST 5)
    userRepo.findByWalletAddressIgnoreCase(walletAddress)
        .ifPresent(u -> registryWrite.setSubscriptionActive(walletAddress, u.isPremium()));

    return new SciPushResult(
        result.walletAddress(),
        result.score(),
        result.tierFromScore(),
        result.effectiveTier(),
        result.kycTierCap(),
        true
    );
  }

  /**
   * Recalcule et pousse pour un utilisateur par ID (utilisé après validation KYC).
   */
  public SciPushResult pushForUser(String userId) {
    AppUser user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    String wallet = user.getWalletAddress();
    if (wallet == null || wallet.isBlank()) {
      throw new IllegalStateException("User has no walletAddress");
    }
    return pushForWallet(wallet);
  }

  public record SciPushResult(
      String walletAddress,
      int score,
      int tierFromScore,
      int effectiveTier,
      int kycTierCap,
      boolean pushed
  ) {}
}
