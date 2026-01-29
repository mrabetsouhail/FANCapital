# Intégration API - Backend Spring Boot

## Vue d'ensemble

Ce document décrit l'intégration entre le backend Spring Boot et la blockchain FAN-Capital, incluant les services, les patterns et les bonnes pratiques.

---

## 1. Architecture d'Intégration

### Schéma Global

```
┌─────────────┐
│   Frontend  │
│   Angular   │
└──────┬──────┘
       │ HTTP/REST
       ▼
┌─────────────────────────────────────┐
│      Backend Spring Boot            │
│                                     │
│  ┌──────────────┐  ┌─────────────┐ │
│  │  Controllers │  │  Services   │ │
│  └──────┬───────┘  └──────┬──────┘ │
│         │                 │         │
│  ┌──────▼─────────────────▼──────┐ │
│  │   Blockchain Service Layer    │ │
│  │   - Web3Service               │ │
│  │   - TokenService              │ │
│  │   - LiquidityPoolService      │ │
│  │   - CreditService             │ │
│  │   - OracleService             │ │
│  └──────┬────────────────────────┘ │
│         │ Web3.js / Ethers.js       │
└─────────┼──────────────────────────┘
          │
          ▼
┌─────────────────────────────────────┐
│      Blockchain Network            │
│      (Hyperledger Besu/Quorum)    │
└─────────────────────────────────────┘
```

---

## 2. Services Backend

### 2.1 Web3Service (Base)

**Rôle** : Communication générale avec la blockchain

**Configuration** :

```java
@Service
public class Web3Service {
    
    @Value("${blockchain.rpc.url}")
    private String rpcUrl;
    
    @Value("${blockchain.chain.id}")
    private Long chainId;
    
    private Web3j web3j;
    private Credentials credentials;
    
    @PostConstruct
    public void init() {
        web3j = Web3j.build(new HttpService(rpcUrl));
        credentials = Credentials.create(privateKey);
    }
    
    public Web3j getWeb3j() {
        return web3j;
    }
    
    public Credentials getCredentials() {
        return credentials;
    }
    
    public TransactionReceipt sendTransaction(Transaction transaction) {
        // Envoi transaction
    }
    
    public BigInteger getCurrentBlockNumber() {
        // Récupération numéro bloc
    }
}
```

**Propriétés** :
```properties
blockchain.rpc.url=http://localhost:8545
blockchain.chain.id=1337
blockchain.private.key=${BLOCKCHAIN_PRIVATE_KEY}
```

---

### 2.2 TokenService

**Rôle** : Gestion des tokens CPEF

**Fonctions Principales** :

```java
@Service
public class TokenService {
    
    @Autowired
    private Web3Service web3Service;
    
    @Value("${contracts.cpef.token.address}")
    private String tokenContractAddress;
    
    private CPEFToken contract;
    
    // Émission de tokens
    public TransactionReceipt mint(String to, BigDecimal amount) {
        // Appel mint() sur contrat
    }
    
    // Rachat de tokens
    public TransactionReceipt burn(String from, BigDecimal amount) {
        // Appel burn() sur contrat
    }
    
    // Récupération solde
    public BigDecimal getBalance(String address) {
        // Appel balanceOf()
    }
    
    // Récupération PRM
    public BigDecimal getPRM(String address) {
        // Appel getPRM()
    }
    
    // Récupération VNI
    public BigDecimal getVNI() {
        // Appel getVNI()
    }
    
    // Écoute événements
    @EventListener
    public void onMintEvent(MintEvent event) {
        // Traitement événement Mint
    }
}
```

---

### 2.3 LiquidityPoolService

**Rôle** : Gestion de la piscine de liquidité

**Fonctions Principales** :

```java
@Service
public class LiquidityPoolService {
    
    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private KYCRegistryService kycService;
    
    // Achat via piscine
    public BuyTokensResponse buyTokens(String userAddress, BigDecimal tndAmount) {
        // 1. Vérification KYC
        // 2. Calcul prix avec spread
        // 3. Appel buyTokens() sur contrat
        // 4. Mise à jour base de données
    }
    
    // Vente via piscine
    public SellTokensResponse sellTokens(String userAddress, BigDecimal tokenAmount) {
        // 1. Vérification solde
        // 2. Calcul prix
        // 3. Appel sellTokens() sur contrat
        // 4. Mise à jour base de données
    }
    
    // Calcul prix
    public BigDecimal calculatePrice(boolean isBuy, String userAddress, UserLevel level) {
        // Appel calculatePrice() sur contrat
    }
    
    // Ratio de réserve
    public BigDecimal getReserveRatio() {
        // Appel getReserveRatio()
    }
}
```

---

### 2.4 CreditService

**Rôle** : Gestion des avances sur titres

**Fonctions Principales** :

```java
@Service
public class CreditService {
    
    @Autowired
    private Web3Service web3Service;
    
    // Demande avance (Taux Fixe)
    public AdvanceResponse requestAdvance(
        String userAddress,
        BigDecimal tokenAmount,
        AssetType assetType,
        int durationMonths
    ) {
        // 1. Vérification niveau Premium
        // 2. Calcul LTV
        // 3. Blocage collatéral
        // 4. Émission crédit
    }
    
    // Demande avance PGP
    public AdvancePGPResponse requestAdvancePGP(
        String userAddress,
        BigDecimal tokenAmount,
        AssetType assetType,
        int durationMonths,
        UserLevel level
    ) {
        // Demande avance participative
    }
    
    // Remboursement
    public TransactionReceipt repayAdvance(Long advanceId, BigDecimal amount) {
        // Remboursement cash
    }
    
    // Vérification marge
    public boolean checkMarginCall(String userAddress) {
        // Vérification appel de marge
    }
}
```

---

### 2.5 OracleService

**Rôle** : Mise à jour de la VNI

**Fonctions Principales** :

```java
@Service
public class OracleService {
    
    @Autowired
    private Web3Service web3Service;
    
    @Scheduled(cron = "0 0 18 * * *") // Quotidien à 18h
    public void updateVNI() {
        // 1. Récupération VNI depuis IB
        // 2. Vérification écart (Oracle Guard)
        // 3. Mise à jour sur blockchain
    }
    
    // Mise à jour manuelle (avec Multi-Sig si écart > 10%)
    public TransactionReceipt updateVNIManual(BigDecimal newVNI) {
        // Mise à jour avec validation
    }
}
```

---

### 2.6 EventService

**Rôle** : Écoute des événements blockchain

**Fonctions Principales** :

```java
@Service
public class EventService {
    
    @Autowired
    private Web3Service web3Service;
    
    @PostConstruct
    public void startListening() {
        // Démarrage écoute événements
        listenToMintEvents();
        listenToBurnEvents();
        listenToTransferEvents();
        listenToAdvanceEvents();
    }
    
    private void listenToMintEvents() {
        // Écoute événements Mint
        // Mise à jour base de données
    }
    
    private void listenToBurnEvents() {
        // Écoute événements Burn
        // Mise à jour base de données
    }
}
```

---

## 3. Contrats Solidity - Génération Java

### Utilisation de Web3j Code Generation

**Génération des wrappers Java** :

```bash
web3j generate solidity \
  --javaTypes \
  --packageName=com.fancapital.contracts \
  --outputDir=src/main/java \
  contracts/CPEFToken.sol
```

**Utilisation** :

```java
@Autowired
private CPEFToken contract;

public BigDecimal getBalance(String address) {
    return contract.balanceOf(address).send();
}
```

---

## 4. Gestion des Transactions

### 4.1 Signature des Transactions

**Côté Serveur** :
- Clés privées stockées dans HSM ou vault sécurisé
- Rotation périodique des clés
- Backup sécurisé

**Pattern** :

```java
public TransactionReceipt sendTransaction(Function function) {
    String encodedFunction = FunctionEncoder.encode(function);
    
    Transaction transaction = Transaction.createFunctionCallTransaction(
        credentials.getAddress(),
        null,
        GasPrice.DEFAULT,
        GasLimit.DEFAULT,
        contractAddress,
        encodedFunction
    );
    
    EthSendTransaction response = web3Service.getWeb3j()
        .ethSendTransaction(transaction)
        .send();
    
    return waitForTransactionReceipt(response.getTransactionHash());
}
```

### 4.2 Gestion du Gas

**Note** : Même si gas-free pour utilisateurs, le backend doit gérer le gas pour les transactions.

**Configuration** :

```java
@Value("${blockchain.gas.price}")
private BigInteger gasPrice;

@Value("${blockchain.gas.limit}")
private BigInteger gasLimit;
```

---

## 5. Gestion des Erreurs

### Types d'Erreurs

**Blockchain Errors** :
- Transaction reverted
- Out of gas
- Nonce too low
- Insufficient funds

**Business Errors** :
- KYC non validé
- Plafond dépassé
- Solde insuffisant

**Gestion** :

```java
@ExceptionHandler(TransactionException.class)
public ResponseEntity<ErrorResponse> handleTransactionException(
    TransactionException e
) {
    // Log erreur
    // Retour réponse appropriée
}
```

---

## 6. Synchronisation Base de Données

### Pattern Event Sourcing

**Principe** : La blockchain est la source de vérité, la base de données est un cache.

**Processus** :
1. Événement émis sur blockchain
2. EventService écoute l'événement
3. Mise à jour base de données
4. Notification frontend (WebSocket)

**Exemple** :

```java
@EventListener
public void onMintEvent(MintEvent event) {
    // Mise à jour solde utilisateur
    userRepository.updateBalance(
        event.getTo(),
        event.getAmount()
    );
    
    // Notification frontend
    webSocketService.notifyUser(
        event.getTo(),
        "Tokens minted: " + event.getAmount()
    );
}
```

---

## 7. Configuration

### application.yml

```yaml
blockchain:
  rpc:
    url: ${BLOCKCHAIN_RPC_URL:http://localhost:8545}
  chain:
    id: ${BLOCKCHAIN_CHAIN_ID:1337}
  private:
    key: ${BLOCKCHAIN_PRIVATE_KEY}
  gas:
    price: ${BLOCKCHAIN_GAS_PRICE:20000000000}
    limit: ${BLOCKCHAIN_GAS_LIMIT:1000000}
  
contracts:
  cpef:
    token:
      address: ${CPEF_TOKEN_ADDRESS}
  liquidity:
    pool:
      address: ${LIQUIDITY_POOL_ADDRESS}
  credit:
    lombard:
      address: ${CREDIT_LOMBARD_ADDRESS}
    pgp:
      address: ${CREDIT_PGP_ADDRESS}
```

---

## 8. Tests

### Tests d'Intégration

**Utilisation de Ganache/Besu local** :

```java
@SpringBootTest
@ActiveProfiles("test")
public class TokenServiceIntegrationTest {
    
    @Autowired
    private TokenService tokenService;
    
    @Test
    public void testMint() {
        // Test émission tokens
    }
}
```

---

## 9. Monitoring

### Métriques à Surveiller

- **Transactions** : Volume, taux de succès
- **Latence** : Temps de réponse blockchain
- **Erreurs** : Taux d'échec transactions
- **Événements** : Nombre d'événements traités

### Logging

```java
@Slf4j
@Service
public class TokenService {
    
    public TransactionReceipt mint(String to, BigDecimal amount) {
        log.info("Minting {} tokens to {}", amount, to);
        // ...
        log.info("Mint successful. Tx: {}", receipt.getTransactionHash());
    }
}
```

---

## 10. Bonnes Pratiques

### Sécurité

- **Clés privées** : Jamais en clair dans le code
- **HTTPS** : Communication chiffrée
- **Rate Limiting** : Protection API
- **Validation** : Validation des inputs

### Performance

- **Cache** : Mise en cache des données fréquentes
- **Async** : Traitement asynchrone des événements
- **Connection Pooling** : Pool de connexions Web3j
- **Batch Requests** : Regroupement requêtes si possible

---

*Document créé le 26 janvier 2026*
*Version 1.0*
