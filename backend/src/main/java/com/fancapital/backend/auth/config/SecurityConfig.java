package com.fancapital.backend.auth.config;

import com.fancapital.backend.auth.service.JwtService;
import com.fancapital.backend.security.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableConfigurationProperties(SecurityJwtProperties.class)
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** Chaîne prioritaire : /api/blockchain/** est entièrement public (GET + POST : portfolio, quote-buy, quote-sell, buy, sell, etc.). */
  @Bean
  @Order(0)
  public SecurityFilterChain blockchainSecurityFilterChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/api/blockchain/**")
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }

  @Bean
  @Order(1)
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .headers(h -> h.frameOptions(fo -> fo.sameOrigin()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            // Blockchain read/write: public (no auth required for portfolio, funds, pool, etc.)
            .requestMatchers("/api/blockchain/**").permitAll()
            .requestMatchers(
                "/api/auth/login",
                "/api/auth/register/**",
                "/api/auth/wallet/login/**"
            ).permitAll()
            .requestMatchers("/api/auth/**").authenticated()
            // Dev-only: H2 console
            .requestMatchers("/h2-console", "/h2-console/**").permitAll()
            .anyRequest().authenticated()
        )
        // Rate limit all /api/** requests
        .addFilterBefore(new RateLimitFilter(objectMapper), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  private static class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    private JwtAuthFilter(JwtService jwtService) {
      this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
        throws ServletException, IOException {
      String auth = request.getHeader("Authorization");
      if (auth != null && auth.startsWith("Bearer ")) {
        String token = auth.substring("Bearer ".length()).trim();
        try {
          Claims claims = jwtService.parse(token);
          String userId = claims.getSubject();
          String type = String.valueOf(claims.get("type"));
          var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("TYPE_" + type));
          var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
          org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ignored) {
          // invalid token => anonymous
        }
      }
      filterChain.doFilter(request, response);
    }
  }
}

