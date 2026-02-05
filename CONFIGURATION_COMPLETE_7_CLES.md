# Configuration Complète des 7 Clés - Guide Rapide

## ✅ Résumé des Clés Configurées

| Clé | Variable d'Environnement | Adresse | Rôle | État |
|-----|-------------------------|---------|------|------|
| **Panic Key** | `PANIC_PRIVATE_KEY` | `0x70997970C51812dc3A010C7d01b50e0d17dc79C8` | `PANIC_KEY_ROLE` | ✅ Configuré |
| **Mint Key** | `MINT_PRIVATE_KEY` | `0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC` | `MINTER_ROLE` | ✅ Configuré |
| **Burn Key** | `BURN_PRIVATE_KEY` | `0x90F79bf6EB2c4f870365E785982E1f101E93b906` | `BURNER_ROLE` | ✅ Configuré |
| **Oracle Key** | `OPERATOR_PRIVATE_KEY` | (keeper) | `ORACLE_ROLE` | ✅ Configuré |
| **Compliance** | `OPERATOR_PRIVATE_KEY` | (backend) | `KYC_VALIDATOR_ROLE` | ✅ Configuré |
| **Audit Key** | (email) | (endpoints) | Read-Only | ✅ Configuré |
| **Governance** | (Multi-Sig) | (MultiSigCouncil) | `DEFAULT_ADMIN_ROLE` | ⚠️ À configurer |

---

## 📋 Configuration IntelliJ IDEA

Ajoutez ces variables d'environnement dans **Run > Edit Configurations...** :

```
PANIC_PRIVATE_KEY=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
MINT_PRIVATE_KEY=0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a
BURN_PRIVATE_KEY=0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6
```

**Note** : Ces clés sont des clés Hardhat de test. En production, utilisez des clés sécurisées.

---

## 🔑 Détails des Clés

### 1. Panic Key ✅
- **Rôle** : `PANIC_KEY_ROLE` sur `CircuitBreaker`
- **Fonction** : Pause globale d'urgence
- **Service** : `PanicKeyService`
- **Endpoint** : `POST /api/backoffice/emergency/pause-all`
- **Stockage** : Cold Storage (production)

### 2. Mint Key ✅
- **Rôle** : `MINTER_ROLE` sur `CashTokenTND`
- **Fonction** : Créer des CashTokenTND
- **Service** : `MintKeyService`
- **Stockage** : HSM (production)

### 3. Burn Key ✅
- **Rôle** : `BURNER_ROLE` sur `CashTokenTND`
- **Fonction** : Détruire des CashTokenTND
- **Service** : `BurnKeyService`
- **Stockage** : HSM (production)

### 4. Oracle Key ✅
- **Rôle** : `ORACLE_ROLE` sur `PriceOracle`
- **Fonction** : Mettre à jour VNI
- **Service** : Keeper `price-bot.ts`
- **Stockage** : API Backend

### 5. Compliance ✅
- **Rôle** : `KYC_VALIDATOR_ROLE` sur `KYCRegistry`
- **Fonction** : Whitelist utilisateurs
- **Service** : `KYCRegistryWriteService`
- **Stockage** : Database/Auth

### 6. Audit Key ✅
- **Rôle** : Read-Only
- **Fonction** : Accès aux registres d'audit
- **Service** : `AuditRegistryController`
- **Stockage** : Email-based auth

### 7. Governance ⚠️
- **Rôle** : `DEFAULT_ADMIN_ROLE` (Multi-Sig 3/5)
- **Fonction** : Modification des paramètres vitaux
- **Service** : `MultiSigCouncil` (à implémenter)
- **Stockage** : Multi-Sig Wallet

---

## 🧪 Tests des Fonctionnalités

### Test Panic Key
```bash
POST /api/backoffice/emergency/pause-all
Body: {"reason": "Test pause globale"}
```

### Test Mint Key
```java
// Via MintKeyService
mintKeyService.mint(userAddress, amount);
```

### Test Burn Key
```java
// Via BurnKeyService
burnKeyService.burn(userAddress, amount);
```

---

## 📝 Scripts Disponibles

1. `get-panic-key-address.ts` - Obtenir l'adresse de la Panic Key
2. `grant-panic-key-role.ts` - Attribuer PANIC_KEY_ROLE
3. `get-mint-burn-key-addresses.ts` - Obtenir les adresses Mint/Burn
4. `grant-minter-role.ts` - Attribuer MINTER_ROLE
5. `grant-burner-role.ts` - Attribuer BURNER_ROLE

---

## ⚠️ Notes de Sécurité

- **Développement** : Utilisez les clés Hardhat de test
- **Production** :
  - Panic Key → Cold Storage (hors ligne)
  - Mint/Burn Keys → HSM (Hardware Security Module)
  - Governance → Multi-Sig réel (pas de test)
  - Rotation régulière des clés selon procédures

---

## ✅ Checklist de Configuration

- [x] Panic Key configurée et rôle attribué
- [x] Mint Key configurée et rôle attribué
- [x] Burn Key configurée et rôle attribué
- [x] Services backend créés
- [x] Variables d'environnement documentées
- [ ] Multi-Sig Governance (à implémenter)
- [ ] Tests end-to-end de toutes les clés

---

## 🚀 Prochaines Étapes

1. **Tester toutes les clés** : Vérifier que chaque clé fonctionne correctement
2. **Implémenter Multi-Sig Governance** : Utiliser `MultiSigCouncil` comme admin
3. **Documenter procédures de production** : HSM, Cold Storage, rotation des clés
