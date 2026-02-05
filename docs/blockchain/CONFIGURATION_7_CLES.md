# Configuration des 7 Clés - Livre Blanc Technique v3.0

Selon le **Livre Blanc Technique Complet**, l'architecture FAN-Capital utilise **7 clés distinctes** pour isoler les risques et segmenter les privilèges cryptographiques.

## Tableau des 7 Clés

| Clé / Rôle | Fonctionnalité Spécifique | Type de Stockage | État |
|------------|---------------------------|------------------|------|
| **Governance** | Modification des paramètres vitaux | Multi-sig 3/5 | ⚠️ À configurer |
| **Mint Key** | Autorise la création de nouveaux titres | HSM Isolé | ✅ Partiellement (MINTER_ROLE) |
| **Burn Key** | Détruit les jetons lors des rachats | HSM Isolé | ✅ Partiellement (BURNER_ROLE) |
| **Oracle Key** | Met à jour les cours en temps réel (BVMT) | API Backend | ✅ Configuré (ORACLE_ROLE) |
| **Compliance** | Gestion du Whitelisting (KYC LBA/FT) | Database/Auth | ✅ Configuré (KYC_VALIDATOR_ROLE) |
| **Panic Key** | Arrêt immédiat de toutes les transactions | Cold Storage | ✅ Configuré (PANIC_KEY_ROLE) |
| **Audit Key** | Accès aux registres chiffrés pour régulateur | Read-Only Key | ✅ Configuré (endpoints audit) |

---

## 1. Governance (Multi-Sig 3/5)

**Rôle** : `DEFAULT_ADMIN_ROLE` sur tous les contrats

**Configuration Actuelle** :
- ✅ `MultiSigCouncil` contract déployé
- ⚠️ Pas encore utilisé comme `DEFAULT_ADMIN_ROLE` (utilise encore le deployer EOA)

**À Faire** :
- [ ] Configurer `MultiSigCouncil` avec 5 signataires
- [ ] Utiliser `MultiSigCouncil` comme admin lors du déploiement
- [ ] Créer service backend pour gérer les propositions multi-sig
- [ ] Documenter le processus de gouvernance

**Stockage** : Multi-sig wallet (3 signatures sur 5 requises)

---

## 2. Mint Key (HSM Isolé)

**Rôle** : `MINTER_ROLE` sur `CashTokenTND`

**Configuration Actuelle** :
- ✅ `MINTER_ROLE` existe dans `CashTokenTND`
- ⚠️ Utilise actuellement `OPERATOR_PRIVATE_KEY` ou clé du deployer

**À Faire** :
- [ ] Créer variable d'environnement `MINT_PRIVATE_KEY`
- [ ] Créer service backend `MintKeyService`
- [ ] Configurer dans IntelliJ IDEA
- [ ] Attribuer `MINTER_ROLE` à l'adresse de la Mint Key
- [ ] Documenter procédure de stockage HSM (production)

**Stockage** :
- **Dev** : Variable d'environnement backend
- **Production** : HSM (Hardware Security Module) isolé

**Configuration IntelliJ IDEA** :
```
MINT_PRIVATE_KEY=0x<votre-clé-privée-hex>
```

---

## 3. Burn Key (HSM Isolé)

**Rôle** : `BURNER_ROLE` sur `CashTokenTND`

**Configuration Actuelle** :
- ✅ `BURNER_ROLE` existe dans `CashTokenTND`
- ⚠️ Utilise actuellement `OPERATOR_PRIVATE_KEY` ou clé du deployer

**À Faire** :
- [ ] Créer variable d'environnement `BURN_PRIVATE_KEY`
- [ ] Créer service backend `BurnKeyService`
- [ ] Configurer dans IntelliJ IDEA
- [ ] Attribuer `BURNER_ROLE` à l'adresse de la Burn Key
- [ ] Documenter procédure de stockage HSM (production)

**Stockage** :
- **Dev** : Variable d'environnement backend
- **Production** : HSM (Hardware Security Module) isolé

**Configuration IntelliJ IDEA** :
```
BURN_PRIVATE_KEY=0x<votre-clé-privée-hex>
```

---

## 4. Oracle Key (API Backend)

**Rôle** : `ORACLE_ROLE` sur `PriceOracle`

**Configuration Actuelle** :
- ✅ `ORACLE_ROLE` existe dans `PriceOracle`
- ✅ Utilisé par le keeper `price-bot.ts`
- ✅ Backend peut mettre à jour VNI via `PriceOracleWriteService`

**Configuration** :
- Utilise `OPERATOR_PRIVATE_KEY` ou clé dédiée du keeper
- Keeper configuré via `keepers/price-bot.example.json`

**Stockage** : API Backend (clé privée dans configuration keeper)

**Note** : Cette clé est déjà fonctionnelle via le keeper.

---

## 5. Compliance (Database/Auth)

**Rôle** : `KYC_VALIDATOR_ROLE` sur `KYCRegistry`

**Configuration Actuelle** :
- ✅ `KYC_VALIDATOR_ROLE` existe dans `KYCRegistry`
- ✅ Backend utilise `KYCRegistryWriteService` pour whitelist
- ✅ Authentification via `ADMIN_EMAILS` dans `BackofficeAuthzService`

**Configuration** :
- Utilise `OPERATOR_PRIVATE_KEY` pour signer les transactions
- Authentification basée sur email (JWT)

**Stockage** : Database/Auth (pas de clé privée dédiée, utilise OPERATOR_ROLE)

**Note** : Cette clé est déjà fonctionnelle via le backend.

---

## 6. Panic Key (Cold Storage) ✅

**Rôle** : `PANIC_KEY_ROLE` sur `CircuitBreaker`

**Configuration Actuelle** :
- ✅ `PANIC_KEY_ROLE` existe dans `CircuitBreaker`
- ✅ `PanicKeyService` créé
- ✅ `EmergencyController` créé
- ✅ Variable d'environnement `PANIC_PRIVATE_KEY` configurée
- ✅ Rôle attribué sur CircuitBreaker

**Configuration IntelliJ IDEA** :
```
PANIC_PRIVATE_KEY=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
```

**Stockage** :
- **Dev** : Variable d'environnement backend
- **Production** : Cold Storage (hardware wallet, paper wallet, coffre-fort)

**Utilisation** :
- Endpoint : `POST /api/backoffice/emergency/pause-all`
- Nécessite : `ADMIN_EMAILS` + `PANIC_PRIVATE_KEY`

---

## 7. Audit Key (Read-Only Key)

**Rôle** : Accès en lecture seule aux registres d'audit

**Configuration Actuelle** :
- ✅ Endpoints audit créés (`AuditRegistryController`)
- ✅ Rôles : `AUDIT_REGULATOR_EMAILS`, `AUDIT_COMPLIANCE_EMAILS`
- ✅ Authentification basée sur email (JWT)

**Configuration** :
- Pas de clé privée blockchain nécessaire
- Authentification via email dans `ADMIN_EMAILS`, `AUDIT_REGULATOR_EMAILS`, `AUDIT_COMPLIANCE_EMAILS`

**Stockage** : Read-Only Key (pas de clé privée, juste authentification)

**Note** : Cette clé est déjà fonctionnelle via les endpoints audit.

---

## Résumé des Configurations Requises

### Variables d'Environnement à Ajouter dans IntelliJ IDEA

1. ✅ `PANIC_PRIVATE_KEY` - **Déjà configuré**
2. ⚠️ `MINT_PRIVATE_KEY` - **À configurer**
3. ⚠️ `BURN_PRIVATE_KEY` - **À configurer**
4. ✅ `OPERATOR_PRIVATE_KEY` - **Déjà configuré** (utilisé pour Oracle, Compliance)
5. ✅ `GOV_PRIVATE_KEY` - **Déjà configuré** (pour TaxVault)

### Services Backend à Créer

1. ✅ `PanicKeyService` - **Créé**
2. ⚠️ `MintKeyService` - **À créer**
3. ⚠️ `BurnKeyService` - **À créer**
4. ⚠️ `MultiSigService` - **À créer** (pour gouvernance)

### Rôles à Attribuer sur Blockchain

1. ✅ `PANIC_KEY_ROLE` sur `CircuitBreaker` - **Attribué**
2. ⚠️ `MINTER_ROLE` sur `CashTokenTND` - **À attribuer**
3. ⚠️ `BURNER_ROLE` sur `CashTokenTND` - **À attribuer**
4. ✅ `ORACLE_ROLE` sur `PriceOracle` - **Déjà attribué** (via keeper)
5. ✅ `KYC_VALIDATOR_ROLE` sur `KYCRegistry` - **Déjà attribué** (via backend)

---

## Prochaines Étapes

1. **Configurer Mint Key et Burn Key** (similaire à Panic Key)
2. **Implémenter Multi-Sig Governance** (utiliser MultiSigCouncil comme admin)
3. **Documenter procédures de stockage** pour production (HSM, Cold Storage)

---

## Notes de Sécurité

⚠️ **En production** :
- **Mint Key** et **Burn Key** doivent être stockées dans un HSM
- **Panic Key** doit être en cold storage (hors ligne)
- **Governance** doit utiliser un multi-sig réel (pas de test)
- Toutes les clés doivent être rotées régulièrement selon les procédures de sécurité
