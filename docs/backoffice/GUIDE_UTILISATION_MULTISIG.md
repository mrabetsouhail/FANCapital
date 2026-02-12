# Guide d'Utilisation - Gouvernance Multi-Sig

## Vue d'ensemble

L'interface **Gouvernance Multi-Sig** permet de gérer les propositions de transaction du `MultiSigCouncil`. Selon le Dossier de Sécurité Institutionnelle v2.0, le Multi-Sig utilise un protocole **N/M** (par exemple 3/5), où **N** signatures sont requises parmi **M** signataires pour exécuter une transaction.

---

## 1. Comprendre le Tableau de Bord

### 1.1 Informations du Council (Cartes en haut)

Quatre cartes affichent l'état actuel du MultiSigCouncil :

#### **Adresse Council**
- **Qu'est-ce que c'est ?** L'adresse blockchain du contrat `MultiSigCouncil` déployé.
- **Exemple :** `0x21dF544947ba3E8b3c32561399E88B52Dc8b2823`
- **Utilité :** Identifier le contrat spécifique que vous gérez.

#### **Seuil Requis**
- **Format :** `X / Y` (exemple : `3 / 5`)
- **Signification :**
  - **X** = Nombre de signatures nécessaires pour exécuter une transaction
  - **Y** = Nombre total de signataires
- **Exemple :** `3 / 5` signifie qu'il faut **3 signatures** parmi **5 signataires** pour valider une proposition.

#### **Signataires**
- **Qu'est-ce que c'est ?** Le nombre total d'adresses autorisées à signer des propositions.
- **Utilité :** Vérifier combien de personnes/entités peuvent participer à la gouvernance.

#### **Total Propositions**
- **Qu'est-ce que c'est ?** Le nombre total de propositions de transaction (en attente + exécutées).
- **Utilité :** Suivre l'activité du council.

### 1.2 Liste des Signataires (Owners)

Cette section liste toutes les adresses blockchain qui sont **propriétaires** du MultiSigCouncil.

- **Format :** Adresses tronquées (exemple : `0x21dF...b2823`)
- **Utilité :** Vérifier qui peut signer les propositions.

---

## 2. Soumettre une Nouvelle Proposition

### 2.1 Ouvrir le Formulaire

1. Cliquez sur le bouton **"Nouvelle Proposition"** (en haut à droite).
2. Le formulaire apparaît sous les informations du council.

### 2.2 Remplir le Formulaire

#### **Champ 1 : Adresse Destination (to)**
- **Qu'est-ce que c'est ?** L'adresse blockchain qui recevra la transaction.
- **Format requis :** `0x` suivi de 40 caractères hexadécimaux (exemple : `0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb`)
- **Exemples d'utilisation :**
  - Transférer des fonds vers un portefeuille : `0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb`
  - Appeler une fonction d'un smart contract : Adresse du contrat
- **Validation :** Le système vérifie que l'adresse est valide (format hexadécimal, 42 caractères).

#### **Champ 2 : Montant (wei)**
- **Qu'est-ce que c'est ?** La quantité d'Ether (ou de token natif) à envoyer avec la transaction.
- **Unité :** **Wei** (la plus petite unité d'Ether)
- **Conversion :**
  - `1 ETH = 1 000 000 000 000 000 000 wei` (1e18)
  - `0.1 ETH = 100 000 000 000 000 000 wei` (1e17)
  - `0.001 ETH = 1 000 000 000 000 000 wei` (1e15)
- **Exemples :**
  - Pour envoyer **1 ETH** : `1000000000000000000`
  - Pour envoyer **0.5 ETH** : `500000000000000000`
  - Pour envoyer **0 TND** (transaction sans valeur) : `0`
- **Note :** Si vous transférez uniquement des tokens ERC-20, mettez `0` et utilisez le champ **Calldata**.

#### **Champ 3 : Calldata (hex, optionnel)**
- **Qu'est-ce que c'est ?** Données encodées en hexadécimal pour appeler une fonction de smart contract.
- **Quand l'utiliser ?**
  - Appeler une fonction d'un smart contract (exemple : `transfer()`, `approve()`, `mint()`)
  - Transférer des tokens ERC-20
  - Exécuter une logique complexe
- **Format :** Commence par `0x` suivi de données hexadécimales.
- **Exemple simple :** `0x` (vide) pour un simple transfert d'Ether.
- **Comment obtenir le calldata ?**
  - Utiliser des outils comme [MyEtherWallet](https://www.myetherwallet.com/tools/interact) ou [Remix](https://remix.ethereum.org/)
  - Encoder via Web3.js/Ethers.js dans votre code
- **Cas d'usage typiques :**
  - **Transfert de tokens ERC-20 :** Encoder l'appel à `transfer(address, uint256)`
  - **Appel de fonction admin :** Encoder l'appel à une fonction spécifique du contrat

### 2.3 Soumettre la Proposition

1. Vérifiez que tous les champs sont corrects.
2. Cliquez sur **"Soumettre"**.
3. La proposition est créée et apparaît dans la section **"Propositions en Attente"**.
4. Un hash de transaction est généré (visible dans les logs backend).

---

## 3. Confirmer une Proposition

### 3.1 Quand Confirmer ?

- Une proposition est **en attente** (non exécutée).
- Vous êtes un **signataire** (votre adresse est dans la liste des Owners).
- La proposition n'a pas encore atteint le **seuil requis**.

### 3.2 Comment Confirmer ?

1. Dans la section **"Propositions en Attente"**, trouvez la proposition souhaitée.
2. Vérifiez les détails :
   - **Transaction #X** : ID de la proposition
   - **Destination** : Adresse qui recevra la transaction
   - **Valeur** : Montant en ETH
   - **Confirmations** : `X / Y` (exemple : `1 / 3`)
3. Cliquez sur le bouton **"Confirmer"** (bleu).
4. La transaction de confirmation est envoyée à la blockchain.
5. Le compteur de confirmations s'incrémente.

### 3.3 État des Confirmations

- **Badge jaune** : `X / Y confirmations` → En attente de plus de signatures
- **Badge vert "Prête à exécuter"** : Le seuil est atteint, la proposition peut être exécutée

---

## 4. Exécuter une Proposition

### 4.1 Quand Exécuter ?

- La proposition a atteint le **seuil requis** (exemple : 3/5 confirmations).
- Le badge **"Prête à exécuter"** est affiché.
- La proposition n'a pas encore été exécutée.

### 4.2 Comment Exécuter ?

1. Trouvez la proposition avec le badge **"Prête à exécuter"**.
2. **⚠️ ATTENTION :** L'exécution est **irréversible** !
3. Cliquez sur le bouton **"Exécuter"** (vert).
4. Confirmez dans la popup de confirmation.
5. La transaction est exécutée sur la blockchain.
6. La proposition passe dans la section **"Transactions Exécutées"**.

---

## 5. Consulter les Transactions

### 5.1 Propositions en Attente

- **Affichage :** Toutes les propositions non exécutées.
- **Informations affichées :**
  - ID de la transaction
  - Adresse destination (tronquée)
  - Montant en ETH (et en wei)
  - Calldata (tronqué si trop long)
  - Nombre de confirmations
  - Boutons d'action (Confirmer/Exécuter)

### 5.2 Transactions Exécutées

- **Affichage :** Toutes les propositions qui ont été exécutées.
- **Style :** Fond gris pour indiquer qu'elles sont terminées.
- **Informations affichées :**
  - ID de la transaction
  - Adresse destination
  - Montant en ETH
  - Badge "Exécutée"

---

## 6. Workflow Complet : Exemple Pratique

### Scénario : Transférer 1 ETH vers un portefeuille

**Configuration :** Multi-Sig 3/5 (3 signatures requises parmi 5 signataires)

#### **Étape 1 : Soumettre la Proposition**
1. Admin A ouvre l'interface Multi-Sig.
2. Clique sur **"Nouvelle Proposition"**.
3. Remplit le formulaire :
   - **Destination :** `0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb`
   - **Montant :** `1000000000000000000` (1 ETH)
   - **Calldata :** `0x` (vide, car simple transfert)
4. Clique sur **"Soumettre"**.
5. La proposition #1 apparaît avec `0 / 3 confirmations`.

#### **Étape 2 : Confirmer (Signataire 1)**
1. Admin B (signataire) voit la proposition #1.
2. Vérifie les détails et clique sur **"Confirmer"**.
3. La proposition affiche maintenant `1 / 3 confirmations`.

#### **Étape 3 : Confirmer (Signataire 2)**
1. Admin C (signataire) confirme également.
2. La proposition affiche `2 / 3 confirmations`.

#### **Étape 4 : Confirmer (Signataire 3)**
1. Admin D (signataire) confirme.
2. La proposition affiche `3 / 3 confirmations`.
3. Le badge **"Prête à exécuter"** apparaît.

#### **Étape 5 : Exécuter**
1. N'importe quel signataire (Admin A, B, C, D, ou E) peut exécuter.
2. Clique sur **"Exécuter"**.
3. Confirme dans la popup.
4. La transaction est exécutée sur la blockchain.
5. 1 ETH est transféré vers `0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb`.
6. La proposition passe dans **"Transactions Exécutées"**.

---

## 7. Cas d'Usage Avancés

### 7.1 Transférer des Tokens ERC-20

**Exemple :** Transférer 100 TND (CashTokenTND) vers un portefeuille.

1. **Destination :** Adresse du contrat `CashTokenTND` (exemple : `0x...`)
2. **Montant :** `0` (pas de valeur native)
3. **Calldata :** Encoder l'appel à `transfer(address to, uint256 amount)`
   - Fonction : `transfer`
   - Paramètres :
     - `to` : Adresse du destinataire
     - `amount` : `100000000000` (100 TND avec 8 décimales = 100 * 1e8)

**Calldata encodé (exemple) :**
```
0xa9059cbb000000000000000000000000742d35cc6634c0532925a3b844bc9e7595f0beb0000000000000000000000000000000000000000000000000000000174876e800
```

### 7.2 Appeler une Fonction Admin

**Exemple :** Changer le seuil du Multi-Sig de 3 à 4.

1. **Destination :** Adresse du `MultiSigCouncil`
2. **Montant :** `0`
3. **Calldata :** Encoder l'appel à `changeThreshold(uint256 newThreshold)`
   - Paramètre : `4`

---

## 8. Bonnes Pratiques

### ✅ À FAIRE

- **Vérifier les détails** avant de confirmer ou exécuter une proposition.
- **Communiquer** avec les autres signataires pour coordonner les confirmations.
- **Documenter** les propositions importantes (pourquoi cette transaction ?).
- **Rafraîchir** régulièrement la page pour voir les dernières mises à jour.
- **Vérifier l'adresse destination** avant de soumettre (copier-coller depuis une source fiable).

### ❌ À ÉVITER

- **Ne pas exécuter** une proposition sans avoir vérifié qu'elle a atteint le seuil.
- **Ne pas confirmer** des propositions suspectes sans validation.
- **Ne pas utiliser** des adresses non vérifiées.
- **Ne pas ignorer** les erreurs affichées (lire les messages d'erreur).

---

## 9. Dépannage

### Problème : "Governance key not configured"
**Solution :** Vérifier que `blockchain.operator-private-key` est configurée dans les variables d'environnement.

### Problème : "Transaction confirmed" mais le compteur ne s'incrémente pas
**Solution :** Rafraîchir la page. La blockchain peut prendre quelques secondes pour mettre à jour.

### Problème : Impossible d'exécuter malgré le seuil atteint
**Solution :** Vérifier que la transaction n'a pas déjà été exécutée. Vérifier les logs backend pour plus de détails.

### Problème : Erreur "Invalid address format"
**Solution :** Vérifier que l'adresse commence par `0x` et contient exactement 40 caractères hexadécimaux.

---

## 10. Sécurité

### Conformité au Dossier de Sécurité v2.0

- **Micro-segmentation :** Le Multi-Sig est isolé des autres clés (Mint, Burn, Oracle, etc.).
- **Gouvernance distribuée :** Aucun point de défaillance unique (SPoF).
- **Audit trail :** Toutes les propositions et confirmations sont enregistrées sur la blockchain.
- **Seuil configurable :** Le seuil peut être ajusté selon les besoins (exemple : 3/5, 4/5, 5/5).

### Recommandations

- **Stockage des clés :** Les clés de gouvernance doivent être stockées dans un HSM (Hardware Security Module) en production.
- **Distribution des signataires :** Les 5 signataires doivent être répartis géographiquement et organisationnellement.
- **Rotation des clés :** Prévoir une procédure de rotation des clés de gouvernance.

---

## 11. Support

Pour toute question ou problème :
1. Vérifier les logs backend (`logs/application.log`).
2. Vérifier l'état de la blockchain (Hardhat node ou réseau de production).
3. Consulter le Dossier de Sécurité Institutionnelle v2.0.

---

**Dernière mise à jour :** Février 2026
