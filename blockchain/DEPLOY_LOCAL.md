# Déploiement des contrats (réseau local)

## Prérequis

- Node.js et npm installés
- Dans le dossier `blockchain/` : `npm install` déjà exécuté

## 1. Démarrer le nœud blockchain

Dans un terminal, à la racine du projet :

```bash
cd blockchain
npm run node
```

Laisser ce terminal ouvert. Le nœud écoute sur **http://127.0.0.1:8545**.

## 2. Déployer les contrats (factory + fonds Atlas & Didon)

Dans un **second terminal** :

```bash
cd blockchain
npm run deploy:factory-funds:localhost
```

Ce script déploie :

- **Infra partagée :** KYCRegistry, InvestorRegistry, CashTokenTND, TaxVault, CircuitBreaker
- **CPEFFactory** et les 2 fonds : **CPEF Atlas** et **CPEF Didon**

Les adresses sont enregistrées dans `blockchain/deployments/localhost.factory-funds.json`.  
Le backend lit ce fichier pour connaître les adresses des contrats.

## 3. (Optionnel) Rôles et données de test

Après le déploiement, vous pouvez :

- **Minter du TND** pour les pools ou un wallet : `npx hardhat run scripts/mint-tnd.ts --network localhost`
- **Accorder les rôles** (minter, burner, oracle, panic, etc.) : ex. `npx hardhat run scripts/grant-minter-role.ts --network localhost`
- **Whitelist un utilisateur** : `npx hardhat run scripts/whitelist-user.ts --network localhost`

## Résumé des commandes

| Action              | Commande                                              |
|---------------------|--------------------------------------------------------|
| Démarrer le nœud    | `cd blockchain && npm run node`                       |
| Déployer contrats   | `cd blockchain && npm run deploy:factory-funds:localhost` |

Une fois le déploiement terminé, redémarrer le backend pour qu’il recharge les adresses depuis `localhost.factory-funds.json`.
