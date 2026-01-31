package com.fancapital.backend.blockchain.model;

public class InvestorProfileDtos {
  public record InvestorProfileResponse(
      String user,
      // KYCRegistry
      boolean whitelisted,
      int kycLevel,
      boolean resident,
      // InvestorRegistry
      int score,
      int tier,
      int feeLevel,
      boolean subscriptionActive
  ) {}
}

