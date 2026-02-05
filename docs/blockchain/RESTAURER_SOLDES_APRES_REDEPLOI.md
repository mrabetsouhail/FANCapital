# Restaurer les soldes (cash + tokens) après un redéploiement

## Pourquoi les soldes sont à zéro ?

Les **soldes Cash Wallet et Token Wallet** sont lus **sur la blockchain**, pas en base de données. Dès que vous :

1. Redémarrez le nœud Hardhat (`npm run node`),
2. Puis redéployez les contrats (`npm run deploy:factory-funds:localhost`),

toute la chaîne repart à zéro : plus de TND, plus de tokens CPEF, plus de whitelist. Les comptes utilisateurs en base (H2 ou MariaDB) existent toujours, mais leurs adresses wallet n’ont plus de solde on-chain, donc l’app affiche 0.

## Étapes pour recréer des données de test

**Prérequis :** nœud Hardhat en cours d’exécution et contrats déjà déployés (`localhost.factory-funds.json` à jour).

### 1. Donner le rôle Minter au déployeur (si besoin)

Le déployeur Hardhat (compte #0) doit pouvoir mint du TND. Si ce n’est pas déjà fait :

```bash
cd blockchain
npx hardhat run scripts/grant-minter-role.ts --network localhost
```

(Le script `mint-tnd-for-testing.ts` peut aussi accorder le rôle lui‑même.)

### 2. Mint TND pour un utilisateur + les pools

Remplacez `0xVOTRE_ADRESSE_WALLET` par l’adresse du wallet affichée dans l’app (ou celle stockée en base pour l’utilisateur).

**PowerShell :**

```powershell
cd blockchain
$env:USER_ADDRESS="0xVOTRE_ADRESSE_WALLET"
$env:USER_AMOUNT="5000"
$env:POOL_AMOUNT="5000"
npx hardhat run scripts/mint-tnd-for-testing.ts --network localhost
```

Cela crédite :
- **5 000 TND** au wallet utilisateur (cash pour acheter des tokens),
- **5 000 TND** à chaque pool (liquidité pour les ventes).

### 3. Whitelist l’utilisateur (KYC)

Sans whitelist, l’utilisateur ne peut pas acheter/vendre via la piscine :

```powershell
$env:USER_ADDRESS="0xVOTRE_ADRESSE_WALLET"
$env:KYC_LEVEL="1"
$env:IS_RESIDENT="true"
npx hardhat run scripts/whitelist-user.ts --network localhost
```

(`KYC_LEVEL=1` = Green List, uniquement piscine ; `2` = White List, piscine + P2P.)

### 4. Vérifier dans l’app

1. Redémarrer le backend si besoin (pour qu’il relise la chaîne).
2. Se connecter avec le compte dont l’adresse a été utilisée ci‑dessus.
3. Aller sur « Passer ordre » ou « Portfolio » : le **Cash Wallet** doit afficher 5 000 TND (ou le montant choisi). Les **tokens** apparaîtront après un ou plusieurs achats via la piscine.

## Plusieurs utilisateurs de test

Répéter les étapes 2 et 3 pour chaque adresse :

```powershell
$env:USER_ADDRESS="0xAdresseUser1"
npx hardhat run scripts/mint-tnd-for-testing.ts --network localhost
npx hardhat run scripts/whitelist-user.ts --network localhost

$env:USER_ADDRESS="0xAdresseUser2"
npx hardhat run scripts/mint-tnd-for-testing.ts --network localhost
npx hardhat run scripts/whitelist-user.ts --network localhost
```

## Récapitulatif

| Donnée              | Où elle est stockée | Après redéploi |
|---------------------|----------------------|----------------|
| Comptes / emails    | Base (H2 ou MariaDB)| Conservés      |
| Lien user ↔ wallet  | Base                 | Conservé       |
| Solde TND (cash)    | Blockchain           | **0** → à recréer avec mint |
| Tokens CPEF         | Blockchain           | **0** → à recréer en faisant des achats |
| Whitelist KYC       | Blockchain           | **Vide** → à recréer avec whitelist-user |

En refaisant **mint TND** + **whitelist** pour les adresses utilisées dans l’app, les soldes Cash Wallet (et ensuite Token Wallet après achats) reviennent comme avant en environnement de test.
