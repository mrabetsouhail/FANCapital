package com.fancapital.backend.blockchain.model;

public record FundDto(
    int id,
    String name,
    String symbol,
    String token,
    String pool,
    String oracle,
    String createdAt
) {}

