package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.dto.AuthDtos;
import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

@Service
public class WalletLinkService {
  private final AppUserRepository repo;
  private final SecureRandom rng = new SecureRandom();

  public WalletLinkService(AppUserRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public AuthDtos.WalletChallengeResponse challenge(String userId) {
    AppUser u = repo.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
    String nonce = randomHex(16); // 128-bit
    u.setWalletLinkNonce(nonce);
    repo.save(u);

    String message = buildMessage(u.getId(), nonce);
    return new AuthDtos.WalletChallengeResponse(message);
  }

  @Transactional
  public String confirmAndStore(String userId, String signatureHex) {
    AppUser u = repo.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
    if (u.getWalletAddress() != null && !u.getWalletAddress().isBlank()) {
      throw new IllegalArgumentException("Wallet already linked/provisioned for this account");
    }
    String nonce = u.getWalletLinkNonce();
    if (nonce == null || nonce.isBlank()) {
      throw new IllegalArgumentException("No wallet challenge found. Request a new challenge.");
    }

    String message = buildMessage(u.getId(), nonce);
    String recovered = recoverAddressFromPersonalSign(message, signatureHex);

    u.setWalletAddress(recovered);
    u.setWalletLinkedAt(Instant.now());
    u.setWalletLinkNonce(null);
    repo.save(u);
    return recovered;
  }

  private static String buildMessage(String userId, String nonce) {
    // Keep it human-readable; wallet providers will show this text to the user.
    return String.join("\n",
        "FAN-Capital — Lier votre wallet",
        "Action: lier ce wallet à votre compte FAN-Capital.",
        "Ne signez que si vous êtes sur FAN-Capital.",
        "userId=" + userId,
        "nonce=" + nonce
    );
  }

  private static String recoverAddressFromPersonalSign(String message, String signatureHex) {
    byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);

    byte[] sig = Numeric.hexStringToByteArray(signatureHex);
    if (sig.length != 65) throw new IllegalArgumentException("Invalid signature length");

    byte vRaw = sig[64];
    byte v = (byte) (vRaw < 27 ? vRaw + 27 : vRaw);

    byte[] r = new byte[32];
    byte[] s = new byte[32];
    System.arraycopy(sig, 0, r, 0, 32);
    System.arraycopy(sig, 32, s, 0, 32);

    Sign.SignatureData sd = new Sign.SignatureData(v, r, s);
    try {
      // personal_sign == Ethereum Signed Message prefix (EIP-191)
      var key = Sign.signedPrefixedMessageToKey(msgBytes, sd);
      return "0x" + Keys.getAddress(key);
    } catch (Exception e) {
      throw new IllegalArgumentException("Signature verification failed");
    }
  }

  private String randomHex(int bytes) {
    byte[] b = new byte[bytes];
    rng.nextBytes(b);
    return Numeric.toHexStringNoPrefix(b);
  }
}

