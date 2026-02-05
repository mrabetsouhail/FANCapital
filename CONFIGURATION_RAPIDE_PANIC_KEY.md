# Configuration Rapide - Panic Key

## Étape 1 : Configurer dans IntelliJ IDEA

1. **Run > Edit Configurations...**
2. Sélectionnez votre configuration (ex: `BackendApplication`)
3. **Environment variables** → Ajoutez :
   ```
   PANIC_PRIVATE_KEY=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
   ```
   *(Cette clé est un exemple Hardhat #1 - utilisez une clé différente en production)*

## Étape 2 : Obtenir l'adresse de la Panic Key

### Option A : Via Hardhat Console

```powershell
cd blockchain
npx hardhat console
```

Dans la console :
```javascript
const ethers = require("ethers");
const wallet = new ethers.Wallet("0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d");
console.log("Panic Key Address:", wallet.address);
// Résultat: 0x70997970C51812dc3A010C7d01b50e0d17dc79C8
```

### Option B : Via PowerShell (avec Node.js)

```powershell
node -e "const ethers = require('ethers'); const w = new ethers.Wallet('0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d'); console.log(w.address);"
```

## Étape 3 : Attribuer le PANIC_KEY_ROLE

```powershell
cd blockchain
$env:PANIC_KEY_ADDRESS='0x70997970C51812dc3A010C7d01b50e0d17dc79C8'
npm run hardhat run scripts/grant-panic-key-role.ts --network localhost
```

## Étape 4 : Vérifier

Redémarrez le backend et testez l'endpoint :
```powershell
# Avec authentification admin
curl -X POST http://localhost:8081/api/backoffice/emergency/pause-all \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <votre-jwt-token>" \
  -d '{"reason": "Test pause globale"}'
```

## Clés Hardhat de Test (Développement Uniquement)

Pour le développement local, vous pouvez utiliser les clés Hardhat par défaut :

| # | Private Key | Address |
|---|-------------|---------|
| 0 | `0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80` | `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266` |
| 1 | `0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d` | `0x70997970C51812dc3A010C7d01b50e0d17dc79C8` |
| 2 | `0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a` | `0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC` |

**⚠️ Ne JAMAIS utiliser ces clés en production !**

## Résumé

1. ✅ Ajouter `PANIC_PRIVATE_KEY` dans IntelliJ IDEA
2. ✅ Obtenir l'adresse de la clé
3. ✅ Exécuter `grant-panic-key-role.ts` avec l'adresse
4. ✅ Redémarrer le backend
5. ✅ Tester l'endpoint `/api/backoffice/emergency/pause-all`
