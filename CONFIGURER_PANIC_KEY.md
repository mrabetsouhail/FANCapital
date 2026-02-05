# Configuration de PANIC_PRIVATE_KEY

## Problème

Le service `PanicKeyService` nécessite la configuration de `PANIC_PRIVATE_KEY` pour activer le Bouton Panique (pause globale d'urgence).

## Qu'est-ce que la Panic Key ?

Selon le **Livre Blanc Technique v3.0**, la Panic Key est une clé privée stockée en **cold storage** (hors ligne) qui permet d'arrêter instantanément toutes les transactions en cas d'intrusion ou de faille de sécurité détectée.

**Important** : Cette clé doit être stockée séparément de la clé de gouvernance normale pour garantir une sécurité maximale.

## Configuration pour Développement Local

Pour le développement local, vous pouvez utiliser une clé privée différente de `OPERATOR_PRIVATE_KEY` ou `GOV_PRIVATE_KEY`. 

### Option 1 : IntelliJ IDEA (Recommandé)

1. Ouvrez **Run > Edit Configurations...**
2. Sélectionnez votre configuration de run (ex: `BackendApplication`)
3. Dans **Environment variables**, ajoutez :
   ```
   PANIC_PRIVATE_KEY=<votre-clé-privée-hex>
   ```
4. Cliquez sur **Apply** puis **OK**

**Exemple avec une clé Hardhat de test** :
```
PANIC_PRIVATE_KEY=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
```

### Option 2 : PowerShell (Avant de lancer le backend)

```powershell
# Générer une nouvelle clé privée pour la Panic Key (dev local uniquement)
$env:PANIC_PRIVATE_KEY='0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d'
```

### Option 3 : Fichier `.env` (si supporté)

Créez un fichier `.env` à la racine du projet `backend/` :
```
PANIC_PRIVATE_KEY=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
```

## Attribution du PANIC_KEY_ROLE

Après avoir configuré la clé, vous devez attribuer le `PANIC_KEY_ROLE` sur le contrat `CircuitBreaker` à l'adresse correspondante.

### Obtenir l'adresse de la Panic Key

```powershell
# Via Hardhat console (si vous utilisez Hardhat)
cd blockchain
npx hardhat console
> const wallet = new ethers.Wallet('0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d')
> wallet.address
```

### Attribuer le rôle via script Hardhat

Créez un script `blockchain/scripts/grant-panic-key-role.ts` :

```typescript
import { ethers } from "hardhat";

async function main() {
  const PANIC_KEY_ADDRESS = process.env.PANIC_KEY_ADDRESS || "0x..."; // Adresse de votre Panic Key
  
  const deployments = require("../deployments/localhost.json");
  const circuitBreakerAddr = deployments.contracts?.CircuitBreaker || deployments.infra?.CircuitBreaker;
  
  if (!circuitBreakerAddr) {
    throw new Error("CircuitBreaker address not found in deployments");
  }
  
  const CircuitBreaker = await ethers.getContractAt("CircuitBreaker", circuitBreakerAddr);
  const PANIC_KEY_ROLE = await CircuitBreaker.PANIC_KEY_ROLE();
  
  // Le deployer (admin) doit attribuer le rôle
  const tx = await CircuitBreaker.grantRole(PANIC_KEY_ROLE, PANIC_KEY_ADDRESS);
  await tx.wait();
  
  console.log(`PANIC_KEY_ROLE granted to ${PANIC_KEY_ADDRESS}`);
}

main().catch(console.error);
```

Exécutez le script :
```powershell
cd blockchain
$env:PANIC_KEY_ADDRESS='0x<votre-adresse-panic-key>'
npm run hardhat run scripts/grant-panic-key-role.ts --network localhost
```

## Vérification

Après configuration, redémarrez le backend et vérifiez que :
1. Le backend démarre sans erreur
2. L'endpoint `/api/backoffice/emergency/pause-all` est accessible (avec authentification admin)
3. La pause globale fonctionne correctement

## Sécurité en Production

⚠️ **En production**, la Panic Key doit être :
1. **Stockée en cold storage** (hardware wallet, paper wallet, ou coffre-fort)
2. **Jamais stockée sur le serveur** de production
3. **Accès restreint** : Seulement les personnes autorisées doivent y avoir accès
4. **Rotation régulière** : Changer la clé périodiquement selon les procédures de sécurité

## Utilisation

Une fois configurée, la Panic Key peut être utilisée via :
- **Backend API** : `POST /api/backoffice/emergency/pause-all` (nécessite `ADMIN_EMAILS`)
- **Directement** : Appel de `CircuitBreaker.pauseAll(reason)` avec la Panic Key

**Note** : La reprise (`resumeAll()`) nécessite `GOVERNANCE_ROLE` (multi-sig), pas `PANIC_KEY_ROLE`, pour sécurité.
