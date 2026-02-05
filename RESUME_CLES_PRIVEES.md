# Résumé des Clés Privées Configurées

## Clés Privées Actuellement Configurées

### ✅ 1. PANIC_PRIVATE_KEY
- **Usage** : Pause globale d'urgence (Panic Button)
- **Rôle** : `PANIC_KEY_ROLE` sur `CircuitBreaker`
- **Service** : `PanicKeyService`
- **Stockage** : Cold Storage (production)

### ✅ 2. MINT_PRIVATE_KEY
- **Usage** : Créer des CashTokenTND (mint)
- **Rôle** : `MINTER_ROLE` sur `CashTokenTND`
- **Service** : `MintKeyService`
- **Stockage** : HSM (production)

### ✅ 3. BURN_PRIVATE_KEY
- **Usage** : Détruire des CashTokenTND (burn)
- **Rôle** : `BURNER_ROLE` sur `CashTokenTND`
- **Service** : `BurnKeyService`
- **Stockage** : HSM (production)

### ✅ 4. OPERATOR_PRIVATE_KEY (Déjà configuré)
- **Usage** : 
  - Opérations LiquidityPool (`buyFor`, `sellFor`)
  - Oracle (mise à jour VNI via keeper)
  - Compliance (whitelist KYC)
  - Autres opérations backend
- **Rôles** : 
  - `OPERATOR_ROLE` sur `LiquidityPool`
  - `ORACLE_ROLE` sur `PriceOracle` (via keeper)
  - `KYC_VALIDATOR_ROLE` sur `KYCRegistry`
- **Stockage** : Backend (clé partagée)

### ✅ 5. GOV_PRIVATE_KEY (Déjà configuré)
- **Usage** : Retraits du TaxVault vers fiscAddress
- **Rôle** : `GOVERNANCE_ROLE` sur `TaxVault`
- **Service** : `TaxVaultWriteService`
- **Stockage** : Backend

---

## Clés qui n'utilisent PAS de clé privée blockchain

### 6. Compliance (KYC)
- **Méthode** : Authentification email (JWT)
- **Pas de clé privée** : Utilise `OPERATOR_PRIVATE_KEY` pour signer les transactions blockchain
- **Stockage** : Database/Auth

### 7. Audit Key
- **Méthode** : Authentification email (JWT)
- **Pas de clé privée** : Read-Only, pas de transactions blockchain
- **Stockage** : Email-based auth

### 8. Governance (Multi-Sig)
- **Méthode** : Multi-Sig Wallet (`MultiSigCouncil`)
- **Pas de clé privée unique** : Utilise plusieurs signataires (3/5)
- **Stockage** : Multi-Sig Wallet

---

## Total des Clés Privées

**Clés privées blockchain configurées : 5**
1. ✅ `PANIC_PRIVATE_KEY` - Panic Key
2. ✅ `MINT_PRIVATE_KEY` - Mint Key
3. ✅ `BURN_PRIVATE_KEY` - Burn Key
4. ✅ `OPERATOR_PRIVATE_KEY` - Operator/Oracle/Compliance (partagée)
5. ✅ `GOV_PRIVATE_KEY` - Governance (TaxVault)

**Clés qui n'ont pas besoin de clé privée : 3**
- Compliance (utilise OPERATOR_PRIVATE_KEY)
- Audit Key (read-only)
- Governance (Multi-Sig)

---

## Séparation Recommandée (Optionnel)

Si vous voulez une séparation plus stricte selon le Livre Blanc Technique, vous pouvez créer :

### Optionnel : ORACLE_PRIVATE_KEY
- **Usage** : Uniquement pour Oracle (mise à jour VNI)
- **Rôle** : `ORACLE_ROLE` sur `PriceOracle`
- **Séparation** : Séparer de `OPERATOR_PRIVATE_KEY`

### Optionnel : COMPLIANCE_PRIVATE_KEY
- **Usage** : Uniquement pour KYC (whitelist)
- **Rôle** : `KYC_VALIDATOR_ROLE` sur `KYCRegistry`
- **Séparation** : Séparer de `OPERATOR_PRIVATE_KEY`

---

## Configuration Actuelle vs. Recommandée

| Clé | Actuel | Recommandé (Séparation Stricte) |
|-----|--------|--------------------------------|
| Panic Key | ✅ `PANIC_PRIVATE_KEY` | ✅ `PANIC_PRIVATE_KEY` |
| Mint Key | ✅ `MINT_PRIVATE_KEY` | ✅ `MINT_PRIVATE_KEY` |
| Burn Key | ✅ `BURN_PRIVATE_KEY` | ✅ `BURN_PRIVATE_KEY` |
| Oracle Key | ⚠️ `OPERATOR_PRIVATE_KEY` (partagée) | ⚠️ `ORACLE_PRIVATE_KEY` (dédiée) |
| Compliance | ⚠️ `OPERATOR_PRIVATE_KEY` (partagée) | ⚠️ `COMPLIANCE_PRIVATE_KEY` (dédiée) |
| Operator | ✅ `OPERATOR_PRIVATE_KEY` | ✅ `OPERATOR_PRIVATE_KEY` |
| Governance | ✅ `GOV_PRIVATE_KEY` | ✅ `GOV_PRIVATE_KEY` |

---

## Conclusion

**Actuellement configuré : 5 clés privées**
- 3 nouvelles clés (Panic, Mint, Burn)
- 2 clés existantes (Operator, Gov)

**Pour une séparation plus stricte** (optionnel) :
- Ajouter `ORACLE_PRIVATE_KEY` (séparer de Operator)
- Ajouter `COMPLIANCE_PRIVATE_KEY` (séparer de Operator)

**Total possible : 7 clés privées** (si on sépare Oracle et Compliance)
