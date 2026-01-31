package com.fancapital.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(
    String encKey
) {}

