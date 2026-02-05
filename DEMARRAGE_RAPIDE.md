# Guide de Démarrage Rapide - FAN-Capital

## 🚀 Démarrage des Services

### Option 1 : Script Automatique (Recommandé)

```powershell
# Démarrer tous les services
.\start-all.ps1

# Arrêter tous les services
.\stop-all.ps1
```

### Option 2 : Démarrage Manuel

#### 1. Blockchain (Hardhat Node)

```powershell
cd blockchain
npm run node
```

**Port** : `8545`  
**URL** : `http://localhost:8545`

Le nœud Hardhat démarre avec 20 comptes de test pré-approvisionnés.

#### 2. Backend (Spring Boot)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Ou si Maven est installé globalement :
```powershell
cd backend
mvn spring-boot:run
```

**Port** : `8081`  
**URL** : `http://localhost:8081`  
**API Docs** : `http://localhost:8081/api`

#### 3. Frontend (Angular)

```powershell
cd frontend
npm start
```

**Port** : `4200`  
**URL** : `http://localhost:4200`

---

## 📋 Prérequis

### Blockchain
- Node.js 18+ et npm
- Dépendances installées : `cd blockchain && npm install`

### Backend
- Java 17+
- Maven (ou utilisez `mvnw.cmd`)
- Variables d'environnement optionnelles (voir `backend/src/main/resources/application.yml`)

### Frontend
- Node.js 18+ et npm
- Dépendances installées : `cd frontend && npm install`

---

## 🔧 Configuration

### Variables d'Environnement (Backend)

Les variables suivantes sont optionnelles pour le développement :

```powershell
# JWT Secret (requis pour l'authentification)
$env:JWT_SECRET="votre-secret-jwt-48-caracteres-minimum"

# Clé de chiffrement des wallets (requis pour créer des wallets)
$env:WALLET_ENC_KEY="<clé-base64-32-octets>"

# Clé privée de l'opérateur blockchain (requis pour les transactions)
$env:OPERATOR_PRIVATE_KEY="<clé-privée-hex>"
```

### Configuration Blockchain

Le backend cherche automatiquement les fichiers de déploiement dans :
- `blockchain/deployments/localhost.council-funds.json`
- `blockchain/deployments/localhost.factory-funds.json`
- `blockchain/deployments/localhost.json`

---

## ✅ Vérification

Après le démarrage, vérifiez que les services sont actifs :

1. **Blockchain** : Ouvrez `http://localhost:8545` (devrait retourner une erreur JSON-RPC, c'est normal)
2. **Backend** : Ouvrez `http://localhost:8081/api/auth/me` (devrait retourner une erreur 401, c'est normal)
3. **Frontend** : Ouvrez `http://localhost:4200` (devrait afficher l'interface)

---

## 🐛 Dépannage

### Port déjà utilisé

Si un port est déjà utilisé :

**Blockchain (8545)** :
```powershell
# Trouver le processus
Get-NetTCPConnection -LocalPort 8545 | Select-Object OwningProcess
# Arrêter le processus
Stop-Process -Id <PID>
```

**Backend (8081)** :
Modifiez `backend/src/main/resources/application.yml` :
```yaml
server:
  port: 8082  # Changez le port
```

**Frontend (4200)** :
```powershell
cd frontend
npm start -- --port 4300
```

### Erreurs de compilation

**Backend** :
```powershell
cd backend
.\mvnw.cmd clean compile
```

**Frontend** :
```powershell
cd frontend
npm install
npm run build
```

**Blockchain** :
```powershell
cd blockchain
npm install
npm run compile
```

---

## 📚 Documentation

- **Architecture Blockchain** : `docs/blockchain/ARCHITECTURE.md`
- **Livre Blanc v2.1** : `docs/blockchain/Livre Blanc FAN-Capital v2.1 Finale.md`
- **API Backend** : Voir les contrôleurs dans `backend/src/main/java/com/fancapital/backend`

---

## 🛑 Arrêt des Services

### Script Automatique
```powershell
.\stop-all.ps1
```

### Manuel
- Appuyez sur `Ctrl+C` dans chaque fenêtre de terminal
- Ou fermez les fenêtres PowerShell ouvertes

---

## 📝 Notes

- Les services démarrent en **arrière-plan** avec les scripts automatiques
- Attendez **10-15 secondes** que tous les services soient complètement démarrés
- Le frontend se recharge automatiquement lors des modifications (hot-reload)
- Le backend nécessite un redémarrage après modification du code Java
