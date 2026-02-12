# Rôle du Multi-Sig dans FAN Capital

## Vue d'Ensemble

Le **MultiSigCouncil** est le **cœur de la gouvernance** de la plateforme FAN Capital. Selon le **Dossier de Sécurité Institutionnelle v2.0**, il utilise un protocole **3/5** (3 signatures requises parmi 5 signataires) pour éliminer tout point de défaillance unique (SPoF) et garantir une gouvernance distribuée.

---

## 1. Architecture de Sécurité : Les 7 Clés

Le Multi-Sig fait partie de l'architecture de **micro-segmentation cryptographique** de FAN Capital, qui divise les privilèges en **7 clés distinctes** :

| Clé | Rôle | Fonction | Type de Stockage |
|-----|------|----------|------------------|
| **Governance (Multi-Sig)** | `DEFAULT_ADMIN_ROLE` | Modification des paramètres vitaux | Multi-sig 3/5 |
| **Mint Key** | `MINTER_ROLE` | Création de nouveaux titres (Atlas/Didon) | HSM Isolé |
| **Burn Key** | `BURNER_ROLE` | Destruction des jetons lors des rachats | HSM Isolé |
| **Oracle Key** | `ORACLE_ROLE` | Mise à jour des cours BVMT | API Backend |
| **Compliance** | `KYC_VALIDATOR_ROLE` | Gestion du Whitelisting (LBA/FT) | Database/Auth |
| **Panic Key** | `PANIC_KEY_ROLE` | Arrêt immédiat (Circuit Breaker) | Cold Storage |
| **Audit Key** | Read-Only | Accès aux registres pour régulateur | Read-Only Key |

**Impact de la micro-segmentation :** Une vulnérabilité sur le service Oracle ne peut **en aucun cas** compromettre les fonctions de Minting ou les avoirs des utilisateurs.

---

## 2. Rôle du Multi-Sig : `DEFAULT_ADMIN_ROLE`

### 2.1 Contrôle Administratif sur Tous les Contrats

Le `MultiSigCouncil` détient le rôle **`DEFAULT_ADMIN_ROLE`** sur **tous les smart contracts** de la plateforme :

#### **Contrats Core**
- ✅ **`CPEFToken`** (Atlas, Didon) : Configuration des pools, oracle, KYC
- ✅ **`LiquidityPool`** : Paramètres de trading, frais, spreads
- ✅ **`P2PExchange`** : Configuration des échanges P2P

#### **Contrats de Services**
- ✅ **`CashTokenTND`** : Gestion des rôles (MINTER, BURNER)
- ✅ **`PriceOracle`** : Configuration des sources de prix
- ✅ **`KYCRegistry`** : Gestion des validateurs KYC
- ✅ **`InvestorRegistry`** : Configuration des investisseurs
- ✅ **`TaxVault`** : Gestion de la trésorerie fiscale
- ✅ **`CircuitBreaker`** : Configuration des seuils de pause

#### **Contrats de Crédit**
- ✅ **`CreditModelA`** / **`CreditModelBPGP`** : Configuration des modèles
- ✅ **`EscrowRegistry`** : Gestion des séquestres
- ✅ **`ReservationOption`** : Configuration des options de réservation

#### **Contrats de Gouvernance**
- ✅ **`CPEFFactory`** : Déploiement de nouveaux fonds
- ✅ **`MultiSigCouncil`** lui-même (auto-gouvernance)

### 2.2 Actions Autorisées par le Multi-Sig

Le Multi-Sig peut effectuer **toutes les actions administratives** via le rôle `DEFAULT_ADMIN_ROLE` et `GOVERNANCE_ROLE` :

#### **Configuration des Paramètres**
- Modifier les adresses des contrats (oracle, pool, KYC, etc.)
- Changer les frais de trading (`setPoolFeeBps`, `setP2PFeeBps`)
- Ajuster les spreads (`setBaseSpreadBps`, `setSpreadParams`)
- Configurer les seuils du Circuit Breaker (`setThresholdBps`)

#### **Gestion des Rôles**
- Accorder/révoquer les rôles (`MINTER_ROLE`, `BURNER_ROLE`, `ORACLE_ROLE`, etc.)
- Ajouter/retirer des validateurs KYC
- Autoriser des appelants pour `TaxVault` et `EscrowRegistry`

#### **Actions Critiques**
- **Déployer de nouveaux fonds** via `CPEFFactory.deployFund()`
- **Forcer la mise à jour des prix** via `PriceOracle.forceUpdateVNI()` (en cas d'urgence)
- **Reprendre les opérations** après un Circuit Breaker (`CircuitBreaker.resumeAll()`)
- **Retirer les fonds fiscaux** vers le compte officiel (`TaxVault.withdrawToFisc()`)

---

## 3. Répartition des Signataires (3/5)

Selon le **Dossier de Sécurité v2.0**, les **5 signataires** sont répartis pour prévenir toute collusion interne :

### 3.1 Composition du Council

1. **Direction Technique (CTO)**
   - Responsable de l'intégrité logicielle
   - Valide les modifications techniques des contrats

2. **Responsable de la Conformité (Compliance)**
   - Garant du respect LBA/FT (Lutte contre le Blanchiment / Financement du Terrorisme)
   - Valide les actions conformes à la réglementation CMF

3. **Membre du Conseil d'Administration**
   - Représentant des actionnaires
   - Valide les décisions stratégiques

4. **Tiers de Confiance / Séquestre**
   - Cabinet d'audit externe
   - Garantit l'indépendance et la transparence

5. **Représentant de l'Intermédiaire en Bourse**
   - Garant de l'adossement aux actifs réels
   - Valide la conformité avec les actifs sous-jacents

### 3.2 Protocole 3/5

- **Seuil requis :** 3 signatures sur 5
- **Avantage :** Empêche la collusion (même si 2 signataires sont compromis, il faut encore 1 signature valide)
- **Résilience :** Permet la continuité même si 2 signataires sont indisponibles

---

## 4. Cas d'Usage Concrets dans FAN Capital

### 4.1 Cas d'Usage 1 : Modifier les Frais de Trading

**Contexte :** Le conseil d'administration décide d'augmenter les frais de trading de 1% à 1.5%.

**Processus Multi-Sig :**
1. **Soumission :** Le CTO soumet une proposition pour appeler `LiquidityPool.setPoolFeeBps(1, 150)` (150 bps = 1.5%).
2. **Confirmation :** 
   - Responsable Compliance confirme (1/3)
   - Membre CA confirme (2/3)
   - Représentant Intermédiaire confirme (3/3)
3. **Exécution :** Une fois le seuil atteint, n'importe quel signataire exécute la transaction.
4. **Résultat :** Les frais sont mis à jour sur tous les pools.

### 4.2 Cas d'Usage 2 : Déployer un Nouveau Fonds

**Contexte :** FAN Capital souhaite créer un nouveau fonds "CPEF Gamma".

**Processus Multi-Sig :**
1. **Soumission :** Le CTO soumet une proposition pour appeler `CPEFFactory.deployFund("CPEF Gamma", "GAMMA")`.
2. **Confirmation :**
   - Responsable Compliance valide la conformité réglementaire (1/3)
   - Membre CA valide la stratégie (2/3)
   - Tiers de Confiance valide l'audit (3/3)
3. **Exécution :** La transaction est exécutée.
4. **Résultat :** Un nouveau contrat `CPEFToken` est déployé avec son `LiquidityPool` associé.

### 4.3 Cas d'Usage 3 : Reprendre les Opérations après Circuit Breaker

**Contexte :** Le Circuit Breaker a été activé (par la Panic Key ou automatiquement). Le conseil décide de reprendre les opérations.

**Processus Multi-Sig :**
1. **Soumission :** Le CTO soumet une proposition pour appeler `CircuitBreaker.resumeAll()`.
2. **Confirmation :**
   - Responsable Compliance valide que les risques sont maîtrisés (1/3)
   - Membre CA valide la décision stratégique (2/3)
   - Représentant Intermédiaire valide l'adossement (3/3)
3. **Exécution :** La transaction est exécutée.
4. **Résultat :** Toutes les opérations reprennent normalement.

### 4.4 Cas d'Usage 4 : Retirer les Fonds Fiscaux

**Contexte :** Le `TaxVault` a accumulé 100,000 TND de frais. Le conseil décide de les retirer vers le compte fiscal officiel.

**Processus Multi-Sig :**
1. **Soumission :** Le Responsable Compliance soumet une proposition pour appeler `TaxVault.withdrawToFisc(10000000000000)` (100k TND avec 8 décimales).
2. **Confirmation :**
   - Membre CA valide le montant (1/3)
   - Tiers de Confiance valide l'audit (2/3)
   - CTO valide la transaction technique (3/3)
3. **Exécution :** La transaction est exécutée.
4. **Résultat :** 100,000 TND sont transférés vers l'adresse fiscale configurée.

### 4.5 Cas d'Usage 5 : Forcer la Mise à Jour des Prix (Urgence)

**Contexte :** L'oracle automatique est en panne. Les prix doivent être mis à jour manuellement pour éviter des écarts.

**Processus Multi-Sig :**
1. **Soumission :** Le CTO soumet une proposition pour appeler `PriceOracle.forceUpdateVNI(tokenAddress, newVNI)`.
2. **Confirmation :**
   - Représentant Intermédiaire valide les prix depuis la BVMT (1/3)
   - Responsable Compliance valide la conformité (2/3)
   - Membre CA valide la décision (3/3)
3. **Exécution :** La transaction est exécutée.
4. **Résultat :** Les prix sont mis à jour manuellement.

---

## 5. Séparation des Responsabilités

### 5.1 Multi-Sig vs Autres Clés

Le Multi-Sig **ne peut pas** :
- ❌ **Minter des tokens** (réservé à la **Mint Key** avec `MINTER_ROLE`)
- ❌ **Brûler des tokens** (réservé à la **Burn Key** avec `BURNER_ROLE`)
- ❌ **Mettre à jour les prix automatiquement** (réservé à l'**Oracle Key** avec `ORACLE_ROLE`)
- ❌ **Valider le KYC** (réservé à la **Compliance Key** avec `KYC_VALIDATOR_ROLE`)
- ❌ **Activer le Circuit Breaker en urgence** (réservé à la **Panic Key** avec `PANIC_KEY_ROLE`)

Le Multi-Sig **peut** :
- ✅ **Configurer** qui peut minter/burn (gérer les rôles)
- ✅ **Configurer** l'adresse de l'oracle
- ✅ **Configurer** les validateurs KYC
- ✅ **Reprendre** les opérations après un Circuit Breaker

### 5.2 Principe de Moindre Privilège

Chaque clé a un **périmètre limité** :
- **Mint Key** : Uniquement `mint()` sur `CashTokenTND`
- **Burn Key** : Uniquement `burn()` sur `CashTokenTND`
- **Oracle Key** : Uniquement `updateVNI()` sur `PriceOracle`
- **Compliance Key** : Uniquement `validateKYC()` sur `KYCRegistry`
- **Panic Key** : Uniquement `pauseAll()` sur `CircuitBreaker`
- **Multi-Sig** : Toutes les actions administratives (mais pas les opérations quotidiennes)

---

## 6. Conformité Réglementaire (CMF)

Selon le **Dossier de Sécurité v2.0**, le Multi-Sig répond aux exigences du Conseil du Marché Financier (CMF) :

| Exigence CMF | Spécification FAN Capital |
|--------------|---------------------------|
| **Non-Collusion** | Gouvernance Multi-sig 3/5 distribuée (5 signataires indépendants) |
| **Traçabilité** | Toutes les propositions et confirmations sont enregistrées sur la blockchain (Hyperledger Besu) |
| **Souveraineté** | Modèle WaaS en Circuit Fermé (pas de portefeuilles externes) |
| **Intégrité LBA/FT** | Whitelisting On-Chain : `isWhitelisted(address) == true` |
| **Résilience** | Géo-redondance Besu + MariaDB (RPO = 0) |

---

## 7. Workflow Opérationnel

### 7.1 Cycle de Vie d'une Proposition

```
1. SOUMISSION
   └─> Un signataire soumet une proposition via l'interface Multi-Sig
       └─> La proposition apparaît dans "Propositions en Attente"
           └─> Statut : "0 / 3 confirmations"

2. CONFIRMATION
   └─> Les autres signataires confirment un par un
       └─> "1 / 3" → "2 / 3" → "3 / 3"
           └─> Badge vert "Prête à exécuter" apparaît

3. EXÉCUTION
   └─> N'importe quel signataire exécute la transaction
       └─> La transaction est envoyée à la blockchain
           └─> La proposition passe dans "Transactions Exécutées"
```

### 7.2 Délais et Notifications

- **Temps de confirmation :** Chaque signataire peut confirmer à tout moment (pas de délai imposé)
- **Temps d'exécution :** Une fois le seuil atteint, l'exécution peut se faire immédiatement
- **Notifications :** Les signataires doivent surveiller l'interface pour voir les nouvelles propositions (pas encore de notifications automatiques)

---

## 8. Sécurité et Bonnes Pratiques

### 8.1 Stockage des Clés

**En Production :**
- Les clés des 5 signataires doivent être stockées dans des **HSM (Hardware Security Module)** certifiés FIPS 140-2
- Chaque signataire doit avoir son propre HSM isolé
- Aucune clé ne doit être stockée en clair dans l'environnement applicatif

**En Développement :**
- Les clés peuvent être stockées dans des variables d'environnement (`OPERATOR_PRIVATE_KEY`)
- ⚠️ **Ne jamais** utiliser les clés de production en développement

### 8.2 Rotation des Clés

- **Procédure :** Prévoir une rotation périodique des clés de gouvernance
- **Processus :** Le Multi-Sig lui-même peut ajouter/retirer des signataires (via `addOwner()` / `removeOwner()`)
- **Seuil :** Le changement de seuil (ex: 3/5 → 4/5) nécessite également un vote multi-sig

### 8.3 Audit Trail

- **Blockchain :** Toutes les propositions et confirmations sont enregistrées sur la blockchain (traçabilité permanente)
- **Backend :** Les logs backend enregistrent toutes les actions (`MultiSigService`)
- **Régulateur :** Le régulateur peut consulter l'historique via les endpoints d'audit

---

## 9. Intégration avec les Autres Services

### 9.1 Backend Spring Boot

Le backend expose les endpoints suivants pour interagir avec le Multi-Sig :
- `GET /api/backoffice/multisig/info` : Informations du council
- `GET /api/backoffice/multisig/transactions` : Liste des propositions
- `POST /api/backoffice/multisig/submit` : Soumettre une proposition
- `POST /api/backoffice/multisig/confirm/{txId}` : Confirmer une proposition
- `POST /api/backoffice/multisig/execute/{txId}` : Exécuter une proposition

### 9.2 Frontend Angular

L'interface utilisateur (`/backoffice/multisig`) permet aux administrateurs de :
- Visualiser l'état du council
- Soumettre de nouvelles propositions
- Confirmer les propositions en attente
- Exécuter les propositions prêtes

### 9.3 Smart Contracts

Tous les contrats vérifient les rôles via `hasRole(DEFAULT_ADMIN_ROLE, councilAddress)` ou `hasRole(GOVERNANCE_ROLE, councilAddress)`.

---

## 10. Plan de Continuité d'Activité (PCA)

### 10.1 Résilience

- **Géo-redondance :** Les nœuds Hyperledger Besu sont distribués géographiquement
- **RTO < 4 heures :** Si un data-center devient indisponible, le consensus IBFT 2.0 continue grâce aux nœuds de secours
- **RPO = 0 :** Aucune perte de données (réplication synchrone)

### 10.2 Disponibilité des Signataires

- **Seuil 3/5 :** Permet la continuité même si 2 signataires sont indisponibles
- **Délégation :** En cas d'indisponibilité prolongée, un signataire peut être remplacé via vote multi-sig

---

## 11. Conclusion

Le **MultiSigCouncil** est le **pilier de la gouvernance** de FAN Capital :

- ✅ **Élimine les points de défaillance unique** (SPoF)
- ✅ **Garantit la conformité réglementaire** (CMF)
- ✅ **Prévient la collusion** (5 signataires indépendants)
- ✅ **Assure la traçabilité** (toutes les actions sur la blockchain)
- ✅ **Permet la continuité** (seuil 3/5 résilient)

**En résumé :** Le Multi-Sig contrôle **toutes les actions administratives critiques** de la plateforme, tandis que les autres clés (Mint, Burn, Oracle, etc.) gèrent les **opérations quotidiennes** dans leurs périmètres respectifs.

---

**Dernière mise à jour :** Février 2026  
**Référence :** Dossier de Sécurité Institutionnelle v2.0
