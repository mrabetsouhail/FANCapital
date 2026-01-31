package com.fancapital.backend.blockchain.model;

import java.util.List;

public class PortfolioDtos {
  /**
   * All monetary / token quantities are scaled to 1e8, to match on-chain decimals (CPEF decimals=8, TND decimals=8).
   */
  public record PortfolioPosition(
      int fundId,
      String name,
      String symbol,
      String token,
      String pool,
      String oracle,
      String balanceTokens,      // token units (1e8)
      String vni,                // TND per token (1e8)
      String prm,                // TND per token (1e8)
      String positionValueTnd,   // TND (1e8)
      String unrealizedGainTnd   // TND (1e8)
  ) {}

  public record PortfolioResponse(
      String user,
      List<PortfolioPosition> positions,
      String cashBalanceTnd,     // TND (1e8) on-chain CashTokenTND balance
      String totalValueTnd,      // TND (1e8)
      String totalUnrealizedGainTnd // TND (1e8)
  ) {}
}

