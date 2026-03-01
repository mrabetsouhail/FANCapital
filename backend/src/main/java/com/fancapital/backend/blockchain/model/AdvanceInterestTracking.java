package com.fancapital.backend.blockchain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Suivi des intérêts payés par avance (AST).
 * Les intérêts sont fixes dès le début (principal × taux × durée / 365)
 * et sont prélevés en priorité depuis le premier remboursement.
 */
@Entity
@Table(name = "advance_interest_tracking")
public class AdvanceInterestTracking {

  @Id
  @Column(nullable = false, updatable = false, length = 40)
  private String loanId;

  /** Principal initial à l'activation (1e8) */
  @Column(nullable = false, length = 40)
  private String originalPrincipalTnd1e8;

  /** Intérêts totaux fixés au départ (1e8) */
  @Column(nullable = false, length = 40)
  private String totalInterestTnd1e8;

  /** Intérêts déjà payés (1e8) */
  @Column(nullable = false, length = 40)
  private String interestPaidTnd1e8 = "0";

  public String getLoanId() {
    return loanId;
  }

  public void setLoanId(String loanId) {
    this.loanId = loanId;
  }

  public String getOriginalPrincipalTnd1e8() {
    return originalPrincipalTnd1e8;
  }

  public void setOriginalPrincipalTnd1e8(String originalPrincipalTnd1e8) {
    this.originalPrincipalTnd1e8 = originalPrincipalTnd1e8;
  }

  public String getTotalInterestTnd1e8() {
    return totalInterestTnd1e8;
  }

  public void setTotalInterestTnd1e8(String totalInterestTnd1e8) {
    this.totalInterestTnd1e8 = totalInterestTnd1e8;
  }

  public String getInterestPaidTnd1e8() {
    return interestPaidTnd1e8;
  }

  public void setInterestPaidTnd1e8(String interestPaidTnd1e8) {
    this.interestPaidTnd1e8 = interestPaidTnd1e8;
  }
}
