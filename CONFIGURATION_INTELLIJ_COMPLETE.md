# Configuration Complète IntelliJ IDEA - Toutes les Variables d'Environnement

## ✅ Variables Déjà Configurées

Vous avez déjà configuré :
- ✅ `PANIC_PRIVATE_KEY`
- ✅ `MINT_PRIVATE_KEY`
- ✅ `BURN_PRIVATE_KEY`
- ✅ `WALLET_ENC_KEY`

## ⚠️ Variables Manquantes à Ajouter

### 1. OPERATOR_PRIVATE_KEY (ESSENTIELLE)

**Usage** : Clé privée pour les opérations backend (LiquidityPool, Oracle, Compliance)

**Valeur** : Utilisez une clé Hardhat de test (ex: clé #0)
```
OPERATOR_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
```

**Adresse correspondante** : `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266`

**Pourquoi c'est important** : Sans cette clé, le backend ne peut pas :
- Exécuter les achats/ventes (LiquidityPool)
- Mettre à jour les prix (Oracle)
- Whitelist les utilisateurs (Compliance)

---

### 2. GOV_PRIVATE_KEY (Pour TaxVault)

**Usage** : Clé privée pour les retraits du TaxVault vers fiscAddress

**Valeur** : Pour le dev local, utilisez la même que OPERATOR_PRIVATE_KEY
```
GOV_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
```

**Pourquoi c'est important** : Nécessaire pour le dashboard fiscal (retraits TaxVault)

---

### 3. JWT_SECRET (ESSENTIELLE)

**Usage** : Secret pour signer les tokens JWT (authentification)

**Valeur** : Générer un secret de 48+ caractères
```
JWT_SECRET=votre-secret-jwt-48-caracteres-minimum-pour-securite-owasp
```

**Génération PowerShell** :
```powershell
$bytes = New-Object byte[] 32
(New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

**Pourquoi c'est important** : Sans cette clé, l'authentification ne fonctionne pas

---

### 4. Configuration MariaDB (Si vous utilisez MariaDB)

**Variables** :
```
SPRING_PROFILES_ACTIVE=mariadb
DB_URL=jdbc:mariadb://127.0.0.1:3306/fancapital
DB_USERNAME=root
DB_PASSWORD=
```

**Note** : Si vous n'utilisez pas de mot de passe pour root, laissez `DB_PASSWORD` vide.

**Pourquoi c'est important** : Pour utiliser MariaDB au lieu de H2

---

## 📋 Configuration Complète dans IntelliJ IDEA

### Étapes

1. **Ouvrez** : `Run > Edit Configurations...`
2. **Sélectionnez** : Votre configuration backend (ex: `BackendApplication`)
3. **Cliquez sur** : `Environment variables` (icône avec 3 points `...`)
4. **Ajoutez toutes ces variables** :

```
PANIC_PRIVATE_KEY=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
MINT_PRIVATE_KEY=0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a
BURN_PRIVATE_KEY=0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6
WALLET_ENC_KEY=<votre-valeur-existante>
OPERATOR_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
GOV_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
JWT_SECRET=votre-secret-jwt-48-caracteres-minimum-pour-securite-owasp
SPRING_PROFILES_ACTIVE=mariadb
DB_URL=jdbc:mariadb://127.0.0.1:3306/fancapital
DB_USERNAME=root
DB_PASSWORD=
```

5. **Cliquez sur** : `OK` puis `Apply`

---

## 🔑 Clés Hardhat de Test (Développement Uniquement)

| # | Private Key | Address | Usage |
|---|-------------|---------|-------|
| 0 | `0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80` | `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266` | OPERATOR, GOV |
| 1 | `0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d` | `0x70997970C51812dc3A010C7d01b50e0d17dc79C8` | PANIC |
| 2 | `0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a` | `0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC` | MINT |
| 3 | `0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6` | `0x90F79bf6EB2c4f870365E785982E1f101E93b906` | BURN |

**⚠️ Ne JAMAIS utiliser ces clés en production !**

---

## ✅ Checklist de Configuration

- [x] PANIC_PRIVATE_KEY
- [x] MINT_PRIVATE_KEY
- [x] BURN_PRIVATE_KEY
- [x] WALLET_ENC_KEY
- [ ] OPERATOR_PRIVATE_KEY ⚠️ **ESSENTIELLE**
- [ ] GOV_PRIVATE_KEY
- [ ] JWT_SECRET ⚠️ **ESSENTIELLE**
- [ ] SPRING_PROFILES_ACTIVE (si MariaDB)
- [ ] DB_URL (si MariaDB)
- [ ] DB_USERNAME (si MariaDB)
- [ ] DB_PASSWORD (si MariaDB)

---

## 🧪 Vérification

Après configuration, redémarrez le backend et vérifiez :

1. **Backend démarre sans erreur**
2. **Authentification fonctionne** (login)
3. **Transactions fonctionnent** (achat/vente)
4. **Dashboard fiscal accessible** (si admin)

---

## 📝 Notes

- **JWT_SECRET** : Changez cette valeur en production
- **OPERATOR_PRIVATE_KEY** : Doit avoir les rôles appropriés sur les contrats
- **GOV_PRIVATE_KEY** : Peut être la même que OPERATOR_PRIVATE_KEY en dev
- **DB_PASSWORD** : Laissez vide si root n'a pas de mot de passe

---

## 🚨 Erreurs Courantes

### "OPERATOR_PRIVATE_KEY not configured"
→ Ajoutez `OPERATOR_PRIVATE_KEY` dans les variables d'environnement

### "JWT_SECRET not configured"
→ Ajoutez `JWT_SECRET` avec une valeur de 48+ caractères

### "Cannot connect to database"
→ Vérifiez `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` et que MariaDB est démarré

### "Access denied for user"
→ Vérifiez `DB_USERNAME` et `DB_PASSWORD` (laissez vide si pas de mot de passe)
