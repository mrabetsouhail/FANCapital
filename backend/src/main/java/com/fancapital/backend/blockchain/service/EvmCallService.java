package com.fancapital.backend.blockchain.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

@Service
public class EvmCallService {
  private final Web3j web3j;

  public EvmCallService(Web3j web3j) {
    this.web3j = web3j;
  }

  public List<Type> ethCall(String contract, Function function) {
    String data = FunctionEncoder.encode(function);
    Transaction tx = Transaction.createEthCallTransaction(null, contract, data);
    EthCall res;
    try {
      res = web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send();
    } catch (IOException e) {
      throw new IllegalStateException("RPC eth_call failed: " + e.getMessage(), e);
    }
    if (res.hasError()) {
      throw new IllegalStateException("eth_call error: " + res.getError().getMessage());
    }
    return FunctionReturnDecoder.decode(res.getValue(), function.getOutputParameters());
  }

  public static BigInteger uint(Type t) {
    return (BigInteger) t.getValue();
  }
}

