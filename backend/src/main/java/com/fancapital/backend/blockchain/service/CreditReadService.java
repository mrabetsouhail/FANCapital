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
   * Get next loan ID (upper bound for iteration) for CreditModelA.
   */
  public BigInteger getNextLoanId() {
    return getNextLoanIdFrom(registry.getCreditModelAAddress());
  }

  /**
   * Get next loan ID for CreditModelBPGP.
   */
  public BigInteger getNextLoanIdB() {
    return getNextLoanIdFrom(registry.getCreditModelBAddress());
  }

  private BigInteger getNextLoanIdFrom(String addr) {
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
   * Read a single loan by ID from CreditModelA. Returns null if not found or invalid.
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
    return parseLoanInfo(addr, loanId, fn, 0);
  }

  /**
   * Read a single loan by ID from CreditModelBPGP. Struct has extra vniAtClose field.
   */
  public LoanInfo getLoanB(BigInteger loanId) {
    String addr = registry.getCreditModelBAddress();
    if (addr == null || addr.isBlank()) return null;
    Function fn = new Function("loans",
        List.of(new Uint256(loanId)),
        List.of(
            new TypeReference<Address>() {},
            new TypeReference<Address>() {},
            new TypeReference<Uint256>() {},
            new TypeReference<Uint256>() {},
            new TypeReference<Uint256>() {},  // vniAtClose
            new TypeReference<Uint256>() {},
            new TypeReference<Uint64>() {},
            new TypeReference<Uint64>() {},
            new TypeReference<Uint8>() {}
        ));
    return parseLoanInfo(addr, loanId, fn, 1);
  }

  private LoanInfo parseLoanInfo(String addr, BigInteger loanId, Function fn, int offset) {
    List<Type> out = evmCall.ethCall(addr, fn);
    if (out == null || out.size() < 8 + offset) return null;
    String user = (String) out.get(0).getValue();
    String token = (String) out.get(1).getValue();
    BigInteger collateralAmount = EvmCallService.uint(out.get(2));
    BigInteger vniAtStart = EvmCallService.uint(out.get(3));
    BigInteger principalTnd = EvmCallService.uint(out.get(4 + offset));
    BigInteger startAt = EvmCallService.uint(out.get(5 + offset));
    BigInteger durationDays = EvmCallService.uint(out.get(6 + offset));
    int status = ((BigInteger) out.get(7 + offset).getValue()).intValue();
    return new LoanInfo(loanId, user, token, collateralAmount, vniAtStart, principalTnd,
        startAt.longValue(), durationDays.longValue(), status);
  }

  /**
   * List all requested loans (status = Requested) on CreditModelA.
   */
  public List<LoanInfo> listRequestedLoans() {
    return listLoansByStatus(registry.getCreditModelAAddress(), false, STATUS_REQUESTED);
  }

  /**
   * List all requested loans (status = Requested) on CreditModelBPGP.
   */
  public List<LoanInfo> listRequestedLoansB() {
    return listLoansByStatus(registry.getCreditModelBAddress(), true, STATUS_REQUESTED);
  }

  private List<LoanInfo> listLoansByStatus(String addr, boolean modelB, int statusFilter) {
    List<LoanInfo> result = new ArrayList<>();
    BigInteger next = modelB ? getNextLoanIdB() : getNextLoanId();
    if (next == null || next.compareTo(BigInteger.ONE) < 0) return result;
    for (BigInteger i = BigInteger.ONE; i.compareTo(next) < 0; i = i.add(BigInteger.ONE)) {
      LoanInfo loan = modelB ? getLoanB(i) : getLoan(i);
      if (loan != null && loan.status() == statusFilter) result.add(loan);
    }
    return result;
  }

  /**
   * Get active advance for a user (Model A or B). Returns model + loan for routing (repay vs close).
   */
  public ActiveAdvanceResult getActiveAdvanceForUser(String userWallet) {
    if (userWallet == null || userWallet.isBlank()) return null;
    String n = userWallet.trim().toLowerCase();
    LoanInfo fromB = listActiveLoansB().stream()
        .filter(l -> l.user() != null && l.user().equalsIgnoreCase(n))
        .findFirst()
        .orElse(null);
    if (fromB != null) return new ActiveAdvanceResult("B", fromB);
    LoanInfo fromA = listActiveLoans().stream()
        .filter(l -> l.user() != null && l.user().equalsIgnoreCase(n))
        .findFirst()
        .orElse(null);
    return fromA != null ? new ActiveAdvanceResult("A", fromA) : null;
  }

  /** @deprecated Use {@link #getActiveAdvanceForUser(String)} for model-aware handling. */
  public LoanInfo getActiveLoanForUser(String userWallet) {
    ActiveAdvanceResult r = getActiveAdvanceForUser(userWallet);
    return r != null ? r.loan() : null;
  }

  /**
   * List all active loans (status = Active) on CreditModelA.
   */
  public List<LoanInfo> listActiveLoans() {
    return listLoansByStatus(registry.getCreditModelAAddress(), false, ACTIVE.intValue());
  }

  /**
   * List all active loans (status = Active) on CreditModelBPGP.
   */
  public List<LoanInfo> listActiveLoansB() {
    return listLoansByStatus(registry.getCreditModelBAddress(), true, ACTIVE.intValue());
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

  /** Résultat avec modèle pour routing (repay = A only, close = B only). */
  public record ActiveAdvanceResult(String model, LoanInfo loan) {}
}
