package com.fancapital.backend.config;

import java.util.List;
import com.fancapital.backend.auth.config.SecurityJwtProperties;
import com.fancapital.backend.backoffice.config.BackofficeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@EnableConfigurationProperties({AppProperties.class, BlockchainProperties.class, WalletProperties.class, SecurityJwtProperties.class, BackofficeProperties.class})
public class AppConfig {

  @Bean
  public Web3j web3j(BlockchainProperties props) {
    return Web3j.build(new HttpService(props.rpcUrl()));
  }

  @Bean
  public CorsFilter corsFilter(AppProperties props) {
    CorsConfiguration cfg = new CorsConfiguration();
    List<String> origins = props.cors() != null ? props.cors().allowedOrigins() : List.of();
    // Use patterns to support dev servers on dynamic ports (e.g. 59546).
    // Examples: "http://127.0.0.1:*", "http://localhost:*"
    cfg.setAllowedOriginPatterns(origins);
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return new CorsFilter(source);
  }
}

