package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.config.WalletProperties;
import com.fancapital.backend.security.AesGcmEncryptor;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;

@Service
public class WaasUserWalletService {
  private final AppUserRepository repo;
  private final WalletProperties walletProps;

  public WaasUserWalletService(AppUserRepository repo, WalletProperties walletProps) {
    this.repo = repo;
    this.walletProps = walletProps;
  }

  public Credentials credentialsForUser(String userId) {
    AppUser u = repo.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
    String enc = u.getWalletPrivateKeyEnc();
    if (enc == null || enc.isBlank()) {
      throw new IllegalStateException("User has no WaaS private key (walletPrivateKeyEnc missing)");
    }
    String keyB64 = walletProps.encKey();
    if (keyB64 == null || keyB64.isBlank()) {
      throw new IllegalStateException("WALLET_ENC_KEY not configured (wallet.enc-key).");
    }
    byte[] key = Base64.getDecoder().decode(keyB64.trim());
    AesGcmEncryptor aes = new AesGcmEncryptor(key);
    String privHex = aes.decryptFromB64(enc).trim();
    if (!privHex.startsWith("0x")) privHex = "0x" + privHex;
    return Credentials.create(privHex);
  }
}

