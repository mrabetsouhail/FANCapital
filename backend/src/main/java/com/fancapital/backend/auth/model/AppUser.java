package com.fancapital.backend.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

  @Id
  @Column(nullable = false, updatable = false, length = 36)
  private String id = UUID.randomUUID().toString();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserType type;

  @Column(nullable = false, unique = true, length = 200)
  private String email;

  @Column(nullable = false, length = 200)
  private String passwordHash;

  // ---- Particulier ----
  @Column(length = 120)
  private String nom;

  @Column(length = 120)
  private String prenom;

  // Add SQL default to support schema migration on existing DB rows.
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean resident = true;

  @Column(length = 20)
  private String cin;

  @Column(length = 40)
  private String passportNumber;

  @Column(length = 40)
  private String telephone;

  // ---- Entreprise ----
  @Column(length = 200)
  private String denominationSociale;

  @Column(length = 40)
  private String matriculeFiscal;

  @Column(length = 120)
  private String nomGerant;

  @Column(length = 120)
  private String prenomGerant;

  @Column(length = 200)
  private String emailProfessionnel;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  // ---- KYC ----
  // Add SQL default to support schema migration on existing DB rows.
  @Column(nullable = false, columnDefinition = "integer default 0")
  private int kycLevel = 0; // 0=none, 1=green, 2=white

  private Instant kycValidatedAt;

  // ---- Wallet (non-custodial link) ----
  @Column(length = 42)
  private String walletAddress;

  @Column(length = 80)
  private String walletLinkNonce;

  private Instant walletLinkedAt;

  // ---- Wallet login (shortcut) ----
  @Column(length = 80)
  private String walletAuthNonce;

  private Instant walletAuthNonceAt;

  // ---- WaaS (custodial) wallet material (encrypted) ----
  @Column(length = 500)
  private String walletPrivateKeyEnc;

  /** Mod AST 5: Abonnement Premium - requis pour AST et tiers Platinum/Diamond */
  @Column(nullable = false, columnDefinition = "boolean default false")
  private boolean premium = false;

  public String getId() {
    return id;
  }

  public UserType getType() {
    return type;
  }

  public void setType(UserType type) {
    this.type = type;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getNom() {
    return nom;
  }

  public void setNom(String nom) {
    this.nom = nom;
  }

  public String getPrenom() {
    return prenom;
  }

  public void setPrenom(String prenom) {
    this.prenom = prenom;
  }

  public String getCin() {
    return cin;
  }

  public void setCin(String cin) {
    this.cin = cin;
  }

  public boolean isResident() {
    return resident;
  }

  public void setResident(boolean resident) {
    this.resident = resident;
  }

  public String getPassportNumber() {
    return passportNumber;
  }

  public void setPassportNumber(String passportNumber) {
    this.passportNumber = passportNumber;
  }

  public String getTelephone() {
    return telephone;
  }

  public void setTelephone(String telephone) {
    this.telephone = telephone;
  }

  public String getDenominationSociale() {
    return denominationSociale;
  }

  public void setDenominationSociale(String denominationSociale) {
    this.denominationSociale = denominationSociale;
  }

  public String getMatriculeFiscal() {
    return matriculeFiscal;
  }

  public void setMatriculeFiscal(String matriculeFiscal) {
    this.matriculeFiscal = matriculeFiscal;
  }

  public String getNomGerant() {
    return nomGerant;
  }

  public void setNomGerant(String nomGerant) {
    this.nomGerant = nomGerant;
  }

  public String getPrenomGerant() {
    return prenomGerant;
  }

  public void setPrenomGerant(String prenomGerant) {
    this.prenomGerant = prenomGerant;
  }

  public String getEmailProfessionnel() {
    return emailProfessionnel;
  }

  public void setEmailProfessionnel(String emailProfessionnel) {
    this.emailProfessionnel = emailProfessionnel;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public int getKycLevel() {
    return kycLevel;
  }

  public void setKycLevel(int kycLevel) {
    this.kycLevel = kycLevel;
  }

  public Instant getKycValidatedAt() {
    return kycValidatedAt;
  }

  public void setKycValidatedAt(Instant kycValidatedAt) {
    this.kycValidatedAt = kycValidatedAt;
  }

  public String getWalletAddress() {
    return walletAddress;
  }

  public void setWalletAddress(String walletAddress) {
    this.walletAddress = walletAddress;
  }

  public String getWalletLinkNonce() {
    return walletLinkNonce;
  }

  public void setWalletLinkNonce(String walletLinkNonce) {
    this.walletLinkNonce = walletLinkNonce;
  }

  public Instant getWalletLinkedAt() {
    return walletLinkedAt;
  }

  public void setWalletLinkedAt(Instant walletLinkedAt) {
    this.walletLinkedAt = walletLinkedAt;
  }

  public String getWalletAuthNonce() {
    return walletAuthNonce;
  }

  public void setWalletAuthNonce(String walletAuthNonce) {
    this.walletAuthNonce = walletAuthNonce;
  }

  public Instant getWalletAuthNonceAt() {
    return walletAuthNonceAt;
  }

  public void setWalletAuthNonceAt(Instant walletAuthNonceAt) {
    this.walletAuthNonceAt = walletAuthNonceAt;
  }

  public String getWalletPrivateKeyEnc() {
    return walletPrivateKeyEnc;
  }

  public void setWalletPrivateKeyEnc(String walletPrivateKeyEnc) {
    this.walletPrivateKeyEnc = walletPrivateKeyEnc;
  }

  public boolean isPremium() {
    return premium;
  }

  public void setPremium(boolean premium) {
    this.premium = premium;
  }
}

