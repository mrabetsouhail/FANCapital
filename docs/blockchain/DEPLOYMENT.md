# Déploiement et Configuration - Blockchain FAN-Capital

## Vue d'ensemble

Ce document décrit le processus de déploiement de l'infrastructure blockchain FAN-Capital, incluant les smart contracts, les nœuds et la configuration.

---

## 1. Prérequis

### Infrastructure

- **Serveurs** : 3+ nœuds validateurs (haute disponibilité)
- **Stockage** : 500GB+ par nœud (croissance)
- **Réseau** : Latence < 100ms entre nœuds
- **Sécurité** : HSM pour clés privées (recommandé)

### Logiciels

- **Hyperledger Besu** : Version 23.x ou supérieure
- **Java** : JDK 17+
- **Node.js** : 18+ (pour outils de déploiement)
- **Truffle/Hardhat** : Framework de déploiement
- **Docker** : Pour containerisation (optionnel)

---

## 2. Environnements

### 2.1 Development

**Configuration** :
- Réseau local (Ganache ou Besu local)
- Contrats non vérifiés
- Données de test

**Déploiement** :
```bash
truffle migrate --network development
```

### 2.2 Staging

**Configuration** :
- Réseau de test avec nœuds de test
- Contrats vérifiés
- Données réalistes

**Déploiement** :
```bash
truffle migrate --network staging
```

### 2.3 Production

**Configuration** :
- Réseau principal
- Contrats audités et vérifiés
- Données réelles

**Déploiement** :
```bash
truffle migrate --network production
```

---

## 3. Configuration du Réseau

### 3.1 Genesis Block

**Fichier genesis.json** :

```json
{
  "config": {
    "chainId": 2026,
    "istanbul": {
      "epoch": 30000,
      "blockperiodseconds": 2,
      "requesttimeoutseconds": 10
    }
  },
  "nonce": "0x0",
  "timestamp": "0x0",
  "extraData": "0x...",
  "gasLimit": "0x1C9C380",
  "difficulty": "0x1",
  "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
  "alloc": {}
}
```

### 3.2 Configuration Besu

**Fichier config.toml** :

```toml
[Eth]
network-id = 2026

[Eth.Network]
bootnodes = ["enode://..."]

[Eth.Protocols]
[Eth.Protocols.Istanbul]
blockperiodseconds = 2
epochlength = 30000
requesttimeoutseconds = 10

[Consensus]
algorithm = "ibft2"
```

---

## 4. Déploiement des Smart Contracts

### 4.1 Ordre de Déploiement

**Séquence** :

1. **KYCRegistry** (base, pas de dépendances)
2. **TaxVault** (base)
3. **PriceOracle** (base)
4. **CPEFToken** (dépend de KYC, Oracle)
5. **LiquidityPool** (dépend de Token)
6. **EscrowRegistry** (base)
7. **CreditLombard** (dépend de Escrow, Token)
8. **CreditPGP** (dépend de Escrow, Token)
9. **Governance** (dépend de tous)
10. **CircuitBreaker** (dépend de tous)

### 4.2 Script de Migration (Truffle)

**migrations/1_deploy_kyc.js** :

```javascript
const KYCRegistry = artifacts.require("KYCRegistry");

module.exports = async function (deployer, network, accounts) {
  await deployer.deploy(KYCRegistry);
  const kycRegistry = await KYCRegistry.deployed();
  
  console.log("KYCRegistry deployed at:", kycRegistry.address);
  
  // Initialisation
  await kycRegistry.initialize(accounts[0]); // Admin
};
```

**migrations/2_deploy_token.js** :

```javascript
const CPEFToken = artifacts.require("CPEFToken");
const KYCRegistry = artifacts.require("KYCRegistry");

module.exports = async function (deployer, network, accounts) {
  const kycRegistry = await KYCRegistry.deployed();
  
  await deployer.deploy(
    CPEFToken,
    kycRegistry.address,
    "CPEF Token",
    "CPEF"
  );
  
  const token = await CPEFToken.deployed();
  console.log("CPEFToken deployed at:", token.address);
};
```

### 4.3 Script de Migration (Hardhat)

**scripts/deploy.js** :

```javascript
async function main() {
  const [deployer] = await ethers.getSigners();
  
  console.log("Deploying contracts with account:", deployer.address);
  
  // Déploiement KYCRegistry
  const KYCRegistry = await ethers.getContractFactory("KYCRegistry");
  const kycRegistry = await KYCRegistry.deploy();
  await kycRegistry.deployed();
  console.log("KYCRegistry deployed to:", kycRegistry.address);
  
  // Déploiement CPEFToken
  const CPEFToken = await ethers.getContractFactory("CPEFToken");
  const token = await CPEFToken.deploy(
    kycRegistry.address,
    "CPEF Token",
    "CPEF"
  );
  await token.deployed();
  console.log("CPEFToken deployed to:", token.address);
}
```

---

## 5. Initialisation Post-Déploiement

### 5.1 Configuration des Contrats

**Script d'initialisation** :

```javascript
async function initializeContracts() {
  // Configuration KYCRegistry
  await kycRegistry.addAdmin(adminAddress);
  
  // Configuration PriceOracle
  await priceOracle.setOracle(oracleAddress);
  await priceOracle.updateVNI(initialVNI);
  
  // Configuration LiquidityPool
  await liquidityPool.setTokenContract(tokenAddress);
  await liquidityPool.setPriceOracle(priceOracleAddress);
  
  // Configuration Governance
  await governance.addSigner(signer1);
  await governance.addSigner(signer2);
  await governance.addSigner(signer3);
  await governance.addSigner(signer4);
  await governance.addSigner(signer5);
}
```

### 5.2 Configuration Multi-Sig

**Étape 1** : Ajout des signataires

```javascript
await governance.addSigner(signer1);
await governance.addSigner(signer2);
// ... jusqu'à 5 signataires
```

**Étape 2** : Configuration quorum (3/5)

```javascript
await governance.setQuorum(3);
```

### 5.3 Configuration Paramètres Financiers

**Commissions** :

```javascript
await liquidityPool.setCommission(UserLevel.BRONZE, 100); // 1.00%
await liquidityPool.setCommission(UserLevel.SILVER, 95);  // 0.95%
await liquidityPool.setCommission(UserLevel.GOLD, 90);    // 0.90%
await liquidityPool.setCommission(UserLevel.DIAMOND, 85); // 0.85%
await liquidityPool.setCommission(UserLevel.PLATINUM, 80); // 0.80%
```

**Taux d'intérêt** :

```javascript
await creditLombard.setInterestRate(200); // 2.00%
```

**Hurdle Rate PGP** :

```javascript
await creditPGP.setHurdleRate(250); // 2.50%
```

---

## 6. Vérification Post-Déploiement

### 6.1 Vérification des Adresses

**Checklist** :
- ✅ Tous les contrats déployés
- ✅ Adresses enregistrées dans configuration
- ✅ Adresses partagées avec équipe

### 6.2 Tests de Fonctionnement

**Tests à effectuer** :

1. **Mint** : Émission de tokens de test
2. **Burn** : Rachat de tokens de test
3. **Transfer** : Transfert P2P
4. **KYC** : Ajout utilisateur test
5. **Piscine** : Achat/vente via piscine
6. **Oracle** : Mise à jour VNI
7. **Avance** : Demande avance test

### 6.3 Vérification Sécurité

**Audit** :
- ✅ Contrats vérifiés sur explorer
- ✅ Code source publié (si requis)
- ✅ Tests de sécurité effectués
- ✅ Review code par équipe

---

## 7. Configuration Backend

### 7.1 Variables d'Environnement

**Fichier .env** :

```env
BLOCKCHAIN_RPC_URL=http://localhost:8545
BLOCKCHAIN_CHAIN_ID=2026
BLOCKCHAIN_PRIVATE_KEY=0x...

CPEF_TOKEN_ADDRESS=0x...
LIQUIDITY_POOL_ADDRESS=0x...
CREDIT_LOMBARD_ADDRESS=0x...
CREDIT_PGP_ADDRESS=0x...
KYC_REGISTRY_ADDRESS=0x...
PRICE_ORACLE_ADDRESS=0x...
TAX_VAULT_ADDRESS=0x...
```

### 7.2 Configuration Spring Boot

**application.yml** :

```yaml
blockchain:
  rpc:
    url: ${BLOCKCHAIN_RPC_URL}
  chain:
    id: ${BLOCKCHAIN_CHAIN_ID}
  contracts:
    cpef:
      token: ${CPEF_TOKEN_ADDRESS}
    liquidity:
      pool: ${LIQUIDITY_POOL_ADDRESS}
```

---

## 8. Monitoring Post-Déploiement

### 8.1 Métriques à Surveiller

- **Santé des nœuds** : Uptime, latence
- **Transactions** : Volume, taux de succès
- **Événements** : Mint, Burn, Transfer
- **Réserves** : Ratio de liquidité
- **VNI** : Évolution

### 8.2 Alertes

**Alertes critiques** :
- Nœud down
- Ratio réserve < 20%
- Écart parité > 1%
- Transaction failed > 5%

---

## 9. Maintenance

### 9.1 Mises à Jour

**Processus** :
1. Tests en staging
2. Audit (si changement majeur)
3. Déploiement production
4. Vérification post-déploiement

### 9.2 Backup

**Fréquence** :
- **Quotidien** : État des contrats
- **Hebdomadaire** : Configuration complète
- **Mensuel** : Snapshot blockchain

**Stockage** : Sauvegarde sécurisée (HSM si clés)

---

## 10. Rollback

### 10.1 Procédure de Rollback

**Si problème détecté** :

1. **Pause** : Activation Circuit Breaker
2. **Analyse** : Identification du problème
3. **Correction** : Fix ou rollback
4. **Tests** : Vérification en staging
5. **Reprise** : Redémarrage contrôlé

### 10.2 Plan de Continuité

**Scénarios** :
- Nœud validateur down
- Contrat compromis
- Attaque réseau
- Perte de clés

**Procédures** : Documentées et testées

---

## 11. Checklist Déploiement Production

### Pré-Déploiement

- [ ] Tests complets en staging
- [ ] Audit de sécurité effectué
- [ ] Documentation à jour
- [ ] Plan de rollback préparé
- [ ] Équipe formée

### Déploiement

- [ ] Réseau configuré
- [ ] Contrats déployés
- [ ] Contrats initialisés
- [ ] Configuration backend mise à jour
- [ ] Tests de vérification passés

### Post-Déploiement

- [ ] Monitoring actif
- [ ] Alertes configurées
- [ ] Documentation mise à jour
- [ ] Équipe notifiée
- [ ] Backup effectué

---

*Document créé le 26 janvier 2026*
*Version 1.0*
