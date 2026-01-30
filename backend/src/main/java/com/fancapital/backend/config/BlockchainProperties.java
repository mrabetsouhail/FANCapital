package com.fancapital.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blockchain")
public record BlockchainProperties(String rpcUrl, String deploymentsPath) {}

