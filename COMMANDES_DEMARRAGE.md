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

## 0b. MariaDB (optionnel — si vous préférez MariaDB à H2)

Par défaut, le backend utilise **H2** (fichier local, sans serveur). Pour utiliser **MariaDB** :

### 1. Installer MariaDB sur Windows

- Télécharger : https://mariadb.org/download/
- Ou via Chocolatey : `choco install mariadb`
- Ou via winget : `winget install MariaDB.MariaDB`

### 2. Démarrer MariaDB

- **Service Windows** : `net start mariadb` (ou "MariaDB" dans Services)
- Ou lancer `mysqld` depuis l'installation

### 3. Créer la base de données

```sql
mysql -u root -p
CREATE DATABASE fancapital CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;
```

### 4. Variables pour le profil MariaDB

```powershell
$env:SPRING_PROFILES_ACTIVE="mariadb"
$env:DB_URL="jdbc:mariadb://127.0.0.1:3306/fancapital"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="votre_mot_de_passe"
```

*(Si le mot de passe root est vide, laisser `DB_PASSWORD` vide.)*

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
$env:MINT_PRIVATE_KEY="0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a"
$env:BURN_PRIVATE_KEY="0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6"
npm run deploy:factory-funds:localhost
```

*(Les rôles MINTER et BURNER sont attribués automatiquement si MINT_PRIVATE_KEY et BURN_PRIVATE_KEY sont définis.)*

*Un premier financement de **50 000 TND** par pool est injecté automatiquement lors du déploiement, afin que tout utilisateur puisse acheter et vendre des CPEF dès le début. Pour modifier ce montant : `$env:INITIAL_LIQUIDITY_TND="100000"` avant le déploiement.*

---

## 4. Accorder les rôles (Terminal 2) — optionnel

Si les variables MINT_PRIVATE_KEY et BURN_PRIVATE_KEY ont été définies à l’étape 3, les rôles sont déjà attribués automatiquement. Sinon :

```powershell
cd c:\Users\MSI\fancapital\blockchain
$env:MINT_PRIVATE_KEY="0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a"
npm run grant-minter
```

```powershell
$env:BURN_PRIVATE_KEY="0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6"
npm run grant-burner
```

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

**Terminal (H2 par défaut) :**

```powershell
cd c:\Users\MSI\fancapital\backend
.\mvnw.cmd spring-boot:run
```

**Terminal (avec MariaDB) :**

```powershell
cd c:\Users\MSI\fancapital\backend
$env:SPRING_PROFILES_ACTIVE="mariadb"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="votre_mot_de_passe"
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

---

## Dépannage : AccessControlUnauthorizedAccount (burn failed)

Si le remboursement de crédit échoue avec :
```
burn failed: AccessControlUnauthorizedAccount("0x...", "0x3c11d16c...")
```

**Cause :** L'adresse de la Burn Key n'a pas le rôle `BURNER_ROLE` sur le contrat CashTokenTND.

**Solution :** Accorder le rôle BURNER (avec la même clé que le backend) :

```powershell
cd c:\Users\MSI\fancapital\blockchain
$env:BURN_PRIVATE_KEY="0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6"
npm run grant-burner
```

**Important :**
- Utilisez la même valeur de `BURN_PRIVATE_KEY` que celle configurée pour le backend (section 0 ou variables d'environnement IntelliJ).
- Le nœud blockchain (`npm run node`) doit être démarré avant d'exécuter `grant-burner`.
