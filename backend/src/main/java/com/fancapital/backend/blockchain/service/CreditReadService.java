package com.fancapital.backend.blockchain.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;

/**
 * Read service for CreditModelA (Avance sur Titres). Reads active loans from chain.
 */
@Service
public class CreditReadService {

  private static final BigInteger ACTIVE = BigInteger.valueOf(1); // Status.Active
  private static final int STATUS_REQUESTED = 0;

  private final EvmCallService evmCall;
  private final DeploymentRegistry registry;

  public CreditReadService(EvmCallService evmCall, DeploymentRegistry registry) {
    this.evmCall = evmCall;
    this.registry = registry;
  }

  /**
   * Get next loan ID (upper bound for iteration).
   */
  public BigInteger getNextLoanId() {
    String addr = registry.getCreditModelAAddress();
    if (addr == null || addr.isBlank()) return BigInteger.ZERO;
    try {
      Function fn = new Function("nextLoanId", List.of(), List.of(new TypeReference<Uint256>() {}));
      List<Type> out = evmCall.ethCall(addr, fn);
      if (out == null || out.isEmpty()) return BigInteger.ZERO;
      return EvmCallService.uint(out.get(0));
    } catch (Exception e) {
      return BigInteger.ZERO;
    }
  }

  /**
   * Read a single loan by ID. Returns null if not found or invalid.
   */
  public LoanInfo getLoan(BigInteger loanId) {
    String addr = registry.getCreditModelAAddress();
    if (addr == null || addr.isBlank()) return null;
    Function fn = new Function("loans",
        List.of(new Uint256(loanId)),
        List.of(
            new TypeReference<Address>() {},
            new TypeReference<Address>() {},
            new TypeReference<Uint256>() {},
            new TypeReference<Uint256>() {},
            new TypeReference<Uint256>() {},
            new TypeReference<Uint64>() {},
            new TypeReference<Uint64>() {},
            new TypeReference<Uint8>() {}
        ));
    List<Type> out = evmCall.ethCall(addr, fn);
    if (out == null || out.size() < 8) return null;
    String user = (String) out.get(0).getValue();
    String token = (String) out.get(1).getValue();
    BigInteger collateralAmount = EvmCallService.uint(out.get(2));
    BigInteger vniAtStart = EvmCallService.uint(out.get(3));
    BigInteger principalTnd = EvmCallService.uint(out.get(4));
    BigInteger startAt = EvmCallService.uint(out.get(5));
    BigInteger durationDays = EvmCallService.uint(out.get(6));
    int status = ((BigInteger) out.get(7).getValue()).intValue();
    return new LoanInfo(loanId, user, token, collateralAmount, vniAtStart, principalTnd,
        startAt.longValue(), durationDays.longValue(), status);
  }

  /**
   * List all requested loans (status = Requested), awaiting operator activation.
   */
  public List<LoanInfo> listRequestedLoans() {
    List<LoanInfo> result = new ArrayList<>();
    BigInteger next = getNextLoanId();
    if (next == null || next.compareTo(BigInteger.ONE) < 0) return result;
    for (BigInteger i = BigInteger.ONE; i.compareTo(next) < 0; i = i.add(BigInteger.ONE)) {
      LoanInfo loan = getLoan(i);
      if (loan != null && loan.status() == STATUS_REQUESTED) {
        result.add(loan);
      }
    }
    return result;
  }

  /**
   * List all active loans (status = Active).
   */
  public List<LoanInfo> listActiveLoans() {
    List<LoanInfo> result = new ArrayList<>();
    BigInteger next = getNextLoanId();
    if (next == null || next.compareTo(BigInteger.ONE) < 0) return result;
    for (BigInteger i = BigInteger.ONE; i.compareTo(next) < 0; i = i.add(BigInteger.ONE)) {
      LoanInfo loan = getLoan(i);
      if (loan != null && loan.status() == ACTIVE.intValue()) {
        result.add(loan);
      }
    }
    return result;
  }

  public record LoanInfo(
      BigInteger loanId,
      String user,
      String token,
      BigInteger collateralAmount,
      BigInteger vniAtStart,
      BigInteger principalTnd,
      long startAt,
      long durationDays,
      int status
  ) {}
}
