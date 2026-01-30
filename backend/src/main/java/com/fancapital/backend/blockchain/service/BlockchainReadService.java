package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.blockchain.model.OracleVniResponse;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteBuyRequest;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteBuyResponse;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteSellRequest;
import com.fancapital.backend.blockchain.model.QuoteDtos.QuoteSellResponse;
import com.fancapital.backend.blockchain.model.FundDto;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.Type;

@Service
public class BlockchainReadService {
  private final DeploymentRegistry registry;
  private final EvmCallService evm;

  public BlockchainReadService(DeploymentRegistry registry, EvmCallService evm) {
    this.registry = registry;
    this.evm = evm;
  }

  public OracleVniResponse getVni(String tokenAddress) {
    FundDto fund = registry.findByToken(tokenAddress)
        .orElseThrow(() -> new IllegalArgumentException("Unknown token: " + tokenAddress));

    // PriceOracle.getVNIData(address) returns (uint256 vni, uint64 updatedAt)
    Function f1 = new Function(
        "getVNIData",
        List.of(new Address(tokenAddress)),
        List.of(new TypeReference<Uint256>() {}, new TypeReference<Uint64>() {})
    );
    List<Type> out1 = evm.ethCall(fund.oracle(), f1);
    BigInteger vni = EvmCallService.uint(out1.get(0));
    BigInteger updatedAt = EvmCallService.uint(out1.get(1));

    Function f2 = new Function(
        "getVolatilityBps",
        List.of(new Address(tokenAddress)),
        List.of(new TypeReference<Uint256>() {})
    );
    List<Type> out2 = evm.ethCall(fund.oracle(), f2);
    BigInteger volBps = EvmCallService.uint(out2.get(0));

    return new OracleVniResponse(tokenAddress, vni.toString(), updatedAt.toString(), volBps.intValue());
  }

  public QuoteBuyResponse quoteBuy(QuoteBuyRequest req) {
    FundDto fund = registry.findByToken(req.token())
        .orElseThrow(() -> new IllegalArgumentException("Unknown token: " + req.token()));

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
}

