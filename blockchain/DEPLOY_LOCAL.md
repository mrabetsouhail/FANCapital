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
- **AST (Avance sur Titres) :** PriceOracle partagé, EscrowRegistry, CreditModelA (avec VNI Atlas 10 TND, Didon 5 TND)

Les adresses sont enregistrées dans `blockchain/deployments/localhost.factory-funds.json`.  
Le backend lit ce fichier pour connaître les adresses des contrats.

## 3. Rôles : accorder MINTER_ROLE à l’opérateur backend

Pour que l’**alimentation du Cash Wallet** (bouton « Alimenter 10 000 TND » ou auto-seed) fonctionne, l’adresse liée à `OPERATOR_PRIVATE_KEY` (celle du backend) doit avoir le rôle **MINTER_ROLE** sur le contrat CashTokenTND.

Exécutez, avec le nœud blockchain et les contrats déjà déployés :

```powershell
cd blockchain
$env:OPERATOR_PRIVATE_KEY="<votre clé privée opérateur - la même que dans application.yml>"
npm run grant-minter
```

Ou si vous préférez passer l’adresse directement :

```powershell
$env:MINT_KEY_ADDRESS="0x..."
npm run grant-minter
```

## 4. Alimenter le wallet en ETH (pour le gas)

Sur Hardhat local, seuls les 20 comptes par défaut ont de l’ETH. Les wallets utilisateurs (WaaS, MetaMask) n’en ont pas — d’où l’erreur :

> `approve failed: Sender doesn't have enough funds to send tx. The max upfront cost is: ... and the sender's balance is: 0.`

Alimentez le wallet avec un peu d’ETH natif :

```powershell
cd blockchain
$env:USER_ADDRESS="0x4ebe7059efce49f3dcfe4b48080a90c94c6b30bf"  # adresse du wallet concerné
npm run fund-eth
```

Optionnel : `$env:AMOUNT_ETH="0.1"` pour envoyer 0.1 ETH (par défaut 0.05 ETH).

## 5. (Optionnel) Autres scripts de test

Après le déploiement, vous pouvez :

- **Minter du TND** pour les pools : `npx hardhat run scripts/mint-tnd.ts --network localhost`
- **Whitelist un utilisateur** : `npx hardhat run scripts/whitelist-user.ts --network localhost`

## Résumé des commandes

| Action                        | Commande                                                              |
|-------------------------------|-----------------------------------------------------------------------|
| Démarrer le nœud              | `cd blockchain && npm run node`                                       |
| Déployer contrats             | `cd blockchain && npm run deploy:factory-funds:localhost`              |
| Accorder MINTER à la Mint Key | `$env:MINT_PRIVATE_KEY="0x..."; npm run grant-minter`                 |
| Accorder BURNER à la Burn Key | `$env:BURN_PRIVATE_KEY="0x..."; npm run grant-burner`                 |
| Alimenter wallet en ETH (gas) | `$env:USER_ADDRESS="0x..."; npm run fund-eth`                         |

**Remarque :** Si vous utilisez une seule clé (MINT = OPERATOR) et que c'est la clé du premier compte Hardhat, vous avez déjà `MINTER_ROLE` et l'étape 3 est inutile.

**AST :** Si le Credit Wallet reste vide après une demande d'avance, redéployez (`npm run deploy:factory-funds:localhost`) puis redémarrez le backend. CreditModelA et EscrowRegistry sont inclus dans le déploiement factory.

Une fois le déploiement terminé, redémarrer le backend pour qu’il recharge les adresses depuis `localhost.factory-funds.json`.
