# Architecture Technique - Infrastructure Blockchain FAN-Capital

## Vue d'ensemble

L'architecture blockchain de FAN-Capital repose sur une **blockchain permissionnée** utilisant le protocole Ethereum, optimisée pour la tokenisation d'actifs réels (RWA) avec conformité réglementaire intégrée.

---

## 1. Choix Technologique

### Blockchain : Hyperledger Besu / Quorum

**Justification** :
- **Finalité immédiate** : Consensus IBFT 2.0 avec validation < 2 secondes
- **Gas-Free** : Suppression des frais de transaction pour l'utilisateur final
- **Gouvernance** : Contrôle total sur les validateurs (FAN-Capital + IB)
- **Conformité** : Infrastructure adaptée aux exigences réglementaires tunisiennes

### Consensus : IBFT 2.0 (Istanbul Byzantine Fault Tolerance)

**Caractéristiques** :
- **Temps de bloc** : < 2 secondes
- **Finalité** : Immédiate (pas de fork possible)
- **Tolérance aux pannes** : Jusqu'à (n-1)/3 nœuds byzantins
- **Validateurs** : Nœuds autorisés uniquement

---

## 2. Architecture du Réseau

### Topologie des Nœuds

```
┌─────────────────────────────────────────────────────────┐
│              Réseau Blockchain FAN-Capital                │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Validator 1   │  │ Validator 2   │  │ Validator 3   │  │
│  │ (FAN-Capital) │  │ (FAN-Capital) │  │ (IB)         │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                  │           │
│         └─────────────────┼──────────────────┘           │
│                           │                              │
│                  ┌────────▼────────┐                     │
│                  │  Consensus IBFT │                     │
│                  │      2.0        │                     │
│                  └────────┬────────┘                     │
│                           │                              │
│  ┌──────────────┐  ┌─────▼──────┐  ┌──────────────┐   │
│  │  Full Node    │  │ Audit Node │  │  Full Node    │   │
│  │ (Backend API) │  │   (CMF)    │  │ (Monitoring)  │   │
│  └──────────────┘  └────────────┘  └──────────────┘   │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

### Types de Nœuds

#### 1. **Nœuds Validateurs** (3 minimum)
- **Rôle** : Validation et création des blocs
- **Opérateurs** : FAN-Capital (2) + Intermédiaire en Bourse (1)
- **Exigences** :
  - Infrastructure haute disponibilité (99.9%)
  - Clés privées sécurisées (HSM recommandé)
  - Monitoring 24/7

#### 2. **Nœuds Complets (Full Nodes)**
- **Rôle** : Réplication complète de la blockchain
- **Utilisation** : Backend Spring Boot, monitoring, analytics
- **Exigences** : Stockage suffisant pour l'historique complet

#### 3. **Nœud d'Audit (CMF)**
- **Rôle** : Surveillance réglementaire en temps réel
- **Opérateur** : Conseil du Marché Financier (CMF)
- **Accès** : Lecture seule avec API dédiée
- **Fonctionnalités** :
  - Vérification parité 1:1 (tokens vs actifs réels)
  - Audit des transactions
  - Rapports de conformité

---

## 3. Architecture des Smart Contracts

### Structure Modulaire

```
Smart Contracts Layer
├── Core Layer
│   ├── CPEFToken.sol          # Token ERC-1404 de base
│   ├── CPEFEquityHigh.sol    # Token pour actions rendement élevé
│   └── CPEFEquityMedium.sol  # Token pour actions rendement moyen
│
├── Service Layer
│   ├── LiquidityPool.sol      # Piscine de liquidité
│   ├── PriceOracle.sol        # Oracle VNI
│   ├── KYCRegistry.sol        # Registre KYC
│   └── TaxVault.sol           # Vault fiscal
│
├── Credit Layer
│   ├── CreditLombard.sol      # Avance taux fixe
│   ├── CreditPGP.sol         # Avance participative
│   └── EscrowRegistry.sol     # Séquestre collatéraux
│
└── Governance Layer
    ├── Governance.sol          # Multi-sig
    └── CircuitBreaker.sol     # Protection d'urgence
```

### Interactions entre Contrats

```
┌─────────────┐
│   Frontend  │
│   Angular   │
└──────┬──────┘
       │ HTTP/REST
       ▼
┌─────────────┐
│   Backend   │
│ Spring Boot │
└──────┬──────┘
       │ Web3.js / Ethers.js
       ▼
┌─────────────────────────────────────┐
│      Blockchain Layer               │
│                                     │
│  ┌──────────┐    ┌──────────┐    │
│  │ CPEFToken │◄───│KYCRegistry│    │
│  └────┬──────┘    └──────────┘    │
│       │                            │
│       ▼                            │
│  ┌──────────────┐                 │
│  │LiquidityPool  │                 │
│  └──────┬───────┘                 │
│         │                          │
│         ▼                          │
│  ┌──────────────┐                 │
│  │ PriceOracle   │                 │
│  └──────────────┘                 │
│                                     │
│  ┌──────────────┐                 │
│  │ CreditLombard │                 │
│  └──────┬───────┘                 │
│         │                          │
│         ▼                          │
│  ┌──────────────┐                 │
│  │EscrowRegistry │                 │
│  └──────────────┘                 │
└─────────────────────────────────────┘
```

---

## 4. Flux de Données

### 4.1 Émission de Tokens (Mint)

```
1. Backend reçoit demande d'achat
2. Vérification KYC (KYCRegistry)
3. Vérification solde utilisateur
4. Calcul prix avec spread (LiquidityPool)
5. Appel mint() sur CPEFToken
6. Mise à jour PRM utilisateur
7. Émission événement Mint
8. Backend met à jour base de données
```

### 4.2 Rachat de Tokens (Burn)

```
1. Backend reçoit demande de rachat
2. Vérification solde tokens utilisateur
3. Calcul VNI actuelle (PriceOracle)
4. Calcul PRM utilisateur
5. Calcul plus-value et RAS
6. Appel burn() sur CPEFToken
7. Transfert TND vers Cash Wallet (net de fiscalité)
8. Transfert taxes vers TaxVault
9. Émission événement Burn
10. Backend met à jour base de données
```

### 4.3 Avance sur Titres

```
1. Utilisateur demande avance
2. Vérification niveau Premium
3. Calcul LTV selon type d'actif
4. Blocage collatéral (EscrowRegistry)
5. Émission crédit vers Credit Wallet
6. Suivi remboursements/coupons
7. Déblocage progressif ou liquidation
```

---

## 5. Sécurité et Gouvernance

### 5.1 Multi-Signature

**Configuration** : 3 signatures sur 5 signataires requis

**Actions protégées** :
- Modification adresses Oracle
- Changement paramètres financiers (spread, commissions)
- Modification taux d'intérêt avances
- Changement Hurdle Rate (PGP)
- Activation/désactivation Circuit Breaker

### 5.2 Circuit Breaker

**Seuil** : Ratio de réserve < 20%

**Actions** :
- Suspension automatique des rachats via piscine
- Maintien du marché P2P actif
- Notification aux administrateurs
- Requiert Multi-Sig pour réactivation

### 5.3 Oracle Guard

**Protection** : Rejet des mises à jour VNI avec écart > 10%

**Processus** :
1. Oracle propose nouvelle VNI
2. Contrat calcule écart avec valeur précédente
3. Si écart > 10% : Requiert validation Multi-Sig
4. Si écart ≤ 10% : Mise à jour automatique

---

## 6. Performance et Scalabilité

### Métriques Cibles

- **Temps de transaction** : < 2 secondes (finalité)
- **Débit** : 100+ transactions/seconde
- **Latence réseau** : < 100ms entre nœuds
- **Disponibilité** : 99.9% (8.76h downtime/an max)

### Optimisations

- **Gas-Free** : Transactions gratuites pour utilisateurs
- **Finalité immédiate** : Pas de confirmation nécessaire
- **Stockage optimisé** : Events pour historique léger
- **Indexation** : Backend indexe les événements pour requêtes rapides

---

## 7. Intégration avec le Backend

### Communication Web3

**Bibliothèque** : Web3.js ou Ethers.js

**Services Backend** :
- `BlockchainService` : Communication générale
- `TokenService` : Gestion tokens CPEF
- `LiquidityPoolService` : Gestion piscine
- `CreditService` : Gestion avances
- `OracleService` : Mise à jour VNI
- `EventService` : Écoute événements blockchain

### Gestion des Clés

- **Clés serveur** : Stockées dans HSM ou vault sécurisé
- **Rotation** : Changement périodique des clés
- **Backup** : Sauvegarde sécurisée des clés de récupération

---

## 8. Monitoring et Observabilité

### Métriques à Surveiller

- **Santé des nœuds** : Uptime, latence
- **Transactions** : Volume, taux d'échec
- **Gas** : Consommation (même si gratuit pour utilisateur)
- **Événements** : Mint, Burn, Transfer, Advance
- **Réserves** : Ratio de liquidité piscine
- **VNI** : Évolution et volatilité

### Outils Recommandés

- **Grafana** : Dashboards de monitoring
- **Prometheus** : Collecte métriques
- **ELK Stack** : Logs et recherche
- **Alerting** : Notifications critiques

---

## 9. Déploiement

### Environnements

1. **Development** : Réseau local (Ganache/Besu local)
2. **Staging** : Réseau de test avec nœuds de test
3. **Production** : Réseau principal avec nœuds validateurs

### Processus de Déploiement

1. **Compilation** : Solidity → Bytecode
2. **Tests** : Unitaires + Intégration
3. **Audit** : Revue de code (optionnel mais recommandé)
4. **Déploiement** : Migration via Truffle/Hardhat
5. **Vérification** : Contrats vérifiés sur explorer
6. **Configuration** : Initialisation paramètres
7. **Monitoring** : Activation surveillance

---

## 10. Évolutivité Future

### Améliorations Possibles

- **Sharding** : Si volume de transactions augmente
- **Sidechains** : Pour fonctionnalités spécifiques
- **Interopérabilité** : Ponts avec autres blockchains
- **Privacy** : Transactions privées (ZKP) si requis

---

*Document créé le 26 janvier 2026*
*Version 1.0*
