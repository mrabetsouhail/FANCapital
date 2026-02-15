package com.fancapital.backend.auth.repo;

import com.fancapital.backend.auth.model.AppUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
  Optional<AppUser> findByEmailIgnoreCase(String email);
  boolean existsByEmailIgnoreCase(String email);

  Optional<AppUser> findByWalletAddressIgnoreCase(String walletAddress);

  List<AppUser> findByPremiumTrue();

  /** Abonnements actifs dont l'expiration est entre now et before (pour relances). */
  @Query("SELECT u FROM AppUser u WHERE u.premium = true AND u.premiumExpiresAt IS NOT NULL AND u.premiumExpiresAt > :now AND u.premiumExpiresAt <= :before")
  List<AppUser> findPremiumExpiringBetween(Instant now, Instant before);
}

