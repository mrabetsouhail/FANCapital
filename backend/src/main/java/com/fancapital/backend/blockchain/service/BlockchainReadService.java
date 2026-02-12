package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.blockchain.model.OracleVniResponse;
import com.fancapital.backend.blockchain.model.PortfolioDtos.PortfolioPosition;
import com.fancapital.backend.blockchain.model.PortfolioDtos.PortfolioResponse;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteBuyRequest;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteBuyResponse;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteSellRequest;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteSellResponse;
import com.fancapital.backend.blockchain.model.FundDto;
import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.blockchain.model.InvestorProfileDtos.InvestorProfileResponse;
import com.fancapital.backend.blockchain.model.TxHistoryDtos.TxHistoryResponse;
import com.fancapital.backend.blockchain.model.TxHistoryDtos.TxRow;
import com.fancapital.backend.backoffice.service.DeploymentInfraService;
import com.fancapital.backend.config.BlockchainProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;

@Service
public class BlockchainReadService {
  private static final BigInteger PRICE_SCALE = BigInteger.valueOf(100_000_000L); // 1e8
  private static final BigInteger BPS = BigInteger.valueOf(10_000);
  private static final String ZERO_TOPIC = "0x0000000000000000000000000000000000000000000000000000000000000000";

  private static final Event BOUGHT = new Event(
      "Bought",
      List.of(
          TypeReference.create(Address.class, true), // token (indexed)
          TypeReference.create(Address.class, true), // user (indexed)
          TypeReference.create(Uint256.class),       // tndIn
          TypeReference.create(Uint256.class),       // priceClient
          TypeReference.create(Uint256.class),       // mintedAmount
          TypeReference.create(Uint256.class),       // feeBase
          TypeReference.create(Uint256.class),       // vat
          TypeReference.create(Uint256.class)        // totalFee
      )
  );

  private static final Event SOLD = new Event(
      "Sold",
      List.of(
          TypeReference.create(Address.class, true), // token (indexed)
          TypeReference.create(Address.class, true), // user (indexed)
          TypeReference.create(Uint256.class),       // tokenAmount
          TypeReference.create(Uint256.class),       // priceClient
          TypeReference.create(Uint256.class),       // tndOut
          TypeReference.create(Uint256.class),       // feeBase
          TypeReference.create(Uint256.class),       // vat
          TypeReference.create(Uint256.class)        // totalFee
      )
  );

  private static final Event TRANSFER = new Event(
      "Transfer",
      List.of(
          TypeReference.create(Address.class, true), // from
          TypeReference.create(Address.class, true), // to
          TypeReference.create(Uint256.class)        // value
      )
  );

  private static final BigInteger CREDIT_LINE_KYC1 = BigInteger.valueOf(5_000).multiply(PRICE_SCALE);
  private static final BigInteger CREDIT_LINE_KYC2 = BigInteger.valueOf(10_000).multiply(PRICE_SCALE);

  private final DeploymentRegistry registry;
  private final EvmCallService evm;
  private final BlockchainProperties props;
  private final DeploymentInfraService infra;
  private final Web3j web3j;
  private final AppUserRepository userRepo;
  private final DebtManager debtManager;

  public BlockchainReadService(DeploymentRegistry registry, EvmCallService evm, BlockchainProperties props, DeploymentInfraService infra, Web3j web3j, AppUserRepository userRepo, DebtManager debtManager) {
    this.registry = registry;
    this.evm = evm;
    this.props = props;
    this.infra = infra;
    this.web3j = web3j;
    this.userRepo = userRepo;
    this.debtManager = debtManager;
  }

  public OracleVniResponse getVni(String tokenAddress) {
    FundDto fund = registry.findByToken(tokenAddress)
        .orElseThrow(() -> new IllegalArgumentException("Unknown token: " + tokenAddress));

    BigInteger fixed = fixedVni1e8ForFund(fund);
    if (fixed != null) {
      long nowSec = System.currentTimeMillis() / 1000L;
      return new OracleVniResponse(tokenAddress, fixed.toString(), String.valueOf(nowSec), 0);
    }

    // PriceOracle.getVNIData(address) returns (uint256 vni, uint64 updatedAt)
    Function f1 = new Function(
        "getVNIData",
        List.of(new Address(tokenAddress)),
        List.of(new TypeReference<Uint256>() {}, new TypeReference<Uint64>() {})
    );
    List<Type> out1 = evm.ethCall(fund.oracle(), f1);
    if (out1 == null || out1.size() < 2) {
      return new OracleVniResponse(tokenAddress, "0", "0", 0);
    }
    BigInteger vni = EvmCallService.uint(out1.get(0));
    BigInteger updatedAt = EvmCallService.uint(out1.get(1));

    Function f2 = new Function(
        "getVolatilityBps",
        List.of(new Address(tokenAddress)),
        List.of(new TypeReference<Uint256>() {})
    );
    List<Type> out2 = evm.ethCall(fund.oracle(), f2);
    BigInteger volBps = (out2 == null || out2.isEmpty()) ? BigInteger.ZERO : EvmCallService.uint(out2.get(0));

    return new OracleVniResponse(tokenAddress, vni.toString(), updatedAt.toString(), volBps.intValue());
  }

  public QuoteBuyResponse quoteBuy(QuoteBuyRequest req) {
    FundDto fund = registry.findByToken(req.token())
        .orElseThrow(() -> new IllegalArgumentException("Unknown token: " + req.token()));

    BigInteger fixed = fixedVni1e8ForFund(fund);
    if (fixed != null) {
      return quoteBuyFixed(req, fixed);
    }

    Function f = new Function(
        "quoteBuy",
        List.of(new Address(req.token()), new Address(req.user()), new Uint256(new BigInteger(req.tndIn()))),
        List.of(
            new TypeReference<Uint256>() {}, // priceClient
            new TypeReference<Uint256>() {}, // minted
            new TypeReference<Uint256>() {}, // feeBase
            new TypeReference<Uint256>() {}, // vat
            new TypeReference<Uint256>() {}  // totalFee
        )
    );
    List<Type> out = evm.ethCall(fund.pool(), f);
    return new QuoteBuyResponse(
        EvmCallService.uint(out.get(0)).toString(),
        EvmCallService.uint(out.get(1)).toString(),
        EvmCallService.uint(out.get(2)).toString(),
        EvmCallService.uint(out.get(3)).toString(),
        EvmCallService.uint(out.get(4)).toString()
    );
  }

  public QuoteSellResponse quoteSell(QuoteSellRequest req) {
    FundDto fund = registry.findByToken(req.token())
        .orElseThrow(() -> new IllegalArgumentException("Unknown token: " + req.token()));

    BigInteger fixed = fixedVni1e8ForFund(fund);
    if (fixed != null) {
      return quoteSellFixed(req, fixed);
    }

    Function f = new Function(
        "quoteSell",
        List.of(new Address(req.token()), new Address(req.user()), new Uint256(new BigInteger(req.tokenAmount()))),
        List.of(
            new TypeReference<Uint256>() {}, // priceClient
            new TypeReference<Uint256>() {}, // tndOut
            new TypeReference<Uint256>() {}, // feeBase
            new TypeReference<Uint256>() {}, // vat
            new TypeReference<Uint256>() {}, // totalFee
            new TypeReference<Uint256>() {}  // tax
        )
    );
    List<Type> out = evm.ethCall(fund.pool(), f);
    return new QuoteSellResponse(
        EvmCallService.uint(out.get(0)).toString(),
        EvmCallService.uint(out.get(1)).toString(),
        EvmCallService.uint(out.get(2)).toString(),
        EvmCallService.uint(out.get(3)).toString(),
        EvmCallService.uint(out.get(4)).toString(),
        EvmCallService.uint(out.get(5)).toString()
    );
  }

  public PortfolioResponse portfolio(String userAddress) {
    List<FundDto> funds = registry.listFunds();
    List<PortfolioPosition> positions = new ArrayList<>();

    BigInteger totalValue = BigInteger.ZERO;
    BigInteger totalGain = BigInteger.ZERO;

    for (FundDto fund : funds) {
      BigInteger bal = balanceOf(fund.token(), userAddress);
      BigInteger prm = getPrm(fund.token(), userAddress);
      BigInteger vni = new BigInteger(getVni(fund.token()).vni());

      BigInteger valueTnd = bal.multiply(vni).divide(PRICE_SCALE);
      BigInteger gainPerToken = vni.subtract(prm);
      if (gainPerToken.signum() < 0) gainPerToken = BigInteger.ZERO;
      BigInteger gainTnd = bal.multiply(gainPerToken).divide(PRICE_SCALE);

      totalValue = totalValue.add(valueTnd);
      totalGain = totalGain.add(gainTnd);

      positions.add(new PortfolioPosition(
          fund.id(),
          fund.name(),
          fund.symbol(),
          fund.token(),
          fund.pool(),
          fund.oracle(),
          bal.toString(),
          vni.toString(),
          prm.toString(),
          valueTnd.toString(),
          gainTnd.toString()
      ));
    }

    String cashToken = infra.cashTokenAddress();
    BigInteger cashBal = (cashToken == null || cashToken.isBlank()) ? BigInteger.ZERO : balanceOf(cashToken, userAddress);
    BigInteger creditLine = creditLineForWallet(userAddress);
    BigInteger creditDebt = BigInteger.ZERO;
    var activeLoan = debtManager.getActiveLoanForUser(userAddress);
    if (activeLoan != null) creditDebt = activeLoan.principalTnd();

    return new PortfolioResponse(userAddress, positions, cashBal.toString(), creditLine.toString(), creditDebt.toString(), totalValue.toString(), totalGain.toString());
  }

  /** Ligne de crédit test: KYC1=5000 TND, KYC2=10000 TND (jusqu'à intégration API paiement). */
  private BigInteger creditLineForWallet(String walletAddress) {
    return userRepo.findByWalletAddressIgnoreCase(walletAddress)
        .map(u -> u.getKycLevel() >= 2 ? CREDIT_LINE_KYC2 : (u.getKycLevel() >= 1 ? CREDIT_LINE_KYC1 : BigInteger.ZERO))
        .orElse(BigInteger.ZERO);
  }

  /**
   * Audit helper: compute portfolio balances at a historical block (eth_call at blockNumber).
   * Note: VNI/oracle reads will still use "latest" unless fixed price overrides are enabled.
   */
  public PortfolioResponse portfolioAtBlock(String userAddress, BigInteger blockNumber) {
    List<FundDto> funds = registry.listFunds();
    List<PortfolioPosition> positions = new ArrayList<>();

    BigInteger totalValue = BigInteger.ZERO;
    BigInteger totalGain = BigInteger.ZERO;

    for (FundDto fund : funds) {
      BigInteger bal = balanceOfAtBlock(fund.token(), userAddress, blockNumber);
      BigInteger prm = getPrm(fund.token(), userAddress);
      BigInteger vni = new BigInteger(getVni(fund.token()).vni());

      BigInteger valueTnd = bal.multiply(vni).divide(PRICE_SCALE);
      BigInteger gainPerToken = vni.subtract(prm);
      if (gainPerToken.signum() < 0) gainPerToken = BigInteger.ZERO;
      BigInteger gainTnd = bal.multiply(gainPerToken).divide(PRICE_SCALE);

      totalValue = totalValue.add(valueTnd);
      totalGain = totalGain.add(gainTnd);

      positions.add(new PortfolioPosition(
          fund.id(),
          fund.name(),
          fund.symbol(),
          fund.token(),
          fund.pool(),
          fund.oracle(),
          bal.toString(),
          vni.toString(),
          prm.toString(),
          valueTnd.toString(),
          gainTnd.toString()
      ));
    }

    String cashToken = infra.cashTokenAddress();
    BigInteger cashBal = (cashToken == null || cashToken.isBlank())
        ? BigInteger.ZERO
        : balanceOfAtBlock(cashToken, userAddress, blockNumber);
    BigInteger creditLine = creditLineForWallet(userAddress);

    return new PortfolioResponse(userAddress, positions, cashBal.toString(), creditLine.toString(), "0", totalValue.toString(), totalGain.toString());
  }

  public InvestorProfileResponse investorProfile(String userAddress) {
    String kyc = infra.kycRegistryAddress();
    String inv = infra.investorRegistryAddress();
    if (kyc == null || kyc.isBlank()) {
      throw new IllegalStateException("KYCRegistry address not configured in deployments infra.");
    }
    if (inv == null || inv.isBlank()) {
      throw new IllegalStateException("InvestorRegistry address not configured in deployments infra.");
    }

    // Helper: first decoded value or null when RPC/contract returns empty (e.g. user not registered)
    List<Type> out;

    // ---- KYCRegistry ----
    Function fWl = new Function(
        "isWhitelisted",
        List.of(new Address(userAddress)),
        List.of(new TypeReference<Bool>() {})
    );
    out = evm.ethCall(kyc, fWl);
    if (out == null || out.isEmpty()) return defaultInvestorProfile(userAddress);
    boolean whitelisted = bool(out.get(0));

    Function fLvl = new Function(
        "getUserLevel",
        List.of(new Address(userAddress)),
        List.of(new TypeReference<Uint256>() {})
    );
    out = evm.ethCall(kyc, fLvl);
    if (out == null || out.isEmpty()) return defaultInvestorProfile(userAddress);
    int kycLevel = EvmCallService.uint(out.get(0)).intValue();

    Function fRes = new Function(
        "isResident",
        List.of(new Address(userAddress)),
        List.of(new TypeReference<Bool>() {})
    );
    out = evm.ethCall(kyc, fRes);
    if (out == null || out.isEmpty()) return defaultInvestorProfile(userAddress);
    boolean resident = bool(out.get(0));

    // ---- InvestorRegistry ----
    Function fScore = new Function(
        "getScore",
        List.of(new Address(userAddress)),
        List.of(new TypeReference<Uint256>() {})
    );
    out = evm.ethCall(inv, fScore);
    if (out == null || out.isEmpty()) return defaultInvestorProfile(userAddress);
    int score = EvmCallService.uint(out.get(0)).intValue();

    Function fTier = new Function(
        "getTier",
        List.of(new Address(userAddress)),
        List.of(new TypeReference<Uint256>() {})
    );
    out = evm.ethCall(inv, fTier);
    if (out == null || out.isEmpty()) return defaultInvestorProfile(userAddress);
    int tier = EvmCallService.uint(out.get(0)).intValue();

    Function fFee = new Function(
        "getFeeLevel",
        List.of(new Address(userAddress)),
        List.of(new TypeReference<Uint256>() {})
    );
    out = evm.ethCall(inv, fFee);
    if (out == null || out.isEmpty()) return defaultInvestorProfile(userAddress);
    int feeLevel = EvmCallService.uint(out.get(0)).intValue();

    Function fSub = new Function(
        "isSubscriptionActive",
        List.of(new Address(userAddress)),
        List.of(new TypeReference<Bool>() {})
    );
    out = evm.ethCall(inv, fSub);
    if (out == null || out.isEmpty()) return defaultInvestorProfile(userAddress);
    boolean subscriptionActive = bool(out.get(0));

    return new InvestorProfileResponse(
        userAddress,
        whitelisted,
        kycLevel,
        resident,
        score,
        tier,
        feeLevel,
        subscriptionActive
    );
  }

  /** Profil par défaut lorsque l'adresse n'est pas enregistrée ou que le contrat ne retourne pas de donnée. */
  private static InvestorProfileResponse defaultInvestorProfile(String userAddress) {
    return new InvestorProfileResponse(
        userAddress,
        false,  // whitelisted
        0,      // kycLevel
        false,  // resident
        0,      // score
        0,      // tier
        0,      // feeLevel
        false   // subscriptionActive
    );
  }

  public TxHistoryResponse txHistory(String userAddress, int limit) {
    if (limit <= 0) limit = 100;
    if (limit > 500) limit = 500;

    String userTopic = topicAddress(userAddress);
    List<TxRow> out = new ArrayList<>();

    // ----- Buy/Sell events from all pools -----
    List<String> pools = registry.listFunds().stream().map(FundDto::pool).distinct().toList();
    out.addAll(fetchPoolEvents("BUY", BOUGHT, pools, userTopic));
    out.addAll(fetchPoolEvents("SELL", SOLD, pools, userTopic));

    // ----- Cash mint/burn (deposit/withdraw) -----
    String cashToken = infra.cashTokenAddress();
    if (cashToken != null && !cashToken.isBlank()) {
      out.addAll(fetchCashEvents("DEPOSIT", cashToken, ZERO_TOPIC, userTopic));
      out.addAll(fetchCashEvents("WITHDRAW", cashToken, userTopic, ZERO_TOPIC));
    }

    out.sort((a, b) -> {
      int t = Long.compare(b.timestampSec(), a.timestampSec());
      if (t != 0) return t;
      return Long.compare(b.blockNumber(), a.blockNumber());
    });

    if (out.size() > limit) out = out.subList(0, limit);
    return new TxHistoryResponse(userAddress, out);
  }

  private List<TxRow> fetchPoolEvents(String kind, Event ev, List<String> pools, String userTopic) {
    String topic0 = EventEncoder.encode(ev);
    EthFilter filter = new EthFilter(
        new DefaultBlockParameterNumber(BigInteger.ZERO),
        DefaultBlockParameterName.LATEST,
        pools
    );
    filter.addSingleTopic(topic0);
    filter.addNullTopic();          // token indexed (any)
    filter.addSingleTopic(userTopic); // user indexed

    List<Log> logs;
    try {
      logs = web3j.ethGetLogs(filter).send().getLogs().stream().map(lr -> (Log) lr.get()).toList();
    } catch (IOException e) {
      throw new IllegalStateException("eth_getLogs failed: " + e.getMessage(), e);
    }

    Map<BigInteger, Long> tsCache = new HashMap<>();
    List<TxRow> out = new ArrayList<>();
    for (Log l : logs) {
      if (l.getTopics() == null || l.getTopics().size() < 3) continue;
      String tokenAddr = "0x" + l.getTopics().get(1).substring(l.getTopics().get(1).length() - 40);
      FundDto fund = registry.findByToken(tokenAddr).orElse(null);

      List<Type> decoded = FunctionReturnDecoder.decode(l.getData(), ev.getNonIndexedParameters());
      BigInteger a0 = EvmCallService.uint(decoded.get(0));
      BigInteger priceClient = EvmCallService.uint(decoded.get(1));
      BigInteger a2 = EvmCallService.uint(decoded.get(2));

      BigInteger blockNo = l.getBlockNumber();
      long ts = blockTimestampSec(blockNo, tsCache);
      String id = l.getTransactionHash() + ":" + (l.getLogIndex() != null ? l.getLogIndex().toString() : "0");

      String amountTnd;
      String amountToken;
      if ("BUY".equals(kind)) {
        amountTnd = a0.toString();     // tndIn
        amountToken = a2.toString();   // minted
      } else {
        amountTnd = a2.toString();     // tndOut
        amountToken = a0.toString();   // tokenAmount
      }

      out.add(new TxRow(
          id,
          kind,
          fund != null ? fund.id() : -1,
          fund != null ? fund.name() : "Unknown",
          fund != null ? fund.symbol() : "",
          tokenAddr,
          amountTnd,
          amountToken,
          priceClient.toString(),
          blockNo.longValue(),
          ts,
          l.getTransactionHash()
      ));
    }
    return out;
  }

  private List<TxRow> fetchCashEvents(String kind, String cashToken, String topicFrom, String topicTo) {
    String topic0 = EventEncoder.encode(TRANSFER);
    EthFilter filter = new EthFilter(
        new DefaultBlockParameterNumber(BigInteger.ZERO),
        DefaultBlockParameterName.LATEST,
        cashToken
    );
    filter.addSingleTopic(topic0);
    filter.addSingleTopic(topicFrom);
    filter.addSingleTopic(topicTo);

    List<Log> logs;
    try {
      logs = web3j.ethGetLogs(filter).send().getLogs().stream().map(lr -> (Log) lr.get()).toList();
    } catch (IOException e) {
      throw new IllegalStateException("eth_getLogs failed: " + e.getMessage(), e);
    }

    Map<BigInteger, Long> tsCache = new HashMap<>();
    List<TxRow> out = new ArrayList<>();
    for (Log l : logs) {
      List<Type> decoded = FunctionReturnDecoder.decode(l.getData(), TRANSFER.getNonIndexedParameters());
      BigInteger value = EvmCallService.uint(decoded.get(0));
      BigInteger blockNo = l.getBlockNumber();
      long ts = blockTimestampSec(blockNo, tsCache);
      String id = l.getTransactionHash() + ":" + (l.getLogIndex() != null ? l.getLogIndex().toString() : "0");

      out.add(new TxRow(
          id,
          kind,
          -1,
          "Cash",
          "TND",
          cashToken,
          value.toString(),
          "0",
          "0",
          blockNo.longValue(),
          ts,
          l.getTransactionHash()
      ));
    }
    return out;
  }

  private long blockTimestampSec(BigInteger blockNo, Map<BigInteger, Long> cache) {
    if (blockNo == null) return 0;
    Long cached = cache.get(blockNo);
    if (cached != null) return cached;
    try {
      var b = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(blockNo), false).send().getBlock();
      long ts = b != null && b.getTimestamp() != null ? b.getTimestamp().longValue() : 0L;
      cache.put(blockNo, ts);
      return ts;
    } catch (IOException e) {
      throw new IllegalStateException("eth_getBlockByNumber failed: " + e.getMessage(), e);
    }
  }

  private static String topicAddress(String addr) {
    String a = addr.toLowerCase();
    if (a.startsWith("0x")) a = a.substring(2);
    return "0x" + "0".repeat(24) + a;
  }

  private BigInteger balanceOf(String token, String user) {
    Function f = new Function(
        "balanceOf",
        List.of(new Address(user)),
        List.of(new TypeReference<Uint256>() {})
    );
    List<Type> out = evm.ethCall(token, f);
    if (out == null || out.isEmpty()) {
      return BigInteger.ZERO;
    }
    return EvmCallService.uint(out.get(0));
  }

  private BigInteger balanceOfAtBlock(String token, String user, BigInteger blockNumber) {
    Function f = new Function(
        "balanceOf",
        List.of(new Address(user)),
        List.of(new TypeReference<Uint256>() {})
    );
    List<Type> out = evm.ethCallAtBlock(token, f, blockNumber);
    if (out == null || out.isEmpty()) {
      return BigInteger.ZERO;
    }
    return EvmCallService.uint(out.get(0));
  }

  private BigInteger getPrm(String token, String user) {
    Function f = new Function(
        "getPRM",
        List.of(new Address(user)),
        List.of(new TypeReference<Uint256>() {})
    );
    List<Type> out = evm.ethCall(token, f);
    if (out == null || out.isEmpty()) return BigInteger.ZERO;
    return EvmCallService.uint(out.get(0));
  }

  private BigInteger fixedVni1e8ForFund(FundDto fund) {
    BlockchainProperties.PriceOverrides po = props.priceOverrides();
    if (po == null || !po.enabled()) return null;

    String name = fund.name() != null ? fund.name().toLowerCase() : "";
    if (name.contains("atlas")) {
      return BigInteger.valueOf(po.atlasTnd()).multiply(PRICE_SCALE);
    }
    if (name.contains("didon")) {
      return BigInteger.valueOf(po.didonTnd()).multiply(PRICE_SCALE);
    }
    // fallback by id if names change
    if (fund.id() == 0) return BigInteger.valueOf(po.atlasTnd()).multiply(PRICE_SCALE);
    if (fund.id() == 1) return BigInteger.valueOf(po.didonTnd()).multiply(PRICE_SCALE);
    return null;
  }

  private QuoteBuyResponse quoteBuyFixed(QuoteBuyRequest req, BigInteger vni1e8) {
    BlockchainProperties.PriceOverrides po = props.priceOverrides();
    BigInteger tndIn = new BigInteger(req.tndIn());

    BigInteger priceClient = vni1e8.multiply(BPS.add(BigInteger.valueOf(po.spreadBps()))).divide(BPS);
    BigInteger feeBase = tndIn.multiply(BigInteger.valueOf(po.feeBps())).divide(BPS);
    BigInteger vat = feeBase.multiply(BigInteger.valueOf(po.vatBps())).divide(BPS);
    BigInteger totalFee = feeBase.add(vat);
    BigInteger netTnd = tndIn.subtract(totalFee);
    BigInteger minted = netTnd.multiply(PRICE_SCALE).divide(priceClient);

    return new QuoteBuyResponse(
        priceClient.toString(),
        minted.toString(),
        feeBase.toString(),
        vat.toString(),
        totalFee.toString()
    );
  }

  private QuoteSellResponse quoteSellFixed(QuoteSellRequest req, BigInteger vni1e8) {
    BlockchainProperties.PriceOverrides po = props.priceOverrides();
    BigInteger tokenAmount = new BigInteger(req.tokenAmount());

    BigInteger priceClient = vni1e8.multiply(BPS.subtract(BigInteger.valueOf(po.spreadBps()))).divide(BPS);
    BigInteger grossTnd = tokenAmount.multiply(priceClient).divide(PRICE_SCALE);
    BigInteger feeBase = grossTnd.multiply(BigInteger.valueOf(po.feeBps())).divide(BPS);
    BigInteger vat = feeBase.multiply(BigInteger.valueOf(po.vatBps())).divide(BPS);
    BigInteger totalFee = feeBase.add(vat);
    BigInteger tax = BigInteger.ZERO; // MVP fixed price mode: no PRM tax calculation
    BigInteger tndOut = grossTnd.subtract(totalFee).subtract(tax);

    return new QuoteSellResponse(
        priceClient.toString(),
        tndOut.toString(),
        feeBase.toString(),
        vat.toString(),
        totalFee.toString(),
        tax.toString()
    );
  }

  private static boolean bool(Type t) {
    return (Boolean) t.getValue();
  }
}

