package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.dto.AuthDtos;
import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.model.UserType;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.service.BackofficeAuthzService;
import com.fancapital.backend.security.InputSanitizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final AppUserRepository repo;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final BackofficeAuthzService backofficeAuthz;

  public AuthService(
      AppUserRepository repo,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      BackofficeAuthzService backofficeAuthz
  ) {
    this.repo = repo;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.backofficeAuthz = backofficeAuthz;
  }

  @Transactional
  public AuthDtos.AuthResponse registerParticulier(AuthDtos.RegisterParticulierRequest req) {
    if (!req.password().equals(req.confirmPassword())) {
      throw new IllegalArgumentException("Les mots de passe ne correspondent pas");
    }
    String email = InputSanitizer.lowerEmail(req.email());
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email invalide");
    }
    if (repo.existsByEmailIgnoreCase(email)) {
      throw new IllegalArgumentException("Email déjà utilisé");
    }

    boolean resident = req.resident() == null ? true : req.resident();
    String cin = InputSanitizer.clean(req.cin());
    String passport = InputSanitizer.clean(req.passportNumber());
    cin = cin == null ? "" : cin;
    passport = passport == null ? "" : passport;
    if (resident) {
      if (!cin.matches("^[0-9]{8}$")) {
        throw new IllegalArgumentException("CIN invalide (8 chiffres)");
      }
    } else {
      if (!passport.matches("^[A-Za-z0-9]{6,20}$")) {
        throw new IllegalArgumentException("Numéro de passeport invalide");
      }
    }

    AppUser u = new AppUser();
    u.setType(UserType.PARTICULIER);
    u.setEmail(email);
    u.setPasswordHash(passwordEncoder.encode(req.password()));
    u.setNom(InputSanitizer.clean(req.nom()));
    u.setPrenom(InputSanitizer.clean(req.prenom()));
    u.setResident(resident);
    u.setCin(resident ? cin : null);
    u.setPassportNumber(resident ? null : passport);
    u.setTelephone(InputSanitizer.clean(req.telephone()));
    repo.save(u);
    // Wallet créé automatiquement après validation KYC1 par l'admin (voir KycService.setKycLevel)
    // Pas de provisionnement à l'inscription.

    String token = jwtService.mint(u.getId(), u.getEmail(), u.getType().name());
    return new AuthDtos.AuthResponse(token, toUserResponse(u));
  }

  @Transactional
  public AuthDtos.AuthResponse registerEntreprise(AuthDtos.RegisterEntrepriseRequest req) {
    if (!req.password().equals(req.confirmPassword())) {
      throw new IllegalArgumentException("Les mots de passe ne correspondent pas");
    }
    String email = InputSanitizer.lowerEmail(req.emailProfessionnel());
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email invalide");
    }
    if (repo.existsByEmailIgnoreCase(email)) {
      throw new IllegalArgumentException("Email déjà utilisé");
    }

    AppUser u = new AppUser();
    u.setType(UserType.ENTREPRISE);
    u.setEmail(email);
    u.setEmailProfessionnel(email);
    u.setPasswordHash(passwordEncoder.encode(req.password()));
    u.setDenominationSociale(InputSanitizer.clean(req.denominationSociale()));
    u.setMatriculeFiscal(InputSanitizer.clean(req.matriculeFiscal()));
    u.setNomGerant(InputSanitizer.clean(req.nomGerant()));
    u.setPrenomGerant(InputSanitizer.clean(req.prenomGerant()));
    u.setTelephone(InputSanitizer.clean(req.telephone()));
    repo.save(u);
    // Wallet créé automatiquement après validation KYC1 par l'admin (voir KycService.setKycLevel)
    // Pas de provisionnement à l'inscription.

    String token = jwtService.mint(u.getId(), u.getEmail(), u.getType().name());
    return new AuthDtos.AuthResponse(token, toUserResponse(u));
  }

  @Transactional(readOnly = true)
  public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
    String email = InputSanitizer.lowerEmail(req.email());
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Identifiants invalides");
    }
    AppUser u = repo.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new IllegalArgumentException("Identifiants invalides"));
    if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
      throw new IllegalArgumentException("Identifiants invalides");
    }
    String token = jwtService.mint(u.getId(), u.getEmail(), u.getType().name());
    return new AuthDtos.AuthResponse(token, toUserResponse(u));
  }

  public AuthDtos.UserResponse toUserResponse(AppUser u) {
    String email = u.getEmail();
    var role = backofficeAuthz.roleForEmail(email);
    return new AuthDtos.UserResponse(
        u.getId(),
        u.getType().name(),
        email,
        u.getWalletAddress(),
        u.getKycLevel(),
        backofficeAuthz.isAdminEmail(email),
        role.name(),
        u.getNom(),
        u.getPrenom(),
        u.isResident(),
        u.getCin(),
        u.getPassportNumber(),
        u.getTelephone(),
        u.getDenominationSociale(),
        u.getMatriculeFiscal(),
        u.getNomGerant(),
        u.getPrenomGerant(),
        u.getEmailProfessionnel()
    );
  }
}

