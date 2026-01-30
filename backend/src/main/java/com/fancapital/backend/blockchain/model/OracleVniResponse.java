package com.fancapital.backend.blockchain.model;

public record OracleVniResponse(
    String token,
    String vni,
    String updatedAt,
    Integer volatilityBps
) {}

