package com.fancapital.backend.blockchain.service;

import com.fancapital.backend.config.BlockchainProperties;
import java.io.IOException;
import java.math.BigInteger;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

@Service
public class OperatorDiagnosticsService {
  private final Web3j web3j;
  private final BlockchainProperties props;

  public OperatorDiagnosticsService(Web3j web3j, BlockchainProperties props) {
    this.web3j = web3j;
    this.props = props;
  }

  public record OperatorInfo(
      String operatorAddress,
      String balanceWei,
      String gasPriceWei,
      String chainId
  ) {}

  public OperatorInfo info() {
    String pk = props.operatorPrivateKey();
    if (pk == null || pk.isBlank()) {
      return new OperatorInfo(null, null, gasPriceWeiSafe(), chainIdSafe());
    }
    Credentials c = Credentials.create(pk.trim());
    String addr = c.getAddress();
    return new OperatorInfo(addr, balanceWeiSafe(addr), gasPriceWeiSafe(), chainIdSafe());
  }

  private String balanceWeiSafe(String addr) {
    try {
      return web3j.ethGetBalance(addr, DefaultBlockParameterName.LATEST).send().getBalance().toString();
    } catch (IOException e) {
      return "RPC_ERROR: " + e.getMessage();
    }
  }

  private String gasPriceWeiSafe() {
    try {
      return web3j.ethGasPrice().send().getGasPrice().toString();
    } catch (IOException e) {
      return "RPC_ERROR: " + e.getMessage();
    }
  }

  private String chainIdSafe() {
    try {
      BigInteger id = web3j.ethChainId().send().getChainId();
      return id.toString();
    } catch (IOException e) {
      return "RPC_ERROR: " + e.getMessage();
    }
  }
}

