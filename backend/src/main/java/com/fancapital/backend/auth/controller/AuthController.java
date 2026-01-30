package com.fancapital.backend.auth.controller;

import com.fancapital.backend.auth.dto.AuthDtos;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;
  private final com.fancapital.backend.auth.service.JwtService jwtService;
  private final AppUserRepository repo;

  public AuthController(AuthService authService, com.fancapital.backend.auth.service.JwtService jwtService, AppUserRepository repo) {
    this.authService = authService;
    this.jwtService = jwtService;
    this.repo = repo;
  }

  @PostMapping("/register/particulier")
  public AuthDtos.AuthResponse registerParticulier(@Valid @RequestBody AuthDtos.RegisterParticulierRequest req) {
    return authService.registerParticulier(req);
  }

  @PostMapping("/register/entreprise")
  public AuthDtos.AuthResponse registerEntreprise(@Valid @RequestBody AuthDtos.RegisterEntrepriseRequest req) {
    return authService.registerEntreprise(req);
  }

  @PostMapping("/login")
  public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
    return authService.login(req);
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return ResponseEntity.status(401).body(Map.of("message", "Missing Bearer token"));
    }
    String token = authorization.substring("Bearer ".length()).trim();
    Claims claims;
    try {
      claims = jwtService.parse(token);
    } catch (Exception e) {
      return ResponseEntity.status(401).body(Map.of("message", "Invalid token"));
    }

    String userId = claims.getSubject();
    var user = repo.findById(userId).orElse(null);
    if (user == null) {
      return ResponseEntity.status(401).body(Map.of("message", "Unknown user"));
    }
    return ResponseEntity.ok(authService.toUserResponse(user));
  }
}

