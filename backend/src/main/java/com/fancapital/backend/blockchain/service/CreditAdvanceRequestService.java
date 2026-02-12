package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

/**
 * Demande d'avance sur titres (AST) — signée par l'utilisateur via WaaS.
 * Appelle CreditModelA.requestAdvance(token, collateralAmount, durationDays).
 */
@Service
public class CreditAdvanceRequestService {

  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L);

  private final Web3j web3j;
  private final DeploymentRegistry registry;
  private final AppUserRepository userRepo;
  private final WaasUserWalletService waasWallets;

  public CreditAdvanceRequestService(Web3j web3j, DeploymentRegistry registry,
      AppUserRepository userRepo, WaasUserWalletService waasWallets) {
    this.web3j = web3j;
    this.registry = registry;
    this.userRepo = userRepo;
    this.waasWallets = waasWallets;
  }

  /**
   * Soumet une demande d'avance pour l'utilisateur (signée via WaaS).
   *
   * @param userWallet  adresse wallet de l'utilisateur
   * @param tokenAddr   adresse du token collatéral (Atlas ou Didon)
   * @param collateralAmountTokens quantité de tokens (entier, ex: 67)
   * @param durationDays durée en jours (ex: 90)
   * @return hash de la transaction
   */
  public String requestAdvanceForUser(String userWallet, String tokenAddr, long collateralAmountTokens, long durationDays) {
    if (userWallet == null || userWallet.isBlank() || !userWallet.startsWith("0x") || userWallet.length() != 42) {
      throw new IllegalArgumentException("Wallet utilisateur invalide");
    }
    if (tokenAddr == null || tokenAddr.isBlank()) {
      throw new IllegalArgumentException("Adresse token requise");
    }
    if (collateralAmountTokens <= 0 || durationDays <= 0) {
      throw new IllegalArgumentException("Collatéral et durée doivent être > 0");
    }

    AppUser user = userRepo.findByWalletAddressIgnoreCase(userWallet)
        .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé. Liez un wallet à votre compte."));
    if (user.getWalletPrivateKeyEnc() == null || user.getWalletPrivateKeyEnc().isBlank()) {
      throw new IllegalStateException("Wallet WaaS non configuré. Complétez le KYC pour créer votre wallet.");
    }

    String creditAddr = registry.getCreditModelAAddress();
    if (creditAddr == null || creditAddr.isBlank()) {
      throw new IllegalStateException("CreditModelA non déployé. Déployez les contrats d'abord.");
    }

    // collateralAmount en 1e8 (CPEF decimals)
    BigInteger collateralAmount = BigInteger.valueOf(collateralAmountTokens).multiply(PRICE_SCALE);
    BigInteger duration = BigInteger.valueOf(durationDays);

    Function fn = new Function(
        "requestAdvance",
        List.of(new Address(tokenAddr), new Uint256(collateralAmount), new Uint64(duration)),
        List.of()
    );

    Credentials creds = waasWallets.credentialsForUser(user.getId());
    return sendAsUser(creds, creditAddr, fn);
  }

  private String sendAsUser(Credentials credentials, String to, Function fn) {
    long chainId;
    try {
      chainId = web3j.ethChainId().send().getChainId().longValue();
    } catch (IOException e) {
      chainId = 31337;
    }
    TransactionManager tm = new RawTransactionManager(web3j, credentials, chainId);
    String data = FunctionEncoder.encode(fn);
    BigInteger gasPrice;
    try {
      EthGasPrice gp = web3j.ethGasPrice().send();
      gasPrice = gp.getGasPrice();
      if (gasPrice == null) gasPrice = BigInteger.valueOf(20_000_000_000L);
    } catch (IOException e) {
      gasPrice = BigInteger.valueOf(20_000_000_000L);
    }
    gasPrice = gasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);

    try {
      EthSendTransaction tx = (EthSendTransaction) tm.sendTransaction(gasPrice, BigInteger.valueOf(500_000), to, data, BigInteger.ZERO);
      if (tx.hasError()) {
        throw new IllegalStateException("EVM tx failed: " + tx.getError().getMessage());
      }
      return tx.getTransactionHash();
    } catch (IOException e) {
      throw new IllegalStateException("Erreur RPC: " + e.getMessage(), e);
    }
  }
}
