package com.fancapital.backend.backoffice.controller;

import com.fancapital.backend.auth.service.AuthService;
import com.fancapital.backend.auth.service.KycService;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backoffice/kyc")
public class KycBackofficeController {
  private final BackofficeAuthzService authz;
  private final KycService kycService;
  private final AuthService authService;
  private final AppUserRepository repo;

  public KycBackofficeController(BackofficeAuthzService authz, KycService kycService, AuthService authService, AppUserRepository repo) {
    this.authz = authz;
    this.kycService = kycService;
    this.authService = authService;
    this.repo = repo;
  }

  public record SetLevelRequest(
      @NotBlank @Size(min = 10, max = 60) String userId,
      @Min(0) @Max(2) int level
  ) {}

  public record KycUserRow(
      String id,
      String email,
      String type,
      int kycLevel,
      Boolean resident,
      String walletAddress,
      String createdAt,
      String kycValidatedAt
  ) {}

  @GetMapping("/users")
  public List<KycUserRow> users(@RequestParam(name = "q", required = false) String q) {
    authz.requireAdmin();
    String needle = q == null ? "" : q.trim().toLowerCase();
    var all = repo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    return all.stream()
        .filter(u -> {
          if (needle.isBlank()) return true;
          String email = u.getEmail() == null ? "" : u.getEmail().toLowerCase();
          String id = u.getId() == null ? "" : u.getId().toLowerCase();
          String nom = u.getNom() == null ? "" : u.getNom().toLowerCase();
          String prenom = u.getPrenom() == null ? "" : u.getPrenom().toLowerCase();
          String denom = u.getDenominationSociale() == null ? "" : u.getDenominationSociale().toLowerCase();
          return email.contains(needle) || id.contains(needle) || nom.contains(needle) || prenom.contains(needle) || denom.contains(needle);
        })
        .map(u -> new KycUserRow(
            u.getId(),
            u.getEmail(),
            u.getType() != null ? u.getType().name() : null,
            u.getKycLevel(),
            u.isResident(),
            u.getWalletAddress(),
            u.getCreatedAt() != null ? u.getCreatedAt().toString() : null,
            u.getKycValidatedAt() != null ? u.getKycValidatedAt().toString() : null
        ))
        .toList();
  }

  @PostMapping("/set-level")
  public ResponseEntity<?> setLevel(@RequestBody SetLevelRequest req) {
    authz.requireAdmin();
    var u = kycService.setKycLevel(req.userId(), req.level());
    return ResponseEntity.ok(authService.toUserResponse(u));
  }
}

