package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.config.WalletProperties;
import com.fancapital.backend.security.AesGcmEncryptor;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

/**
 * WaaS-like wallet provisioning (MVP):
 * - Generate EOA keypair on backend
 * - Store address + encrypted private key in DB
 *
 * NOTE: Production WaaS should use MPC/HSM/KMS. This is dev/MVP only.
 */
@Service
public class WalletProvisioningService {
  private final AppUserRepository repo;
  private final WalletProperties props;

  public WalletProvisioningService(AppUserRepository repo, WalletProperties props) {
    this.repo = repo;
    this.props = props;
  }

  @Transactional
  public String ensureProvisionedForKycLevel1(String userId) {
    AppUser u = repo.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
    if (u.getKycLevel() < 1) {
      throw new IllegalArgumentException("KYC Level 1 required");
    }
    if (u.getWalletAddress() != null && !u.getWalletAddress().isBlank()) {
      return u.getWalletAddress();
    }
    String encKeyB64 = props.encKey();
    if (encKeyB64 == null || encKeyB64.isBlank()) {
      throw new IllegalStateException("WALLET_ENC_KEY not configured (wallet.enc-key).");
    }
    byte[] key = Base64.getDecoder().decode(encKeyB64.trim());
    AesGcmEncryptor enc = new AesGcmEncryptor(key);

    try {
      ECKeyPair kp = Keys.createEcKeyPair();
      String address = "0x" + Keys.getAddress(kp.getPublicKey());
      String privHex = Numeric.toHexStringNoPrefixZeroPadded(kp.getPrivateKey(), 64);
      String privEnc = enc.encryptToB64(privHex);

      u.setWalletAddress(address);
      u.setWalletPrivateKeyEnc(privEnc);
      u.setWalletLinkedAt(Instant.now());
      repo.save(u);
      return address;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to provision wallet: " + e.getMessage(), e);
    }
  }
}

