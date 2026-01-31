package com.fancapital.backend.blockchain.model;

import java.util.List;

public class TxHistoryDtos {
  public record TxHistoryResponse(
      String user,
      List<TxRow> items
  ) {}

  public record TxRow(
      String id,          // txHash:logIndex
      String kind,        // BUY | SELL | DEPOSIT | WITHDRAW
      int fundId,         // -1 for cash
      String fundName,
      String fundSymbol,
      String tokenAddress, // token (Atlas/Didon) or cash token
      String amountTnd1e8, // for BUY/DEPOSIT: in; for SELL/WITHDRAW: out
      String amountToken1e8, // for BUY: minted; for SELL: sold; else 0
      String priceClient1e8, // for BUY/SELL else 0
      long blockNumber,
      long timestampSec,
      String txHash
  ) {}
}

