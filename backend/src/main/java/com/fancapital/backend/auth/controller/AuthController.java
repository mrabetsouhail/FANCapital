package com.fancapital.backend.auth.controller;

import com.fancapital.backend.auth.dto.AuthDtos;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.auth.service.AuthService;
import com.fancapital.backend.auth.service.WalletAuthService;
import com.fancapital.backend.auth.service.WalletLinkService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;
  private final com.fancapital.backend.auth.service.JwtService jwtService;
  private final AppUserRepository repo;
  private final WalletLinkService walletLinkService;
  private final WalletAuthService walletAuthService;

  public AuthController(
      AuthService authService,
      com.fancapital.backend.auth.service.JwtService jwtService,
      AppUserRepository repo,
      WalletLinkService walletLinkService,
      WalletAuthService walletAuthService
  ) {
    this.authService = authService;
    this.jwtService = jwtService;
    this.repo = repo;
    this.walletLinkService = walletLinkService;
    this.walletAuthService = walletAuthService;
  }

  @PostMapping("/register/particulier")
  public AuthDtos.AuthResponse registerParticulier(@Valid @RequestBody AuthDtos.RegisterParticulierRequest req) {
    return authService.registerParticulier(req);
  }

  @PostMapping("/register/entreprise")
  public AuthDtos.AuthResponse registerEntreprise(@Valid @RequestBody AuthDtos.RegisterEntrepriseRequest req) {
    return authService.registerEntreprise(req);
  }

  @PostMapping("/login")
  public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
    return authService.login(req);
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return ResponseEntity.status(401).body(Map.of("message", "Missing Bearer token"));
    }
    String token = authorization.substring("Bearer ".length()).trim();
    Claims claims;
    try {
      claims = jwtService.parse(token);
    } catch (Exception e) {
      return ResponseEntity.status(401).body(Map.of("message", "Invalid token"));
    }

    String userId = claims.getSubject();
    var user = repo.findById(userId).orElse(null);
    if (user == null) {
      return ResponseEntity.status(401).body(Map.of("message", "Unknown user"));
    }
    return ResponseEntity.ok(authService.toUserResponse(user));
  }

  @PostMapping("/wallet/challenge")
  public AuthDtos.WalletChallengeResponse walletChallenge() {
    String userId = currentUserIdOrThrow();
    return walletLinkService.challenge(userId);
  }

  @PostMapping("/wallet/confirm")
  public ResponseEntity<?> walletConfirm(@Valid @RequestBody AuthDtos.WalletConfirmRequest req) {
    String userId = currentUserIdOrThrow();
    String addr = walletLinkService.confirmAndStore(userId, req.signature());
    var user = repo.findById(userId).orElse(null);
    return ResponseEntity.ok(Map.of(
        "walletAddress", addr,
        "user", user == null ? null : authService.toUserResponse(user)
    ));
  }

  @PostMapping("/wallet/login/challenge")
  public AuthDtos.WalletChallengeResponse walletLoginChallenge(@Valid @RequestBody AuthDtos.WalletLoginChallengeRequest req) {
    return walletAuthService.loginChallenge(req.walletAddress());
  }

  @PostMapping("/wallet/login")
  public AuthDtos.AuthResponse walletLogin(@Valid @RequestBody AuthDtos.WalletLoginRequest req) {
    return walletAuthService.loginWithWallet(req);
  }

  private static String currentUserIdOrThrow() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      throw new IllegalArgumentException("Unauthorized");
    }
    String userId = String.valueOf(auth.getPrincipal());
    if (userId.isBlank() || "anonymousUser".equalsIgnoreCase(userId)) {
      throw new IllegalArgumentException("Unauthorized");
    }
    return userId;
  }
}

