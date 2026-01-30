package com.fancapital.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record SecurityJwtProperties(String secret, long ttlSeconds) {}

