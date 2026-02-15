# Commandes pour lancer le projet FAN-Capital

Exécuter les commandes **dans l’ordre**, dans des terminaux séparés.

---

## 0. Configuration (une seule fois)

### Variables d’environnement (IntelliJ ou terminal)

Pour le backend, définir au minimum :

```
JWT_SECRET=votre-secret-jwt-48-caracteres-minimum-pour-securite
WALLET_ENC_KEY=<clé-base64-32-octets>
OPERATOR_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
MINT_PRIVATE_KEY=0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a
BURN_PRIVATE_KEY=0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6
```

Voir `VARIABLES_ENVIRONNEMENT_COMPLETE.txt` pour l’ensemble des variables.

---

## 1. Installer les dépendances (première fois)

**PowerShell :**

```powershell
cd c:\Users\MSI\fancapital\blockchain
npm install
```

```powershell
cd c:\Users\MSI\fancapital\frontend
npm install
```

---

## 2. Démarrer le nœud blockchain (Terminal 1)

```powershell
cd c:\Users\MSI\fancapital\blockchain
npm run node
```

Laisser ce terminal ouvert. Le nœud écoute sur **http://127.0.0.1:8545**.

---

## 3. Déployer les contrats (Terminal 2)

```powershell
cd c:\Users\MSI\fancapital\blockchain
npm run deploy:factory-funds:localhost
```

---

## 4. Accorder les rôles (Terminal 2)

```powershell
cd c:\Users\MSI\fancapital\blockchain
$env:MINT_PRIVATE_KEY="0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a"
npm run grant-minter
```

```powershell
$env:BURN_PRIVATE_KEY="0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6"
npm run grant-burner
```

*(Remplacer les clés par celles de `VARIABLES_ENVIRONNEMENT_COMPLETE.txt` si besoin.)*

---

## 5. Optionnel : Alimenter un wallet en ETH (pour achats, Premium)

Si un wallet utilisateur n’a pas d’ETH pour payer le gas :

```powershell
$env:USER_ADDRESS="0x467902a7366b68000c4936ab5f048296cebef72c"
npm run fund-eth
```

---

## 6. Optionnel : Alimenter Cash Wallet + whitelist (Terminal 2)

```powershell
$env:USER_ADDRESS="0x<adresse_wallet>"
npm run seed:cash
npm run seed:whitelist
```

---

## 7. Démarrer le backend (Terminal 2 ou IntelliJ)

**Terminal :**

```powershell
cd c:\Users\MSI\fancapital\backend
.\mvnw.cmd spring-boot:run
```

**Ou depuis IntelliJ :** lancer `BackendApplication` (Run Configuration avec les variables d’environnement).

Le backend écoute sur **http://127.0.0.1:8081**.

---

## 8. Démarrer le frontend (Terminal 3)

```powershell
cd c:\Users\MSI\fancapital\frontend
npm start
```

L’application est disponible sur **http://localhost:4200**.

---

## Résumé rapide (3 terminaux)

| Terminal | Commande | Port |
|----------|----------|------|
| **1** | `cd blockchain` puis `npm run node` | 8545 |
| **2** | `cd blockchain` puis `npm run deploy:factory-funds:localhost` puis `npm run grant-minter` puis `npm run grant-burner` | — |
| **2** | `cd backend` puis `.\mvnw.cmd spring-boot:run` | 8081 |
| **3** | `cd frontend` puis `npm start` | 4200 |

---

## Ordre minimal (démarrage quotidien)

1. **Terminal 1** : `cd blockchain` → `npm run node`
2. **Terminal 2** : `cd backend` → `.\mvnw.cmd spring-boot:run`
3. **Terminal 3** : `cd frontend` → `npm start`

*(Sauf redéploiement de la chaîne, les étapes 3 et 4 ne sont pas à refaire.)*
