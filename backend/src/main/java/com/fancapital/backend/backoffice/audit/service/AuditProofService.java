package com.fancapital.backend.backoffice.audit.service;

import com.fancapital.backend.backoffice.audit.model.AuditCheckpoint;
import com.fancapital.backend.backoffice.audit.repo.AuditCheckpointRepository;
import com.fancapital.backend.blockchain.model.FundDto;
import com.fancapital.backend.blockchain.service.DeploymentRegistry;
import com.fancapital.backend.blockchain.service.EvmCallService;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;

/**
 * Service de génération de checkpoints d'audit tous les 10 000 blocs.
 * 
 * Conforme au Livre Blanc FAN-Capital v2.1 - Section 4.1
 * Optimise les inspections réglementaires en permettant de vérifier l'intégrité
 * des données sans recalculer l'historique complet depuis le bloc zéro.
 */
@Service
public class AuditProofService {
  private static final long CHECKPOINT_INTERVAL = 10_000L;

  private final Web3j web3j;
  private final DeploymentRegistry registry;
  private final EvmCallService evm;
  private final AuditCheckpointRepository checkpointRepo;
  private final AuditLogService auditLog;

  public AuditProofService(
      Web3j web3j,
      DeploymentRegistry registry,
      EvmCallService evm,
      AuditCheckpointRepository checkpointRepo,
      AuditLogService auditLog
  ) {
    this.web3j = web3j;
    this.registry = registry;
    this.evm = evm;
    this.checkpointRepo = checkpointRepo;
    this.auditLog = auditLog;
  }

  /**
   * Génère un checkpoint pour un token à un numéro de bloc donné si nécessaire.
   * Un checkpoint est créé tous les 10 000 blocs.
   */
  @Transactional
  public void generateCheckpointIfNeeded(String tokenAddress) {
    long latestBlock = getLatestBlockNumber();
    long checkpointBlock = (latestBlock / CHECKPOINT_INTERVAL) * CHECKPOINT_INTERVAL;
    
    if (checkpointBlock == 0) {
      return; // Pas de checkpoint au bloc 0
    }

    AuditCheckpoint lastCheckpoint = checkpointRepo
        .findTopByTokenAddressOrderByBlockNumberDesc(tokenAddress)
        .orElse(null);

    if (lastCheckpoint != null && lastCheckpoint.getBlockNumber() >= checkpointBlock) {
      return; // Checkpoint déjà généré pour ce bloc
    }

    generateCheckpoint(tokenAddress, checkpointBlock);
  }

  /**
   * Génère un checkpoint pour tous les tokens actifs.
   */
  @Transactional
  public void generateCheckpointsForAllTokens() {
    for (FundDto fund : registry.listFunds()) {
      generateCheckpointIfNeeded(fund.token());
    }
  }

  /**
   * Génère un checkpoint à un bloc spécifique.
   */
  @Transactional
  public AuditCheckpoint generateCheckpoint(String tokenAddress, long blockNumber) {
    try {
      // Récupérer le hash du bloc
      EthBlock.Block block = web3j.ethGetBlockByNumber(
          new org.web3j.protocol.core.DefaultBlockParameterNumber(BigInteger.valueOf(blockNumber)),
          false
      ).send().getBlock();
      
      if (block == null) {
        throw new IllegalStateException("Block " + blockNumber + " not found");
      }

      String blockHash = block.getHash();

      // Récupérer le totalSupply du token à ce bloc
      BigInteger totalSupply = getTotalSupplyAtBlock(tokenAddress, blockNumber);

      // Récupérer le dernier checkpoint pour calculer la preuve incrémentale
      AuditCheckpoint previousCheckpoint = checkpointRepo
          .findTopByTokenAddressOrderByBlockNumberDesc(tokenAddress)
          .orElse(null);

      String previousHash = previousCheckpoint != null 
          ? previousCheckpoint.getProofHash() 
          : "0x0";

      // Calculer la preuve de hash (preuve mathématique incrémentale)
      String proofHash = calculateProofHash(
          tokenAddress,
          blockNumber,
          blockHash,
          totalSupply,
          previousHash
      );

      AuditCheckpoint checkpoint = new AuditCheckpoint();
      checkpoint.setCreatedAt(Instant.now());
      checkpoint.setBlockNumber(blockNumber);
      checkpoint.setBlockHash(blockHash);
      checkpoint.setTokenAddress(tokenAddress);
      checkpoint.setTotalSupply1e8(totalSupply.toString());
      checkpoint.setProofHash(proofHash);
      checkpoint.setPreviousCheckpointHash(previousHash);
      checkpoint.setMetadata(String.format(
          Locale.ROOT,
          "token=%s,block=%d,totalSupply=%s",
          tokenAddress,
          blockNumber,
          totalSupply
      ));

      AuditCheckpoint saved = checkpointRepo.save(checkpoint);

      auditLog.append(
          "AUDIT_CHECKPOINT_GENERATED",
          null,
          null,
          null,
          null,
          null,
          String.format("token=%s,block=%d,proofHash=%s", tokenAddress, blockNumber, proofHash)
      );

      return saved;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to generate checkpoint: " + e.getMessage(), e);
    }
  }

  /**
   * Vérifie l'intégrité d'un checkpoint en recalculant sa preuve.
   */
  public boolean verifyCheckpoint(AuditCheckpoint checkpoint) {
    try {
      String expectedHash = calculateProofHash(
          checkpoint.getTokenAddress(),
          checkpoint.getBlockNumber(),
          checkpoint.getBlockHash(),
          new BigInteger(checkpoint.getTotalSupply1e8()),
          checkpoint.getPreviousCheckpointHash()
      );
      return expectedHash.equals(checkpoint.getProofHash());
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Récupère le dernier checkpoint avant un bloc donné.
   */
  public AuditCheckpoint getLatestCheckpointBefore(String tokenAddress, long blockNumber) {
    return checkpointRepo
        .findByTokenAddressAndBlockNumberLessThanEqualOrderByBlockNumberDesc(tokenAddress, blockNumber)
        .stream()
        .findFirst()
        .orElse(null);
  }

  /**
   * Liste les checkpoints avec filtres optionnels.
   */
  public List<AuditCheckpoint> listCheckpoints(String tokenAddress, int limit) {
    if (tokenAddress != null && !tokenAddress.isBlank()) {
      return checkpointRepo.findTopByTokenAddressOrderByBlockNumberDesc(tokenAddress)
          .map(List::of)
          .orElse(List.of());
    }
    // Retourner les derniers checkpoints (tous tokens confondus)
    return checkpointRepo.findAll().stream()
        .sorted((a, b) -> Long.compare(b.getBlockNumber(), a.getBlockNumber()))
        .limit(limit)
        .toList();
  }

  /**
   * Récupère un checkpoint par son ID.
   */
  public Optional<AuditCheckpoint> findCheckpointById(String checkpointId) {
    return checkpointRepo.findById(checkpointId);
  }

  private BigInteger getTotalSupplyAtBlock(String tokenAddress, long blockNumber) {
    Function f = new Function(
        "totalSupply",
        List.of(),
        List.of(new TypeReference<Uint256>() {})
    );
    @SuppressWarnings("rawtypes")
    List<Type> out = evm.ethCallAtBlock(tokenAddress, f, BigInteger.valueOf(blockNumber));
    return EvmCallService.uint(out.get(0));
  }

  private String calculateProofHash(
      String tokenAddress,
      long blockNumber,
      String blockHash,
      BigInteger totalSupply,
      String previousHash
  ) {
    // Preuve mathématique incrémentale : hash(previousHash | tokenAddress | blockNumber | blockHash | totalSupply)
    String payload = String.join("|",
        "prev=" + (previousHash == null ? "0x0" : previousHash),
        "token=" + tokenAddress.toLowerCase(Locale.ROOT),
        "block=" + blockNumber,
        "blockHash=" + blockHash,
        "totalSupply=" + totalSupply.toString()
    );
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
      return "0x" + HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private long getLatestBlockNumber() {
    try {
      return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
          .send()
          .getBlock()
          .getNumber()
          .longValue();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get latest block: " + e.getMessage(), e);
    }
  }
}
