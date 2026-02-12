package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.blockchain.service.BlockchainReadService;
import com.fancapital.backend.blockchain.service.InvestorRegistryWriteService;
import com.fancapital.backend.blockchain.service.SciScorePushService;
import java.math.BigInteger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Active l'abonnement Premium (versement via Cash Wallet).
 * Vérifie le solde Cash Wallet avant activation.
 */
@Service
public class PremiumActivationService {

  /** Montant minimum TND (1e8) pour activer Premium. 1 TND = versement symbolique depuis Cash Wallet. */
  private static final BigInteger MIN_CASH_1E8 = BigInteger.valueOf(100_000_000);

  private final AppUserRepository userRepo;
  private final BlockchainReadService blockchainRead;
  private final InvestorRegistryWriteService registryWrite;
  private final SciScorePushService sciPush;

  public PremiumActivationService(
      AppUserRepository userRepo,
      BlockchainReadService blockchainRead,
      InvestorRegistryWriteService registryWrite,
      SciScorePushService sciPush
  ) {
    this.userRepo = userRepo;
    this.blockchainRead = blockchainRead;
    this.registryWrite = registryWrite;
    this.sciPush = sciPush;
  }

  @Transactional
  public String activateForUser(String userId) {
    AppUser user = userRepo.findById(userId).orElse(null);
    if (user == null) {
      throw new IllegalArgumentException("Utilisateur introuvable");
    }
    if (user.isPremium()) {
      return "Abonnement Premium déjà actif.";
    }
    String wallet = user.getWalletAddress();
    if (wallet == null || wallet.isBlank() || !wallet.startsWith("0x") || wallet.length() != 42) {
      throw new IllegalArgumentException("Wallet non configuré. Complétez votre profil et liez un wallet.");
    }

    var port = blockchainRead.portfolio(wallet);
    BigInteger cash = new BigInteger(port.cashBalanceTnd() != null ? port.cashBalanceTnd() : "0");
    if (cash.compareTo(MIN_CASH_1E8) < 0) {
      throw new IllegalArgumentException(
          "Solde Cash Wallet insuffisant. Alimentez votre Cash Wallet depuis la page Passer Ordre.");
    }

    user.setPremium(true);
    userRepo.save(user);

    try {
      registryWrite.setSubscriptionActive(wallet, true);
      sciPush.pushForWallet(wallet);
    } catch (Exception e) {
      // Rollback premium en base si la synchro on-chain échoue
      user.setPremium(false);
      userRepo.save(user);
      throw new IllegalStateException("Erreur synchro blockchain: " + e.getMessage());
    }

    return "Abonnement Premium activé avec succès.";
  }
}
