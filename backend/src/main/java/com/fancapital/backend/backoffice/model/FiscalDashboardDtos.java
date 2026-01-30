package com.fancapital.backend.backoffice.model;

import java.util.List;

public class FiscalDashboardDtos {

  public record FundTaxSummary(
      int fundId,
      String name,
      String symbol,
      String token,
      String rasCollectedTnd, // 1e8 string
      long rasEvents
  ) {}

  public record FiscalDashboardResponse(
      String deploymentsPath,
      String taxVault,
      String cashToken,
      String fiscAddress,
      String taxVaultBalanceTnd, // 1e8 string (cashToken balanceOf(taxVault))
      List<FundTaxSummary> byFund
  ) {}

  public record WithdrawRequest(String amount) {} // uint as string (1e8)

  public record TxResponse(String status, String txHash, String message) {}
}

