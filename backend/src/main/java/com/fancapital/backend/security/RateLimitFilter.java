package com.fancapital.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple in-memory fixed-window rate limiter.
 * OWASP: apply rate limiting on all API endpoints.
 *
 * Note: In production, prefer a distributed limiter (Redis) or gateway-level limiting.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final class Counter {
    volatile long windowStartSec;
    volatile int count;
  }

  private record Rule(int limit, int windowSeconds) {}

  private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;

  public RateLimitFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    String path = request.getRequestURI();
    return path == null || !path.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    final String path = request.getRequestURI();
    final String ip = clientIp(request);
    final Rule rule = ruleFor(path);
    final String key = ip + "|" + bucketName(path);

    long nowSec = Instant.now().getEpochSecond();
    Counter c = counters.computeIfAbsent(key, k -> {
      Counter x = new Counter();
      x.windowStartSec = nowSec;
      x.count = 0;
      return x;
    });

    int remaining;
    long retryAfterSec = 0;
    boolean allowed;
    synchronized (c) {
      if (nowSec - c.windowStartSec >= rule.windowSeconds) {
        c.windowStartSec = nowSec;
        c.count = 0;
      }
      if (c.count < rule.limit) {
        c.count++;
        allowed = true;
      } else {
        allowed = false;
        retryAfterSec = Math.max(1, (c.windowStartSec + rule.windowSeconds) - nowSec);
      }
      remaining = Math.max(0, rule.limit - c.count);
    }

    response.setHeader("X-RateLimit-Limit", String.valueOf(rule.limit));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
    if (!allowed) {
      response.setStatus(429);
      response.setHeader("Retry-After", String.valueOf(retryAfterSec));
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write(objectMapper.writeValueAsString(Map.of(
          "message", "Too many requests. Please retry later.",
          "retryAfterSeconds", retryAfterSec
      )));
      return;
    }

    filterChain.doFilter(request, response);
  }

  private static Rule ruleFor(String path) {
    // stricter on auth endpoints
    if (path.startsWith("/api/auth/login")) return new Rule(10, 60);      // 10/min
    if (path.startsWith("/api/auth/register")) return new Rule(5, 60);    // 5/min
    if (path.startsWith("/api/auth/me")) return new Rule(60, 60);         // 60/min
    // default for all other API endpoints
    return new Rule(120, 60); // 120/min
  }

  private static String bucketName(String path) {
    if (path.startsWith("/api/auth/login")) return "auth-login";
    if (path.startsWith("/api/auth/register")) return "auth-register";
    if (path.startsWith("/api/auth/me")) return "auth-me";
    if (path.startsWith("/api/blockchain")) return "blockchain";
    return "api";
  }

  private static String clientIp(HttpServletRequest request) {
    // In production behind a proxy, ensure proper forwarded headers handling.
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      // first IP in list
      int comma = xff.indexOf(',');
      return (comma >= 0 ? xff.substring(0, comma) : xff).trim();
    }
    return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
  }
}

