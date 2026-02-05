# Implémentation du Livre Blanc Technique v3.0

## État Actuel vs. Spécifications

### ✅ Déjà Implémenté

#### 1. Gestion des Portefeuilles (WaaS)
- ✅ Wallet-as-a-Service avec AES-256-GCM
- ✅ Création native lors de validation KYC
- ✅ Abstraction UX (pas de MetaMask requis)
- ✅ Clés privées chiffrées et stockées dans la base de données

#### 2. Mécanisme de Création (Minting) et Équilibre Actif/Passif
- ✅ Formule : `N_tokens = V_portefeuille / VNI`
- ✅ Réception TND sur compte bancaire (modélisé)
- ✅ Émission CashTokenTND (Cash Wallet)
- ✅ Swap atomique via LiquidityPool
- ✅ Mint/Burn corrélé aux actifs réels

#### 3. Matrice de Sécurité Multi-Clés (Partiellement)
- ✅ **Mint Key** : `MINTER_ROLE` sur `CashTokenTND`
- ✅ **Burn Key** : `BURNER_ROLE` sur `CashTokenTND`
- ✅ **Oracle Key** : `ORACLE_ROLE` sur `PriceOracle`
- ✅ **Compliance** : `KYC_VALIDATOR_ROLE` sur `KYCRegistry`
- ✅ **Audit Key** : Endpoints audit avec rôles (Read-Only)
- ⚠️ **Governance** : `MultiSigCouncil` existe mais pas utilisé comme `DEFAULT_ADMIN_ROLE`
- ⚠️ **Panic Key** : CircuitBreaker existe mais pause limitée aux redemptions

#### 4. Sécurité Opérationnelle (Partiellement)
- ⚠️ **Circuit Breaker** : Pause redemptions seulement (pas de pause globale)
- ✅ **Proof of Reserve** : `AuditReconciliationService` existe mais manque réconciliation off-chain

---

## À Implémenter

### 1. Multi-Sig Governance (3/5)

**Objectif** : Utiliser `MultiSigCouncil` comme `DEFAULT_ADMIN_ROLE` sur tous les contrats.

**Actions** :
- [ ] Modifier les scripts de déploiement pour utiliser `MultiSigCouncil` comme admin
- [ ] Créer un service backend pour gérer les propositions multi-sig
- [ ] Ajouter endpoints backoffice pour soumettre/confirmer/exécuter des transactions multi-sig
- [ ] Documenter le processus de gouvernance

**Fichiers à modifier** :
- `blockchain/scripts/deploy.ts` - Utiliser council comme admin
- `blockchain/scripts/deploy-factory-funds.ts` - Utiliser council comme admin
- `backend/src/main/java/com/fancapital/backend/backoffice/service/MultiSigService.java` - Nouveau service
- `backend/src/main/java/com/fancapital/backend/backoffice/controller/MultiSigController.java` - Nouveau controller

---

### 2. Circuit Breaker avec Pause Globale

**Objectif** : Implémenter un "Bouton Panique" qui pause tous les contrats simultanément.

**Actions** :
- [ ] Ajouter `Pausable` (OpenZeppelin) aux contrats critiques :
  - `CPEFToken`
  - `LiquidityPool`
  - `P2PExchange`
  - `CashTokenTND` (optionnel)
- [ ] Créer `PanicKey` contract ou modifier `CircuitBreaker` pour pause globale
- [ ] Implémenter `pauseAll()` et `unpauseAll()` dans `CircuitBreaker`
- [ ] Ajouter vérification `paused()` dans toutes les fonctions critiques
- [ ] Créer service backend pour gérer la Panic Key (clé froide)

**Fichiers à modifier** :
- `blockchain/contracts/governance/CircuitBreaker.sol` - Ajouter pause globale
- `blockchain/contracts/core/CPEFToken.sol` - Ajouter `Pausable`
- `blockchain/contracts/services/LiquidityPool.sol` - Ajouter `Pausable`
- `blockchain/contracts/services/P2PExchange.sol` - Ajouter `Pausable`
- `backend/src/main/java/com/fancapital/backend/backoffice/service/PanicKeyService.java` - Nouveau service

---

### 3. Panic Key (Clé Froide)

**Objectif** : Clé privée stockée hors ligne (cold storage) pour pause d'urgence.

**Actions** :
- [ ] Documenter le processus de stockage de la Panic Key
- [ ] Créer un script de déploiement qui génère une clé dédiée
- [ ] Implémenter un service backend avec configuration séparée pour Panic Key
- [ ] Ajouter endpoints backoffice protégés pour pause d'urgence
- [ ] Créer procédure d'urgence documentée

**Fichiers à créer** :
- `docs/blockchain/PANIC_KEY_PROCEDURE.md`
- `backend/src/main/java/com/fancapital/backend/backoffice/service/PanicKeyService.java`
- `backend/src/main/java/com/fancapital/backend/backoffice/controller/EmergencyController.java`

---

### 4. Proof of Reserve Amélioré

**Objectif** : Réconciliation complète entre blockchain et positions off-chain (bourse).

**Actions** :
- [ ] Créer modèle de données pour positions off-chain (bourse)
- [ ] Implémenter service de réconciliation avec API bourse (mock pour MVP)
- [ ] Ajouter calcul de réserves totales (blockchain + off-chain)
- [ ] Créer dashboard de réconciliation pour régulateur
- [ ] Générer rapports de preuve de réserve

**Fichiers à créer/modifier** :
- `backend/src/main/java/com/fancapital/backend/backoffice/audit/model/OffChainPosition.java`
- `backend/src/main/java/com/fancapital/backend/backoffice/audit/service/ProofOfReserveService.java`
- `backend/src/main/java/com/fancapital/backend/backoffice/controller/ProofOfReserveController.java`
- `frontend/src/app/components/backoffice/proof-of-reserve-page/` - Nouveau composant

---

### 5. Documentation Architecture 7 Clés

**Objectif** : Documenter l'architecture complète des 7 clés et leur stockage.

**Actions** :
- [ ] Créer document détaillé sur chaque clé
- [ ] Documenter les procédures de stockage (HSM, Cold Storage, etc.)
- [ ] Créer matrice de responsabilités
- [ ] Documenter les procédures de rotation des clés

**Fichiers à créer** :
- `docs/blockchain/ARCHITECTURE_7_CLES.md`
- `docs/blockchain/PROCEDURES_STOCKAGE_CLES.md`
- `docs/blockchain/MATRICE_RESPONSABILITES.md`

---

## Priorités

1. **Haute Priorité** :
   - Circuit Breaker avec pause globale (sécurité critique)
   - Panic Key (sécurité d'urgence)

2. **Priorité Moyenne** :
   - Multi-Sig Governance (gouvernance)
   - Proof of Reserve amélioré (conformité)

3. **Priorité Basse** :
   - Documentation complète (maintenance)

---

## Notes Techniques

### Stockage des Clés

Selon le Livre Blanc :
- **Governance** : Multi-sig 3/5 (déjà implémenté via `MultiSigCouncil`)
- **Mint Key** : HSM Isolé (actuellement clé privée backend)
- **Burn Key** : HSM Isolé (actuellement clé privée backend)
- **Oracle Key** : API Backend (actuellement `ORACLE_ROLE`)
- **Compliance** : Database/Auth (actuellement `KYC_VALIDATOR_ROLE`)
- **Panic Key** : Cold Storage (à implémenter)
- **Audit Key** : Read-Only Key (actuellement endpoints avec rôles)

### Migration vers Production

Pour la production, il faudra :
1. Migrer vers HSM pour Mint/Burn keys
2. Implémenter cold storage pour Panic Key
3. Configurer multi-sig avec signataires réels
4. Mettre en place monitoring et alertes
