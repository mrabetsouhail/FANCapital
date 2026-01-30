package com.fancapital.backend.auth.service;

import com.fancapital.backend.auth.config.SecurityJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecurityJwtProperties props;
  private final SecretKey key;

  public JwtService(SecurityJwtProperties props) {
    this.props = props;
    this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
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

