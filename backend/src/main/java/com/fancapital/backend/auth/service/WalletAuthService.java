package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.dto.AuthDtos;
import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.model.Notification.Priority;
import com.fancapital.backend.auth.model.Notification.Type;
import com.fancapital.backend.auth.repo.AppUserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;

@Service
public class WalletAuthService {
  private static final Duration NONCE_TTL = Duration.ofMinutes(5);

  private final AppUserRepository repo;
  private final JwtService jwtService;
  private final AuthService authService;
  private final SecureRandom rng = new SecureRandom();

  public WalletAuthService(AppUserRepository repo, JwtService jwtService, AuthService authService) {
    this.repo = repo;
    this.jwtService = jwtService;
    this.authService = authService;
  }

  @Transactional
  public AuthDtos.WalletChallengeResponse loginChallenge(String walletAddress) {
    AppUser u = repo.findByWalletAddressIgnoreCase(walletAddress)
        .orElseThrow(() -> new IllegalArgumentException("Wallet not linked"));

    String nonce = randomHex(16);
    u.setWalletAuthNonce(nonce);
    u.setWalletAuthNonceAt(Instant.now());
    repo.save(u);

    String message = buildLoginMessage(walletAddress, nonce);
    return new AuthDtos.WalletChallengeResponse(message);
  }

  @Transactional
  public AuthDtos.AuthResponse loginWithWallet(AuthDtos.WalletLoginRequest req) {
    AppUser u = repo.findByWalletAddressIgnoreCase(req.walletAddress())
        .orElseThrow(() -> new IllegalArgumentException("Wallet not linked"));

    String nonce = u.getWalletAuthNonce();
    Instant at = u.getWalletAuthNonceAt();
    if (nonce == null || nonce.isBlank() || at == null) {
      throw new IllegalArgumentException("No wallet login challenge found. Request a new challenge.");
    }
    if (at.plus(NONCE_TTL).isBefore(Instant.now())) {
      // expire + force new challenge
      u.setWalletAuthNonce(null);
      u.setWalletAuthNonceAt(null);
      repo.save(u);
      throw new IllegalArgumentException("Wallet login challenge expired. Request a new challenge.");
    }

    String message = buildLoginMessage(req.walletAddress(), nonce);
    String recovered = recoverAddressFromPersonalSign(message, req.signature());
    if (!recovered.equalsIgnoreCase(req.walletAddress())) {
      throw new IllegalArgumentException("Signature does not match walletAddress");
    }

    // one-time nonce
    u.setWalletAuthNonce(null);
    u.setWalletAuthNonceAt(null);
    repo.save(u);

    String token = jwtService.mint(u.getId(), u.getEmail(), u.getType().name());
    return new AuthDtos.AuthResponse(token, authService.toUserResponse(u));
  }

  private static String buildLoginMessage(String walletAddress, String nonce) {
    return String.join("\n",
        "FAN-Capital — Connexion Wallet",
        "Action: se connecter à FAN-Capital avec ce wallet.",
        "Ne signez que si vous êtes sur FAN-Capital.",
        "wallet=" + walletAddress,
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

