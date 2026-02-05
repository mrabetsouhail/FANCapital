package com.fancapital.backend.backoffice.audit.service;

import com.fancapital.backend.auth.repo.AppUserRepository;
import com.fancapital.backend.backoffice.audit.model.AuditAlert;
import com.fancapital.backend.backoffice.audit.model.AuditTokenSyncState;
import com.fancapital.backend.backoffice.audit.model.AuditUserTokenBalance;
import com.fancapital.backend.backoffice.audit.repo.AuditAlertRepository;
import com.fancapital.backend.backoffice.audit.repo.AuditTokenSyncStateRepository;
import com.fancapital.backend.backoffice.audit.repo.AuditUserTokenBalanceRepository;
import com.fancapital.backend.blockchain.model.FundDto;
import com.fancapital.backend.blockchain.service.DeploymentRegistry;
import com.fancapital.backend.blockchain.service.EvmCallService;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;

@Service
public class AuditReconciliationService {
  private static final Event TRANSFER = new Event(
      "Transfer",
      List.of(
          TypeReference.create(Address.class, true), // from
          TypeReference.create(Address.class, true), // to
          TypeReference.create(Uint256.class)        // value
      )
  );

  private final Web3j web3j;
  private final DeploymentRegistry registry;
  private final AppUserRepository users;
  private final EvmCallService evm;
  private final AuditTokenSyncStateRepository syncRepo;
  private final AuditUserTokenBalanceRepository balRepo;
  private final AuditAlertRepository alertRepo;
  private final AuditLogService auditLog;

  public AuditReconciliationService(
      Web3j web3j,
      DeploymentRegistry registry,
      AppUserRepository users,
      EvmCallService evm,
      AuditTokenSyncStateRepository syncRepo,
      AuditUserTokenBalanceRepository balRepo,
      AuditAlertRepository alertRepo,
      AuditLogService auditLog
  ) {
    this.web3j = web3j;
    this.registry = registry;
    this.users = users;
    this.evm = evm;
    this.syncRepo = syncRepo;
    this.balRepo = balRepo;
    this.alertRepo = alertRepo;
    this.auditLog = auditLog;
  }

  public record ReconcileResult(long latestBlock, int tokensSynced, long transfersProcessed, int alertsCreated) {}

  @Transactional
  public ReconcileResult reconcileOnce(String actorUserId, String actorEmail) {
    long latest = latestBlockNumber();
    Map<String, String> walletToUser = loadKnownWallets();

    long transfers = 0;
    int tokensSynced = 0;

    for (FundDto fund : registry.listFunds()) {
      String token = fund.token();
      transfers += syncTokenTransfers(token, latest, walletToUser);
      tokensSynced++;
    }

    // Optional: cash token reconciliation could be added similarly
    // String cash = infra.cashTokenAddress();

    int alerts = 0;
    for (FundDto fund : registry.listFunds()) {
      alerts += detectMismatchesForToken(fund.token(), latest, walletToUser);
    }

    auditLog.append("RECONCILE_RUN", actorUserId, actorEmail, null, null, null,
        "latestBlock=" + latest + ", tokens=" + tokensSynced + ", transfers=" + transfers + ", alerts=" + alerts);

    return new ReconcileResult(latest, tokensSynced, transfers, alerts);
  }

  private long syncTokenTransfers(String tokenAddress, long latestBlock, Map<String, String> walletToUser) {
    AuditTokenSyncState st = syncRepo.findById(tokenAddress.toLowerCase(Locale.ROOT))
        .orElseGet(() -> new AuditTokenSyncState(tokenAddress.toLowerCase(Locale.ROOT)));

    long from = st.getLastProcessedBlock() <= 0 ? 0 : st.getLastProcessedBlock() + 1;
    long to = latestBlock;
    if (from > to) return 0;

    long processed = 0;
    long chunk = 20_000;

    for (long start = from; start <= to; start += chunk) {
      long end = Math.min(to, start + chunk - 1);
      processed += applyTransferLogs(tokenAddress, start, end, walletToUser);
    }

    st.setLastProcessedBlock(to);
    st.setUpdatedAt(Instant.now());
    syncRepo.save(st);
    return processed;
  }

  private long applyTransferLogs(String tokenAddress, long fromBlock, long toBlock, Map<String, String> walletToUser) {
    String topic0 = EventEncoder.encode(TRANSFER);
    EthFilter filter = new EthFilter(
        new DefaultBlockParameterNumber(BigInteger.valueOf(fromBlock)),
        new DefaultBlockParameterNumber(BigInteger.valueOf(toBlock)),
        tokenAddress
    );
    filter.addSingleTopic(topic0);

    List<Log> logs;
    try {
      logs = web3j.ethGetLogs(filter).send().getLogs().stream().map(lr -> (Log) lr.get()).toList();
    } catch (IOException e) {
      throw new IllegalStateException("eth_getLogs failed: " + e.getMessage(), e);
    }

    if (logs.isEmpty()) return 0;

    // Accumulate deltas in-memory
    Map<String, BigInteger> deltaByUser = new HashMap<>(); // userId -> delta
    for (Log l : logs) {
      if (l.getTopics() == null || l.getTopics().size() < 3) continue;
      String fromAddr = topicToAddress(l.getTopics().get(1));
      String toAddr = topicToAddress(l.getTopics().get(2));

      BigInteger value = decodeTransferValue(l.getData());
      if (value.signum() == 0) continue;

      String fromUser = walletToUser.get(fromAddr);
      if (fromUser != null) {
        deltaByUser.merge(fromUser, value.negate(), BigInteger::add);
      }
      String toUser = walletToUser.get(toAddr);
      if (toUser != null) {
        deltaByUser.merge(toUser, value, BigInteger::add);
      }
    }

    // Apply deltas to DB
    for (var entry : deltaByUser.entrySet()) {
      String userId = entry.getKey();
      BigInteger delta = entry.getValue();
      if (delta.signum() == 0) continue;

      String wallet = users.findById(userId).map(u -> u.getWalletAddress()).orElse(null);
      if (wallet == null || wallet.isBlank()) continue;

      String key = AuditUserTokenBalance.key(userId, tokenAddress);
      AuditUserTokenBalance b = balRepo.findById(key).orElseGet(() -> {
        AuditUserTokenBalance x = new AuditUserTokenBalance();
        x.setId(key);
        x.setUserId(userId);
        x.setWalletAddress(wallet);
        x.setTokenAddress(tokenAddress.toLowerCase(Locale.ROOT));
        x.setBalance1e8("0");
        return x;
      });

      BigInteger old = new BigInteger(b.getBalance1e8());
      BigInteger next = old.add(delta);
      if (next.signum() < 0) next = BigInteger.ZERO;

      b.setBalance1e8(next.toString());
      b.setLastUpdatedBlock(Math.max(b.getLastUpdatedBlock(), toBlock));
      b.setUpdatedAt(Instant.now());
      balRepo.save(b);
    }

    return logs.size();
  }

  private int detectMismatchesForToken(String tokenAddress, long latestBlock, Map<String, String> walletToUser) {
    int created = 0;
    // Ensure we have a row for each known user/wallet even if they had no transfers yet.
    var allUsers = users.findAll();
    for (var u : allUsers) {
      String wallet = u.getWalletAddress();
      if (wallet == null || !wallet.startsWith("0x") || wallet.length() != 42) continue;

      String key = AuditUserTokenBalance.key(u.getId(), tokenAddress);
      AuditUserTokenBalance b = balRepo.findById(key).orElseGet(() -> {
        AuditUserTokenBalance x = new AuditUserTokenBalance();
        x.setId(key);
        x.setUserId(u.getId());
        x.setWalletAddress(wallet);
        x.setTokenAddress(tokenAddress.toLowerCase(Locale.ROOT));
        x.setBalance1e8("0");
        x.setLastUpdatedBlock(0);
        x.setUpdatedAt(Instant.now());
        return balRepo.save(x);
      });

      BigInteger expected = new BigInteger(b.getBalance1e8());
      BigInteger onchain = balanceOf(tokenAddress, wallet);
      if (expected.equals(onchain)) continue;

      AuditAlert lastOpen = alertRepo.findTop1ByResolvedAtIsNullAndUserIdAndTokenAddressIgnoreCaseOrderByCreatedAtDesc(u.getId(), tokenAddress);
      if (lastOpen != null
          && safeEq(lastOpen.getExpectedBalance1e8(), expected.toString())
          && safeEq(lastOpen.getOnchainBalance1e8(), onchain.toString())) {
        continue; // don't spam duplicates
      }

      AuditAlert a = new AuditAlert();
      a.setSeverity("CRITICAL");
      a.setKind("BALANCE_MISMATCH");
      a.setUserId(u.getId());
      a.setWalletAddress(wallet);
      a.setTokenAddress(tokenAddress);
      a.setExpectedBalance1e8(expected.toString());
      a.setOnchainBalance1e8(onchain.toString());
      a.setCheckedAtBlock(latestBlock);
      a.setDetails("Indexed transfer-sum balance differs from ERC20.balanceOf");
      alertRepo.save(a);

      auditLog.append("ALERT_BALANCE_MISMATCH", null, null, u.getId(), null, null,
          "token=" + tokenAddress + ", expected=" + expected + ", onchain=" + onchain + ", block=" + latestBlock);
      created++;
    }
    return created;
  }

  private Map<String, String> loadKnownWallets() {
    Map<String, String> m = new HashMap<>();
    for (var u : users.findAll()) {
      String w = u.getWalletAddress();
      if (w == null || !w.startsWith("0x") || w.length() != 42) continue;
      m.put(w.toLowerCase(Locale.ROOT), u.getId());
    }
    return m;
  }

  private long latestBlockNumber() {
    try {
      return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber().longValue();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to get latest block: " + e.getMessage(), e);
    }
  }

  private BigInteger balanceOf(String token, String wallet) {
    Function f = new Function(
        "balanceOf",
        List.of(new Address(wallet)),
        List.of(new TypeReference<Uint256>() {})
    );
    @SuppressWarnings("rawtypes")
    List<Type> out = evm.ethCall(token, f);
    return EvmCallService.uint(out.get(0));
  }

  private static String topicToAddress(String topic) {
    // topic is 0x + 64 hex, last 40 hex are address
    String t = topic.toLowerCase(Locale.ROOT);
    if (t.startsWith("0x")) t = t.substring(2);
    String last40 = t.substring(t.length() - 40);
    return ("0x" + last40).toLowerCase(Locale.ROOT);
  }

  @SuppressWarnings("rawtypes")
  private static BigInteger decodeTransferValue(String data) {
    List<Type> decoded = FunctionReturnDecoder.decode(data, TRANSFER.getNonIndexedParameters());
    if (decoded.isEmpty()) return BigInteger.ZERO;
    return (BigInteger) decoded.get(0).getValue();
  }

  private static boolean safeEq(String a, String b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.equals(b);
  }
}

