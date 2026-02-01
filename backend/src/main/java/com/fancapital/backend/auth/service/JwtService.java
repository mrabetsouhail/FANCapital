package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.config.SecurityJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private static final Logger log = LoggerFactory.getLogger(JwtService.class);
  private final SecurityJwtProperties props;
  private final SecretKey key;

  public JwtService(SecurityJwtProperties props) {
    this.props = props;
    String secret = props.secret();
    if (secret == null || secret.isBlank() || secret.length() < 48) {
      // Dev-friendly fallback: generate ephemeral secret so the app can start.
      // Tokens will become invalid after restart. For production, ALWAYS set JWT_SECRET.
      byte[] b = new byte[64]; // 512-bit
      new SecureRandom().nextBytes(b);
      this.key = Keys.hmacShaKeyFor(b);
      log.warn("JWT_SECRET is missing/too short. Using an ephemeral in-memory JWT key (DEV ONLY). Set env JWT_SECRET (>=48 chars) for stable sessions.");
    } else {
      this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
  }

  public String mint(String userId, String email, String type) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(Math.max(60, props.ttlSeconds()));

    return Jwts.builder()
        .subject(userId)
        .claim("email", email)
        .claim("type", type)
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(key)
        .compact();
  }

  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}

