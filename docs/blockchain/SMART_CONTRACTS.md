# Smart Contracts - Spécifications Techniques

## Vue d'ensemble

Ce document consolide les spécifications techniques de tous les smart contracts de l'écosystème FAN-Capital, basé sur les documents d'analyse existants.

---

## 1. Architecture des Contrats

### Structure Modulaire

```
contracts/
├── interfaces/
│   ├── IERC1404.sol           # Interface standard ERC-1404
│   ├── IPriceOracle.sol       # Interface oracle VNI
│   └── IKYCRegistry.sol       # Interface registre KYC
│
├── core/
│   ├── CPEFToken.sol          # Token ERC-1404 de base
│   ├── CPEFEquityHigh.sol     # Token actions rendement élevé (hérite CPEFToken)
│   └── CPEFEquityMedium.sol   # Token actions rendement moyen (hérite CPEFToken)
│
├── services/
│   ├── LiquidityPool.sol      # Piscine de liquidité
│   ├── PriceOracle.sol        # Oracle VNI
│   ├── KYCRegistry.sol        # Registre KYC
│   ├── TaxVault.sol           # Vault fiscal
│   └── ReservationOption.sol  # Option de réservation sur stock CPEF
│
├── credit/
│   ├── CreditLombard.sol      # Avance taux fixe
│   ├── CreditPGP.sol         # Avance participative
│   └── EscrowRegistry.sol     # Séquestre collatéraux
│
└── governance/
    ├── Governance.sol          # Multi-sig
    └── CircuitBreaker.sol     # Protection d'urgence
```

---

## 2. Contrats Principaux

### 2.1 CPEFToken.sol (Base)

**Héritage** : ERC20, ERC1404, Ownable, ReentrancyGuard

**Fonctions Principales** :

```solidity
// Émission
function mint(address to, uint256 amount) external onlyLiquidityPool;

// Rachat
function burn(uint256 amount) external;

// Transfert avec restrictions
function transfer(address to, uint256 amount) public override returns (bool);

// ERC-1404
function detectTransferRestriction(address from, address to, uint256 amount) 
    public view returns (uint8);

// PRM
function getPRM(address user) public view returns (uint256);
function updatePRM(address user, uint256 newPRM) internal;

// VNI
function getVNI() public view returns (uint256);
function updateVNI(uint256 newVNI) external onlyOracle;
```

**Variables d'État** :
- `mapping(address => uint256) private prm` : Prix de revient moyen
- `uint256 private vni` : Valeur nette d'inventaire
- `IKYCRegistry public kycRegistry` : Référence registre KYC
- `ITaxVault public taxVault` : Référence vault fiscal

---

### 2.2 LiquidityPool.sol

**Rôle** : Gestion de la piscine de liquidité et exécution des ordres

**Fonctions Principales** :

```solidity
// Achat via piscine
function buyTokens(uint256 tndAmount, address user) external;

// Vente via piscine
function sellTokens(uint256 tokenAmount, address user) external;

// Calcul prix dynamique
function calculatePrice(bool isBuy, address user, uint8 level) 
    public view returns (uint256);

// Gestion réserves
function getReserveRatio() public view returns (uint256);
function checkCircuitBreaker() public view returns (bool);

// Spread dynamique
function calculateSpread(uint256 reserveRatio, uint256 volatility) 
    internal pure returns (uint256);
```

**Formule de Pricing** :
```
P_client = P_ref × (1 ± Spread_dyn)
Spread_dyn = S_base + α(σ) + β(1/R)

Où:
- S_base = 0.2% (marge fixe)
- α(σ) = prime de volatilité
- β(1/R) = prime selon ratio de réserve
```

**Circuit Breaker** :
- Seuil : Ratio réserve < 20%
- Action : Suspension rachats via piscine
- P2P : Reste actif

---

### 2.3 PriceOracle.sol

**Rôle** : Mise à jour sécurisée de la VNI

**Fonctions Principales** :

```solidity
// Mise à jour VNI
function updateVNI(uint256 newVNI) external onlyOracle;

// Vérification écart
function checkVNIChange(uint256 newVNI) internal view returns (bool);

// Historique
function getVNIHistory(uint256 index) public view returns (uint256, uint256);
```

**Oracle Guard** :
- Écart max : 10% sans Multi-Sig
- Si écart > 10% : Requiert validation Multi-Sig
- Historique : Stockage des valeurs précédentes

---

### 2.4 KYCRegistry.sol

**Rôle** : Gestion KYC et whitelisting

**Fonctions Principales** :

```solidity
// Ajout whitelist
function addToWhitelist(address user, uint8 level, bool isResident) 
    external onlyAdmin;

// Retrait whitelist
function removeFromWhitelist(address user) external onlyAdmin;

// Vérification
function isWhitelisted(address user) public view returns (bool);
function getUserLevel(address user) public view returns (uint8);
function checkTransferAllowed(address from, address to) 
    public view returns (bool);

// Statut fiscal
function isResident(address user) public view returns (bool);
function setUserResidency(address user, bool isResident) external onlyAdmin;
```

**Niveaux KYC** :
- **0** : Non whitelisté
- **1** : Green List (plafond 5000 TND, pas de P2P)
- **2** : White List (accès complet)

---

### 2.5 TaxVault.sol

**Rôle** : Collecte et gestion des taxes

**Fonctions Principales** :

```solidity
// Dépôt taxes
function depositTax(uint256 amount) external onlyTokenContract;

// Retrait vers fisc
function withdrawToFisc(address fiscAddress, uint256 amount) 
    external onlyAdmin;

// Solde
function getBalance() public view returns (uint256);
```

**Types de Taxes** :
- **RAS** : Retenue à la source (10% résidents, 15% non-résidents)
- **TVA** : Sur commissions (19%)

---

### 2.6 CreditLombard.sol

**Rôle** : Avance sur titres à taux fixe

**Fonctions Principales** :

```solidity
// Demande avance
function requestAdvance(
    uint256 tokenAmount,
    uint8 assetType,
    uint256 duration
) external;

// Calcul LTV
function calculateLTV(address user, uint8 assetType) 
    public view returns (uint256);

// Remboursement
function repayAdvance(uint256 advanceId) external payable;

// Libération collatéral
function releaseCollateral(uint256 advanceId) internal;

// Vérification marge
function checkMarginCall(address user) public view returns (bool);
```

**LTV par Type** :
- **EQUITY-HIGH** : 50%
- **EQUITY-MEDIUM** : 50%

**Taux** : 2% annuel fixe

---

### 2.7 CreditPGP.sol

**Rôle** : Avance participative (Partage Gains/Pertes)

**Fonctions Principales** :

```solidity
// Demande avance PGP
function requestAdvancePGP(
    uint256 tokenAmount,
    uint8 assetType,
    uint256 duration,
    uint8 level
) external;

// Calcul performance
function calculatePerformance(uint256 advanceId) 
    public view returns (int256);

// Partage gains
function distributeGains(uint256 advanceId) internal;

// Partage pertes
function distributeLosses(uint256 advanceId) internal;

// Coupons mensuels
function processMonthlyCoupon(uint256 advanceId) external;

// Déblocage progressif
function releaseCollateralProrata(uint256 advanceId, uint256 amount) 
    internal;
```

**Hurdle Rate** : 2.5%

**Ratios de Partage** :
- **SILVER** : 70% Client / 30% FAN
- **GOLD** : 80% Client / 20% FAN
- **DIAMOND** : 90% Client / 10% FAN

---

### 2.8 EscrowRegistry.sol

**Rôle** : Séquestre des collatéraux pour avances

**Fonctions Principales** :

```solidity
// Blocage collatéral
function lockCollateral(
    address user,
    uint256 tokenAmount,
    uint256 advanceId
) external onlyCreditContract;

// Déblocage total
function unlockCollateral(address user, uint256 advanceId) 
    external onlyCreditContract;

// Déblocage progressif
function unlockProrata(
    address user,
    uint256 advanceId,
    uint256 amount
) external onlyCreditContract;

// Liquidation
function liquidateCollateral(address user, uint256 advanceId) 
    external onlyCreditContract;

// Vérification
function isLocked(address user, uint256 amount) 
    public view returns (bool);
```

**Sécurité** :
- Tokens bloqués : `transfer` et `burn` désactivés
- Uniquement crédit contracts peuvent débloquer
- Protection contre double utilisation

---

### 2.9 Governance.sol

**Rôle** : Multi-signature et gouvernance

**Fonctions Principales** :

```solidity
// Proposer modification
function propose(
    address target,
    bytes calldata data,
    string memory description
) external returns (uint256);

// Voter
function vote(uint256 proposalId, bool support) external;

// Exécuter
function execute(uint256 proposalId) external;

// Annuler
function cancel(uint256 proposalId) external onlyAdmin;
```

**Configuration** :
- **Signataires** : 5 administrateurs
- **Quorum** : 3 signatures sur 5
- **Délai** : 24h minimum entre proposition et exécution

---

### 2.10 CircuitBreaker.sol

**Rôle** : Protection d'urgence

**Fonctions Principales** :

```solidity
// Activation pause
function pause() external onlyGovernance;

// Reprise
function unpause() external onlyGovernance;

// Vérification
function isPaused() public view returns (bool);

// Vérification circuit breaker piscine
function checkLiquidityPool() public view returns (bool);
```

**Conditions d'Activation** :
- Ratio réserve < 20%
- Détection anomalie
- Décision Multi-Sig

---

### 2.11 ReservationOption.sol

**Rôle** : Permettre à un utilisateur de **réserver** un prix \(K\) (VNI à \(t_0\)) pour acheter \(Q\) CPEF avant une échéance \(T\), en payant une avance \(A\).

**Idée** : C'est un mécanisme d'option “call” adossé à un **stock** de la plateforme (inventaire de la piscine), avec libération automatique à l'expiration.

**Dépendances** :
- `LiquidityPool` : vérification / verrouillage / libération du stock de CPEF
- `PriceOracle` : récupération de `VNI(t0)` pour fixer \(K\), et `VNI(T)` pour l'exercice (si nécessaire)
- `TaxVault` (optionnel) : si vous souhaitez taxer/TVA la prime \(A\)
- `TreasuryVault` (si présent côté design) : destination de l'avance \(A\) en cas de non-exercice / expiration

**Structure de données** :
- `struct Reservation { address user; address token; uint256 qty; uint256 strikePrice; uint256 expiry; uint256 advancePaid; bool exercised; bool cancelled; }`
- `mapping(uint256 => Reservation) public reservations;`

**Fonctions principales (spécification)** :

```solidity
// 1) Réserver Q jetons au prix K=VNI(t0) jusqu'à expiry, en payant l'avance A
function reserve(
    address token,        // CPEFEquityHigh ou CPEFEquityMedium
    uint256 qty,          // quantité en unités token (8 décimales)
    uint256 expiry,       // timestamp
    uint256 rBaseBps,     // ex: 500 = 5.00%
    uint256 sigmaAdjBps   // ajustement volatilité en bps
) external returns (uint256 reservationId);

// 2) Exercer: payer le reliquat R=(K*Q)-A et recevoir les jetons réservés
function exercise(uint256 reservationId) external;

// 3) Annuler (non-exercice volontaire) avant expiry: la plateforme conserve A
// et libère le stock
function cancel(uint256 reservationId) external;

// 4) Purge post-expiry: libère le stock, route A vers le trésor si pas exercée
function purge(uint256 reservationId) external;
```

**Calcul de l'avance (référence doc)** :
\[
A = (K \times Q) \times (r_{\text{base}} + \sigma_{\text{adj}})
\]
Avec \(K = VNI(t_0)\) (oracle) et \(Q\) la quantité réservée.

**Gestion des frais au moment de l’exercice (décision de design)** :
- **Option A (recommandée)** : la prime \(A\) est **“tout compris”** (service de réservation + mobilisation de stock + frais d’exécution), donc **pas de commission piscine** additionnelle à `exercise()`.
- **Option B** : appliquer les commissions “achat via piscine” (niveau Bronze→Platinum) au moment de `exercise()` en plus du reliquat \(R\).
  - Si Option B, il faut préciser si la commission est calculée sur le notionnel \(K \times Q\) ou sur \(R\).

**Contraintes / validations** :
- `expiry > block.timestamp`
- `qty > 0`
- `LiquidityPool` doit confirmer que l'inventaire est suffisant, puis **verrouiller** \(Q\) (réservation de stock).
- `exercise/cancel/purge` doivent libérer le stock une seule fois (éviter double spend).
- Événements : `Reserved`, `Exercised`, `Cancelled`, `Purged` (audit + backend sync).

---

## 3. Interactions entre Contrats

### Flux d'Achat

```
User → Backend → LiquidityPool.buyTokens()
    → KYCRegistry.checkTransferAllowed()
    → LiquidityPool.calculatePrice()
    → CPEFToken.mint()
    → KYCRegistry.getUserLevel() (pour commission)
    → TaxVault (si applicable)
```

### Flux de Rachat

```
User → Backend → LiquidityPool.sellTokens()
    → CPEFToken.getPRM()
    → PriceOracle.getVNI()
    → Calcul plus-value et RAS
    → CPEFToken.burn()
    → TaxVault.depositTax()
    → Transfert TND vers Cash Wallet
```

### Flux d'Avance

```
User → Backend → CreditLombard.requestAdvance()
    → EscrowRegistry.lockCollateral()
    → Calcul LTV
    → Émission crédit vers Credit Wallet
    → Suivi remboursements
    → EscrowRegistry.unlockCollateral()
```

---

### Flux de Réservation (Option)

```
User → Backend → ReservationOption.reserve()
    → PriceOracle.getVNI() pour fixer K=VNI(t0)
    → Calcul A
    → Débit Cash Wallet (selon design)
    → LiquidityPool.lockInventory(Q)
    → Event Reserved(reservationId, token, Q, K, expiry, A)

À T (ou avant) :
User → Backend → ReservationOption.exercise()
    → Paiement reliquat R=(K*Q)-A
    → LiquidityPool.deliverReserved(Q) / transfert à l'utilisateur
    → Event Exercised(...)

Sinon :
User/Backend/Keeper → ReservationOption.cancel() ou purge()
    → LiquidityPool.unlockInventory(Q)
    → TreasuryVault reçoit A
    → Event Cancelled/Purged(...)
```

## 4. Sécurité

### Bonnes Pratiques Implémentées

1. **ReentrancyGuard** : Protection contre réentrance
2. **SafeMath** : Protection overflow (Solidity 0.8+)
3. **Access Control** : Rôles et permissions
4. **Events** : Traçabilité complète
5. **Pause Mechanism** : Gel d'urgence
6. **Input Validation** : Vérification des paramètres

### Patterns Utilisés

- **Ownable** : Propriété des contrats
- **ReentrancyGuard** : Protection réentrance
- **Pausable** : Mécanisme de pause
- **Proxy Pattern** : Upgradeability (si nécessaire)

---

## 5. Tests

### Tests Requis

**Unitaires** :
- Chaque fonction isolée
- Cas limites
- Gestion erreurs

**Intégration** :
- Interactions entre contrats
- Flux complets
- Scénarios réels

**Sécurité** :
- Tests de pénétration
- Fuzzing
- Audit formel

---

## 6. Déploiement

### Ordre de Déploiement

1. **KYCRegistry** (base)
2. **TaxVault** (base)
3. **PriceOracle** (base)
4. **CPEFToken** (dépend de KYC, Oracle)
5. **LiquidityPool** (dépend de Token)
6. **EscrowRegistry** (base)
7. **CreditLombard** (dépend de Escrow, Token)
8. **CreditPGP** (dépend de Escrow, Token)
9. **Governance** (dépend de tous)
10. **CircuitBreaker** (dépend de tous)

### Initialisation

- Configuration paramètres
- Ajout administrateurs
- Configuration Multi-Sig
- Tests de bout en bout

---

*Document créé le 26 janvier 2026*
*Version 1.0 - Consolidation des spécifications*
