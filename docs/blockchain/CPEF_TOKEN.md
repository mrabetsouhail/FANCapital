# Spécifications du Token CPEF

## Vue d'ensemble

Le **CPEF** (Certificat de Propriété Économique Fractionnée) est un Security Token conforme au standard **ERC-1404**, représentant une fraction d'actifs réels tokenisés sur la blockchain FAN-Capital.

---

## 1. Standard ERC-1404

### Conformité

Le token CPEF implémente le standard **ERC-1404** (Security Token avec restrictions de transfert), permettant :

- **Restrictions natives** : Vérification automatique avant chaque transfert
- **Conformité intégrée** : KYC, plafonds, restrictions réglementaires
- **Flexibilité** : Règles de transfert configurables par contrat

### Interface Principale

```solidity
interface IERC1404 {
    function detectTransferRestriction(
        address from,
        address to,
        uint256 amount
    ) external view returns (uint8);
    
    function messageForTransferRestriction(uint8 restrictionCode)
        external pure returns (string memory);
}
```

---

## 2. Architecture des Types de CPEF

Le système gère **deux types de CPEF** via des Smart Contracts ERC-1404 distincts :

### 2.1 CPEF-EQUITY-HIGH (Panier d'Actions - Rendement Élevé)

**Caractéristiques** :
- **Actifs sous-jacents** : Panier d'actions cotées tunisiennes à fort potentiel
- **Risque** : Très élevé
- **Rendement** : Élevé, variable selon performance marché
- **Spread** : Dynamique selon volatilité (plus élevé)
- **LTV pour avance** : 50%

**Exemple de composition** :
- Actions de croissance : 40%
- Actions bancaires : 30%
- Actions industrielles : 20%
- Actions autres : 10%

**Profil** : Pour investisseurs recherchant un rendement élevé avec acceptation du risque

### 2.2 CPEF-EQUITY-MEDIUM (Panier d'Actions - Rendement Moyen)

**Caractéristiques** :
- **Actifs sous-jacents** : Panier d'actions cotées tunisiennes équilibré
- **Risque** : Élevé (mais inférieur à HIGH)
- **Rendement** : Moyen, plus stable
- **Spread** : Dynamique selon volatilité (modéré)
- **LTV pour avance** : 50%

**Exemple de composition** :
- Actions bancaires : 35%
- Actions industrielles : 30%
- Actions services : 25%
- Actions autres : 10%

**Profil** : Pour investisseurs recherchant un équilibre rendement/risque

---

## 3. Propriétés du Token

### 3.1 Fractionnement

- **Précision** : 8 décimales (comme ETH)
- **Unité minimale** : 0.00000001 CPEF
- **Exemple** : Un actif de 1000 TND peut être divisé en 1 000 000 000 unités

### 3.2 Parité 1:1

**Principe** : Chaque token CPEF émis est couvert à 100% par des actifs réels

**Vérification** :
- **Total Supply** (blockchain) = **Actifs en réserve** (IB)
- Audit trimestriel externe
- Nœud d'audit CMF en temps réel

### 3.3 Valorisation (VNI)

**VNI** : Valeur Nette d'Inventaire

**Calcul** :
```
VNI = (Valeur totale des actifs - Passifs) / Nombre de tokens émis
```

**Mise à jour** :
- **Fréquence** : Quotidienne (ou selon besoin)
- **Oracle** : PriceOracle.sol
- **Source** : Dépositaire (IB)
- **Protection** : Oracle Guard (écart max 10% sans Multi-Sig)

---

## 4. Fonctions Principales

### 4.1 Mint (Émission)

**Fonction** : `mint(address to, uint256 amount)`

**Processus** :
1. Vérification KYC (Green/White List)
2. Vérification plafonds (si Green List)
3. Calcul prix avec spread dynamique
4. Vérification paiement TND
5. Émission tokens
6. Mise à jour PRM (Prix de Revient Moyen)
7. Émission événement `Mint`

**Restrictions** :
- Seul le contrat LiquidityPool peut appeler mint()
- Ou administrateur pour émission initiale

### 4.2 Burn (Rachat)

**Fonction** : `burn(uint256 amount)`

**Processus** :
1. Vérification solde utilisateur
2. Calcul VNI actuelle
3. Calcul PRM utilisateur
4. Calcul plus-value : `(VNI - PRM) × Quantité`
5. Calcul RAS selon résidence fiscale
6. Destruction tokens
7. Transfert TND vers Cash Wallet (net de fiscalité)
8. Transfert taxes vers TaxVault
9. Émission événement `Burn`

**Restrictions** :
- Seul le propriétaire peut brûler ses tokens
- Ou le contrat EscrowRegistry pour liquidation

### 4.3 Transfer

**Fonction** : `transfer(address to, uint256 amount)`

**Processus** :
1. Vérification restriction ERC-1404
2. Vérification KYC (from et to)
3. Vérification niveau (Green List : transferts bloqués)
4. Mise à jour PRM (transfert P2P)
5. Transfert tokens
6. Émission événement `Transfer`

**Restrictions** :
- Green List : Transferts P2P bloqués
- White List : Transferts autorisés
- Vérification plafonds si applicable

---

## 5. Gestion du PRM (Prix de Revient Moyen)

### Principe

Le **PRM** permet de calculer la plus-value réelle lors du rachat, optimisant la fiscalité.

### Calcul

**Lors d'un achat (Mint)** :
```
PRM_nouveau = (PRM_ancien × Quantité_ancienne + Prix_achat × Quantité_achetée) 
              / (Quantité_ancienne + Quantité_achetée)
```

**Lors d'un transfert P2P** :
```
PRM_acquéreur = PRM_vendeur (conservation du PRM)
```

### Stockage

- **Mapping** : `mapping(address => uint256) private prm`
- **Unité** : TND avec 8 décimales
- **Mise à jour** : Automatique à chaque mint/transfer

---

## 6. Restrictions de Transfert (ERC-1404)

### Codes de Restriction

| Code | Signification | Action |
|------|---------------|--------|
| 0 | Aucune restriction | Transfert autorisé |
| 1 | Adresse non whitelistée | Transfert bloqué |
| 2 | Plafond dépassé | Transfert bloqué |
| 3 | Transfert P2P non autorisé (Green List) | Transfert bloqué |
| 4 | Tokens en séquestre | Transfert bloqué |
| 5 | Contrat en pause | Transfert bloqué |

### Fonction de Détection

```solidity
function detectTransferRestriction(
    address from,
    address to,
    uint256 amount
) public view returns (uint8) {
    // Vérifications KYC
    // Vérifications plafonds
    // Vérifications séquestre
    // Retourne code de restriction
}
```

---

## 7. Double Portefeuille

### Token Balance

- **Type** : Solde de tokens CPEF
- **Unité** : CPEF (8 décimales)
- **Variation** : Selon VNI
- **Stockage** : Mapping standard ERC-20

### Liquidity Balance (Cash Wallet)

- **Type** : Solde en TND
- **Unité** : TND (8 décimales)
- **Source** : Rachats, coupons, remboursements
- **Utilisation** : Réinvestissement ou retrait bancaire
- **Stockage** : Mapping interne au contrat

---

## 8. Événements

### Événements Principaux

```solidity
event Mint(address indexed to, uint256 amount, uint256 price, uint256 prm);
event Burn(address indexed from, uint256 amount, uint256 vni, uint256 gain, uint256 tax);
event Transfer(address indexed from, address indexed to, uint256 amount);
event VNIUpdated(uint256 oldVNI, uint256 newVNI, uint256 timestamp);
event PRMUpdated(address indexed user, uint256 oldPRM, uint256 newPRM);
```

### Utilisation

- **Backend** : Écoute des événements pour synchronisation
- **Frontend** : Affichage en temps réel
- **Audit** : Traçabilité complète

---

## 9. Sécurité

### Protections Intégrées

1. **Reentrancy Guard** : Protection contre attaques de réentrance
2. **Overflow Protection** : SafeMath ou Solidity 0.8+
3. **Access Control** : Rôles et permissions
4. **Pause Mechanism** : Gel d'urgence
5. **Oracle Guard** : Protection contre VNI erronée

### Bonnes Pratiques

- **Audit de code** : Avant déploiement production
- **Tests exhaustifs** : Unitaires + Intégration
- **Upgradeability** : Proxy pattern si nécessaire
- **Monitoring** : Surveillance continue

---

## 10. Exemples d'Utilisation

### Exemple 1 : Achat de 1000 TND de CPEF-EQUITY-MEDIUM

```
1. Utilisateur demande achat 1000 TND
2. VNI actuelle : 125.50 TND
3. Spread : 0.2% (niveau Bronze)
4. Prix client : 125.50 × 1.002 = 125.751 TND
5. Commission : 1000 × 1% = 10 TND
6. TVA : 10 × 19% = 1.9 TND
7. Total : 1000 + 10 + 1.9 = 1011.9 TND
8. Tokens reçus : 1000 / 125.751 = 7.952 CPEF
9. PRM mis à jour : 125.751 TND
```

### Exemple 2 : Rachat de 7.952 CPEF

```
1. VNI actuelle : 130.00 TND
2. PRM utilisateur : 125.751 TND
3. Valeur brute : 7.952 × 130 = 1033.76 TND
4. Commission : 1033.76 × 1% = 10.34 TND
5. TVA : 10.34 × 19% = 1.96 TND
6. Plus-value : (130 - 125.751) × 7.952 = 33.79 TND
7. RAS (10%) : 33.79 × 10% = 3.38 TND
8. Montant net : 1033.76 - 10.34 - 1.96 - 3.38 = 1018.08 TND
9. Crédité sur Cash Wallet
```

---

## 11. Intégration avec Autres Contrats

### Dépendances

- **KYCRegistry** : Vérification KYC
- **LiquidityPool** : Calcul prix et exécution
- **PriceOracle** : Mise à jour VNI
- **TaxVault** : Destination taxes
- **EscrowRegistry** : Blocage pour avances

---

*Document créé le 26 janvier 2026*
*Version 1.0*
