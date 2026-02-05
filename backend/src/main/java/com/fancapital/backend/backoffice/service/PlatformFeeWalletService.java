package com.fancapital.backend.backoffice.service;

import com.fancapital.backend.backoffice.model.PlatformFeeWalletDtos;
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
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;

/**
 * Service pour suivre les frais collectés par la plateforme.
 * Les frais sont envoyés au wallet "treasury" configuré dans les contrats LiquidityPool et P2PExchange.
 */
@Service
public class PlatformFeeWalletService {
  // Event Bought from LiquidityPool
  private static final Event BOUGHT_EVENT = new Event(
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

  // Event Sold from LiquidityPool
  private static final Event SOLD_EVENT = new Event(
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

  // Note: P2P Exchange fees can be added later if needed

  private final Web3j web3j;
  private final EvmCallService evm;
  private final DeploymentInfraService infra;
  private final DeploymentRegistry registry;

  public PlatformFeeWalletService(Web3j web3j, EvmCallService evm, DeploymentInfraService infra, DeploymentRegistry registry) {
    this.web3j = web3j;
    this.evm = evm;
    this.infra = infra;
    this.registry = registry;
  }

  /**
   * Récupère le dashboard des frais de la plateforme.
   * @return Dashboard avec solde total, frais par fond, et historique
   */
  @SuppressWarnings("rawtypes")
  public PlatformFeeWalletDtos.FeeWalletDashboard dashboard() {
    String treasuryAddress = getTreasuryAddress();
    String cashToken = infra.cashTokenAddress();
    
    if (treasuryAddress == null || treasuryAddress.isBlank()) {
      throw new IllegalStateException("Treasury address not configured. Check liquidity pool contracts.");
    }
    if (cashToken == null || cashToken.isBlank()) {
      throw new IllegalStateException("CashTokenTND address not configured");
    }

    // Lire le solde du treasury wallet
    Function balFn = new Function(
        "balanceOf",
        List.of(new Address(treasuryAddress)),
        List.of(TypeReference.create(Uint256.class))
    );
    BigInteger treasuryBalance = (BigInteger) evm.ethCall(cashToken, balFn).get(0).getValue();

    // Agréger les frais par fond (token)
    Map<String, BigInteger> feesByToken = new HashMap<>();
    Map<String, Long> countByToken = new HashMap<>();
    Map<String, BigInteger> feesBaseByToken = new HashMap<>();
    Map<String, BigInteger> vatByToken = new HashMap<>();

    List<FundDto> funds = registry.listFunds();
    
    // Parcourir tous les pools pour collecter les événements Bought et Sold
    for (FundDto fund : funds) {
      if (fund.pool() == null || fund.pool().isBlank()) continue;
      
      String token = fund.token().toLowerCase();
      
      // Collecter les événements Bought
      List<Log> boughtLogs = fetchBoughtLogs(fund.pool());
      for (Log log : boughtLogs) {
        List<Type> decoded = FunctionReturnDecoder.decode(log.getData(), BOUGHT_EVENT.getNonIndexedParameters());
        // Non-indexed: tndIn, priceClient, mintedAmount, feeBase, vat, totalFee
        BigInteger feeBase = (BigInteger) decoded.get(3).getValue();
        BigInteger vat = (BigInteger) decoded.get(4).getValue();
        BigInteger totalFee = (BigInteger) decoded.get(5).getValue();
        
        feesByToken.merge(token, totalFee, BigInteger::add);
        feesBaseByToken.merge(token, feeBase, BigInteger::add);
        vatByToken.merge(token, vat, BigInteger::add);
        countByToken.merge(token, 1L, Long::sum);
      }
      
      // Collecter les événements Sold
      List<Log> soldLogs = fetchSoldLogs(fund.pool());
      for (Log log : soldLogs) {
        List<Type> decoded = FunctionReturnDecoder.decode(log.getData(), SOLD_EVENT.getNonIndexedParameters());
        // Non-indexed: tokenAmount, priceClient, tndOut, feeBase, vat, totalFee
        BigInteger feeBase = (BigInteger) decoded.get(3).getValue();
        BigInteger vat = (BigInteger) decoded.get(4).getValue();
        BigInteger totalFee = (BigInteger) decoded.get(5).getValue();
        
        feesByToken.merge(token, totalFee, BigInteger::add);
        feesBaseByToken.merge(token, feeBase, BigInteger::add);
        vatByToken.merge(token, vat, BigInteger::add);
        countByToken.merge(token, 1L, Long::sum);
      }
    }

    // Collecter les frais P2P (si P2PExchange est déployé)
    // Note: Pour l'instant, on se concentre sur les pools. P2P peut être ajouté plus tard.

    // Construire la réponse
    List<PlatformFeeWalletDtos.FundFeeSummary> byFund = new ArrayList<>();
    for (FundDto fund : funds) {
      String token = fund.token().toLowerCase();
      BigInteger totalFees = feesByToken.getOrDefault(token, BigInteger.ZERO);
      BigInteger feesBase = feesBaseByToken.getOrDefault(token, BigInteger.ZERO);
      BigInteger vat = vatByToken.getOrDefault(token, BigInteger.ZERO);
      long count = countByToken.getOrDefault(token, 0L);
      
      byFund.add(new PlatformFeeWalletDtos.FundFeeSummary(
          fund.id(),
          fund.name(),
          fund.symbol(),
          fund.token(),
          fund.pool(),
          totalFees.toString(),
          feesBase.toString(),
          vat.toString(),
          count
      ));
    }

    return new PlatformFeeWalletDtos.FeeWalletDashboard(
        treasuryAddress,
        cashToken,
        treasuryBalance.toString(),
        byFund
    );
  }

  /**
   * Récupère l'adresse du treasury depuis un pool (tous les pools ont la même adresse treasury).
   */
  @SuppressWarnings("rawtypes")
  private String getTreasuryAddress() {
    List<FundDto> funds = registry.listFunds();
    if (funds.isEmpty()) {
      throw new IllegalStateException("No funds found. Cannot determine treasury address.");
    }
    
    // Utiliser le premier pool pour lire l'adresse treasury
    String poolAddress = funds.get(0).pool();
    if (poolAddress == null || poolAddress.isBlank()) {
      throw new IllegalStateException("Pool address not found for fund: " + funds.get(0).name());
    }
    
    Function treasuryFn = new Function(
        "treasury",
        List.of(),
        List.of(TypeReference.create(Address.class))
    );
    return ((Address) evm.ethCall(poolAddress, treasuryFn).get(0)).getValue();
  }

  @SuppressWarnings("rawtypes")
  private List<Log> fetchBoughtLogs(String poolAddress) {
    String topic0 = EventEncoder.encode(BOUGHT_EVENT);
    EthFilter filter = new EthFilter(
        new DefaultBlockParameterNumber(BigInteger.ZERO),
        DefaultBlockParameterName.LATEST,
        poolAddress
    );
    filter.addSingleTopic(topic0);
    try {
      return web3j.ethGetLogs(filter).send().getLogs().stream()
          .map(lr -> (Log) lr.get())
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("eth_getLogs failed for Bought events: " + e.getMessage(), e);
    }
  }

  @SuppressWarnings("rawtypes")
  private List<Log> fetchSoldLogs(String poolAddress) {
    String topic0 = EventEncoder.encode(SOLD_EVENT);
    EthFilter filter = new EthFilter(
        new DefaultBlockParameterNumber(BigInteger.ZERO),
        DefaultBlockParameterName.LATEST,
        poolAddress
    );
    filter.addSingleTopic(topic0);
    try {
      return web3j.ethGetLogs(filter).send().getLogs().stream()
          .map(lr -> (Log) lr.get())
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("eth_getLogs failed for Sold events: " + e.getMessage(), e);
    }
  }
}
