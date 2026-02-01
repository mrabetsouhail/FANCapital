package com.fancapital.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

  private static final String NAME_RX = "^[\\p{L}][\\p{L} '\\-]{1,118}$";
  private static final String PHONE_RX = "^[0-9+][0-9 ]{6,20}$";
  private static final String PASSPORT_RX = "^[A-Za-z0-9]{6,20}$";
  private static final String ETH_ADDRESS_RX = "^0x[a-fA-F0-9]{40}$";
  private static final String SIG_RX = "^0x[a-fA-F0-9]{130}$"; // 65 bytes

  public record LoginRequest(
      @Email @NotBlank @Size(max = 200) String email,
      @NotBlank @Size(min = 8, max = 200) String password
  ) {}

  public record AuthResponse(
      String token,
      UserResponse user
  ) {}

  public record UserResponse(
      String id,
      String type,
      String email,
      String walletAddress,
      Integer kycLevel,
      Boolean isBackofficeAdmin,
      String backofficeRole,
      String nom,
      String prenom,
      Boolean resident,
      String cin,
      String passportNumber,
      String telephone,
      String denominationSociale,
      String matriculeFiscal,
      String nomGerant,
      String prenomGerant,
      String emailProfessionnel
  ) {}

  public record WalletChallengeResponse(
      String message
  ) {}

  public record WalletConfirmRequest(
      @NotBlank @Pattern(regexp = SIG_RX) String signature
  ) {}

  // Wallet login (shortcut): unauthenticated challenge + signature verification
  public record WalletLoginChallengeRequest(
      @NotBlank @Pattern(regexp = ETH_ADDRESS_RX) String walletAddress
  ) {}

  public record WalletLoginRequest(
      @NotBlank @Pattern(regexp = ETH_ADDRESS_RX) String walletAddress,
      @NotBlank @Pattern(regexp = SIG_RX) String signature
  ) {}

  // Signup Particulier: keep same attribute names as in SigninPage.particulierForm
  public record RegisterParticulierRequest(
      @NotBlank @Pattern(regexp = NAME_RX) String nom,
      @NotBlank @Pattern(regexp = NAME_RX) String prenom,
      @Email @NotBlank @Size(max = 200) String email,
      @NotBlank @Size(min = 8, max = 200) @JsonAlias({"motDePasse"}) String password,
      @NotBlank @JsonAlias({"confirmation"}) String confirmPassword,
      @JsonAlias({"isResident"}) Boolean resident,
      @JsonAlias({"numeroCIN"}) String cin,
      // If frontend sends empty string when resident=true, allow "" here.
      @JsonAlias({"passport"}) @Pattern(regexp = "^$|" + PASSPORT_RX) String passportNumber,
      @NotBlank @Pattern(regexp = PHONE_RX) String telephone
  ) {}

  // Signup Entreprise: keep same attribute names as in SigninPage.entrepriseForm
  public record RegisterEntrepriseRequest(
      @NotBlank @Size(min = 2, max = 200) String denominationSociale,
      @NotBlank @Pattern(regexp = "^[0-9]{8}$") String matriculeFiscal,
      @NotBlank @Pattern(regexp = NAME_RX) String nomGerant,
      @NotBlank @Pattern(regexp = NAME_RX) String prenomGerant,
      @Email @NotBlank @Size(max = 200) String emailProfessionnel,
      @NotBlank @Size(min = 8, max = 200) String password,
      @NotBlank String confirmPassword,
      @NotBlank @Pattern(regexp = PHONE_RX) String telephone
  ) {}
}

