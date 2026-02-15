package com.fancapital.backend.backoffice.audit.service;

import com.fancapital.backend.auth.model.AppUser;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.audit.model.AuditDtos;
import com.fancapital.backend.blockchain.model.PortfolioDtos.PortfolioPosition;
import com.fancapital.backend.blockchain.service.BlockchainReadService;
import java.math.BigInteger;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AuditRegistryService {
  private final AppUserRepository users;
  private final BlockchainReadService chain;

  public AuditRegistryService(AppUserRepository users, BlockchainReadService chain) {
    this.users = users;
    this.chain = chain;
  }

  public AuditDtos.AuditRegistryResponse registry(String q, BigInteger atBlockNumber) {
    String needle = q == null ? "" : q.trim().toLowerCase();
    long nowSec = System.currentTimeMillis() / 1000L;

    var all = users.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    List<AuditDtos.AuditRegistryRow> rows = all.stream()
        .filter(u -> {
          if (needle.isBlank()) return true;
          return contains(u.getEmail(), needle)
              || contains(u.getId(), needle)
              || contains(u.getNom(), needle)
              || contains(u.getPrenom(), needle)
              || contains(u.getDenominationSociale(), needle)
              || contains(u.getCin(), needle)
              || contains(u.getPassportNumber(), needle)
              || contains(u.getMatriculeFiscal(), needle)
              || contains(u.getWalletAddress(), needle);
        })
        .map(u -> toRow(u, atBlockNumber))
        .toList();

    return new AuditDtos.AuditRegistryResponse(nowSec, atBlockNumber == null ? null : atBlockNumber.longValue(), rows);
  }

  private AuditDtos.AuditRegistryRow toRow(AppUser u, BigInteger atBlockNumber) {
    String wallet = u.getWalletAddress();
    String atlas = "0";
    String didon = "0";
    String atlasLocked = "0";
    String didonLocked = "0";

    if (wallet != null && wallet.startsWith("0x") && wallet.length() == 42) {
      var portfolio = (atBlockNumber == null)
          ? chain.portfolio(wallet)
          : chain.portfolioAtBlock(wallet, atBlockNumber);

      PortfolioPosition pAtlas = pickFund(portfolio.positions(), "atlas", 0);
      PortfolioPosition pDidon = pickFund(portfolio.positions(), "didon", 1);
      atlas = pAtlas != null ? pAtlas.balanceTokens() : "0";
      didon = pDidon != null ? pDidon.balanceTokens() : "0";
      atlasLocked = pAtlas != null ? pAtlas.lockedTokens1e8() : "0";
      didonLocked = pDidon != null ? pDidon.lockedTokens1e8() : "0";
    }

    String idValue = pickFiscalOrCinOrPassport(u);
    String displayName = displayName(u);

    return new AuditDtos.AuditRegistryRow(
        u.getId(),
        u.getType() != null ? u.getType().name() : null,
        u.getEmail(),
        u.isResident(),
        idValue,
        displayName,
        wallet,
        atlas,
        didon,
        atlasLocked,
        didonLocked
    );
  }

  private static String pickFiscalOrCinOrPassport(AppUser u) {
    if (u.getType() != null && "ENTREPRISE".equalsIgnoreCase(u.getType().name())) {
      return u.getMatriculeFiscal();
    }
    // Particulier
    if (u.isResident()) return u.getCin();
    return u.getPassportNumber();
  }

  private static String displayName(AppUser u) {
    if (u.getType() != null && "ENTREPRISE".equalsIgnoreCase(u.getType().name())) {
      return u.getDenominationSociale();
    }
    String fn = u.getPrenom() == null ? "" : u.getPrenom();
    String ln = u.getNom() == null ? "" : u.getNom();
    return (fn + " " + ln).trim();
  }

  private static boolean contains(String hay, String needle) {
    if (hay == null) return false;
    return hay.toLowerCase().contains(needle);
  }

  private static PortfolioPosition pickFund(List<PortfolioPosition> positions, String nameHint, int idHint) {
    if (positions == null || positions.isEmpty()) return null;
    PortfolioPosition byName = positions.stream()
        .filter(x -> (x.name() != null && x.name().toLowerCase().contains(nameHint))
            || (x.symbol() != null && x.symbol().toLowerCase().contains(nameHint)))
        .findFirst()
        .orElse(null);
    if (byName != null) return byName;
    return positions.stream().filter(x -> x.fundId() == idHint).findFirst().orElse(null);
  }
}

