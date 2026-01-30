package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.backoffice.model.FiscalDashboardDtos;
import com.fancapital.backend.blockchain.model.FundDto;
import com.fancapital.backend.blockchain.service.DeploymentRegistry;
import com.fancapital.backend.blockchain.service.EvmCallService;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
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
public class TaxVaultDashboardService {
  private static final Event RAS_RECORDED = new Event(
      "RASRecorded",
      List.of(
          TypeReference.create(Address.class, true), // user (indexed)
          TypeReference.create(Address.class, true), // token (indexed)
          TypeReference.create(Uint256.class),       // gainTnd
          TypeReference.create(Uint256.class),       // taxTnd
          TypeReference.create(Bool.class),          // resident
          TypeReference.create(Uint256.class)        // timestamp
      )
  );

  private final Web3j web3j;
  private final EvmCallService evm;
  private final DeploymentInfraService infra;
  private final DeploymentRegistry registry;

  public TaxVaultDashboardService(Web3j web3j, EvmCallService evm, DeploymentInfraService infra, DeploymentRegistry registry) {
    this.web3j = web3j;
    this.evm = evm;
    this.infra = infra;
    this.registry = registry;
  }

  public FiscalDashboardDtos.FiscalDashboardResponse dashboard() {
    String taxVault = infra.taxVaultAddress();
    String cashToken = infra.cashTokenAddress();
    if (taxVault == null || taxVault.isBlank()) throw new IllegalStateException("TaxVault address not configured");
    if (cashToken == null || cashToken.isBlank()) throw new IllegalStateException("CashTokenTND address not configured");

    // Read fiscAddress from TaxVault
    Function fiscFn = new Function(
        "fiscAddress",
        List.of(),
        List.of(TypeReference.create(Address.class))
    );
    String fiscAddress = ((Address) evm.ethCall(taxVault, fiscFn).get(0)).getValue();

    // Read cashToken.balanceOf(taxVault)
    Function balFn = new Function(
        "balanceOf",
        List.of(new Address(taxVault)),
        List.of(TypeReference.create(Uint256.class))
    );
    BigInteger bal = (BigInteger) evm.ethCall(cashToken, balFn).get(0).getValue();

    // Aggregate RASRecorded taxTnd by token
    Map<String, BigInteger> taxByToken = new HashMap<>();
    Map<String, Long> countByToken = new HashMap<>();
    List<Log> logs = fetchRasLogs(taxVault);
    for (Log l : logs) {
      // topic0 = signature, topic2 = indexed token
      if (l.getTopics() == null || l.getTopics().size() < 3) continue;
      String tokenTopic = l.getTopics().get(2); // indexed token
      String tokenAddr = "0x" + tokenTopic.substring(tokenTopic.length() - 40);
      tokenAddr = tokenAddr.toLowerCase();

      List<Type> decoded = FunctionReturnDecoder.decode(l.getData(), RAS_RECORDED.getNonIndexedParameters());
      // non-indexed are: gainTnd, taxTnd, resident, timestamp
      BigInteger taxTnd = (BigInteger) decoded.get(1).getValue();

      taxByToken.merge(tokenAddr, taxTnd, BigInteger::add);
      countByToken.merge(tokenAddr, 1L, Long::sum);
    }

    List<FundDto> funds = registry.listFunds();
    List<FiscalDashboardDtos.FundTaxSummary> byFund = new ArrayList<>();
    for (FundDto f : funds) {
      String token = f.token().toLowerCase();
      BigInteger tax = taxByToken.getOrDefault(token, BigInteger.ZERO);
      long cnt = countByToken.getOrDefault(token, 0L);
      byFund.add(new FiscalDashboardDtos.FundTaxSummary(
          f.id(),
          f.name(),
          f.symbol(),
          f.token(),
          tax.toString(),
          cnt
      ));
    }

    return new FiscalDashboardDtos.FiscalDashboardResponse(
        infra.deploymentsPathUsed(),
        taxVault,
        cashToken,
        fiscAddress,
        bal.toString(),
        byFund
    );
  }

  private List<Log> fetchRasLogs(String taxVaultAddress) {
    String topic0 = EventEncoder.encode(RAS_RECORDED);
    EthFilter filter = new EthFilter(
        new DefaultBlockParameterNumber(BigInteger.ZERO),
        DefaultBlockParameterName.LATEST,
        taxVaultAddress
    );
    filter.addSingleTopic(topic0);
    try {
      return web3j.ethGetLogs(filter).send().getLogs().stream()
          .map(lr -> (Log) lr.get())
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("eth_getLogs failed: " + e.getMessage(), e);
    }
  }
}

