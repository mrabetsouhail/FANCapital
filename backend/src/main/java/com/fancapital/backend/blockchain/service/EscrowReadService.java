package com.fancapital.backend.blockchain.service;

import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

/**
 * Read service for EscrowRegistry. Reads lock info per loan.
 */
@Service
public class EscrowReadService {

  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L);
  private static final BigInteger LTV_BPS = BigInteger.valueOf(7_000);
  private static final BigInteger BPS = BigInteger.valueOf(10_000);

  private final EvmCallService evmCall;
  private final DeploymentRegistry registry;

  public EscrowReadService(EvmCallService evmCall, DeploymentRegistry registry) {
    this.evmCall = evmCall;
    this.registry = registry;
  }

  /**
   * Lock info for a loan from EscrowRegistry.locks(loanId).
   */
  public LockInfo getLock(BigInteger loanId) {
    String addr = registry.getEscrowRegistryAddress();
    if (addr == null || addr.isBlank()) return null;
    try {
      Function fn = new Function("locks",
          List.of(new Uint256(loanId)),
          List.of(
              new TypeReference<Address>() {},
              new TypeReference<Uint256>() {},
              new TypeReference<Bool>() {}
          ));
      List<Type> out = evmCall.ethCall(addr, fn);
      if (out == null || out.size() < 3) return null;
      String token = (String) out.get(0).getValue();
      BigInteger amount = EvmCallService.uint(out.get(1));
      boolean active = (Boolean) out.get(2).getValue();
      return new LockInfo(token, amount, active);
    } catch (Exception e) {
      return null;
    }
  }

  public record LockInfo(String token, BigInteger amount, boolean active) {}
}
