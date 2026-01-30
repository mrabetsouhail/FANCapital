# Grille Tarifaire Officielle - FAN-Capital

## Vue d'ensemble

Ce document définit l'intégralité des frais et commissions applicables aux opérations sur les certificats CPEF via la plateforme FAN-Capital.

---

## 1. Architecture des Portefeuilles

### Double Portefeuille

**Portefeuille de Tokens (CPEF)** :
- Contient les titres financiers tokenisés
- Valeur évolue selon la VNI
- Stockage : Blockchain

**Compte de Liquidité (Cash Wallet)** :
- Portefeuille interne en TND
- Net de fiscalité
- Utilisation : Réinvestissement ou retrait bancaire

---

## 2. Frais d'Accès au Service

### 2.1 Ouverture de Compte

**Standard (Bronze)** : **12 DT**
- Frais uniques
- Couvre KYC et initialisation du portefeuille

**Premium (Silver-Platinum)** : **Gratuit**
- Offert en promotion
- Même service KYC inclus

### 2.2 Frais de Garde

**Tous les niveaux** : **0 DT** (Gratuit)
- Aucun frais de garde
- Stockage gratuit sur blockchain

### 2.3 Frais de Transaction Réseau (Gas)

**Tous les niveaux** : **0 DT** (Pris en charge)
- Infrastructure gas-free
- Aucun coût pour l'utilisateur

---

## 3. Commissions de Transaction

### 3.1 Commissions Piscine (Achat/Rachat)

**Via Piscine de Liquidité** :

| Niveau | Commission | Avec TVA (19%) |
|--------|-----------|----------------|
| BRONZE | 1.00% | 1.19% |
| SILVER | 0.95% | 1.1305% |
| GOLD   | 0.90% | 1.071% |
| DIAMOND | 0.85% | 1.0115% |
| PLATINUM | 0.80% | 0.952% |

**Formule** : `Commission_réelle = Commission_niveau × (1 + 0.19)`

### 3.2 Commissions P2P (Marché Secondaire)

**Transferts Peer-to-Peer** :

| Niveau | Commission | Avec TVA (19%) |
|--------|-----------|----------------|
| BRONZE | 0.80% | 0.952% |
| SILVER | 0.75% | 0.8925% |
| GOLD   | 0.70% | 0.833% |
| DIAMOND | 0.60% | 0.714% |
| PLATINUM | 0.50% | 0.595% |

**Note** : P2P uniquement disponible pour White List

### 3.3 Support IA

| Niveau | Support IA |
|--------|-----------|
| BRONZE | Basique |
| SILVER | Avancé |
| GOLD   | Expert |
| DIAMOND | Illimité |
| PLATINUM | Dédié |

---

## 4. Absence de Pénalités

### 4.1 Pénalité de Sortie

**Tous les niveaux** : **Aucune pénalité**
- Liquidité totale dès le jour 1
- Rachat possible à tout moment
- Aucun frais de durée de détention

### 4.2 Frais Supplémentaires

**Aucun frais caché** :
- Pas de frais de clôture
- Pas de frais d'inactivité
- Pas de frais de maintenance

---

## 5. Fiscalité

### 5.1 Retenue à la Source (RAS)

**Moment** : Prélevée lors du rachat via la Piscine (Burn)

**Calcul** :
```
Plus-value = (VNI_sortie - PRM) × Quantité
RAS = Plus-value × Taux_RAS
```

**Taux** :
- **Résident Tunisien** : 10%
- **Non-Résident** : 15% (sauf convention bilatérale)

**Base imposable** : Uniquement sur la plus-value réelle

**Important (P2P)** :
- **Aucune RAS n’est prélevée lors des transactions P2P**.
- La fiscalité (RAS) est **différée** et appliquée uniquement lors du **rachat final via la Piscine**.
- Objectif : maximiser la liquidité du marché secondaire et éviter la complexité de calcul du gain vendeur en temps réel.

### 5.2 TVA sur Commissions

**Taux** : 19%

**Application** : Sur toutes les commissions
- Commissions piscine
- Commissions P2P

**Inclusion** : Automatique dans le calcul final

### 5.3 Disponibilité Nette

**Après rachat** :
- Montant sur Cash Wallet = Net de fiscalité
- RAS déjà prélevée
- TVA déjà incluse
- Retrait bancaire sans prélèvement supplémentaire

---

## 6. Exemples de Simulation

### 6.1 Achat de 1,000 TND (Niveau Bronze)

**Données** :
- VNI : 125.50 TND
- Spread : 0.2%
- Commission : 1.00%

**Calcul** :
1. Prix avec spread : 125.50 × 1.002 = **125.751 TND**
2. Tokens reçus : 1000 / 125.751 = **7.952 CPEF**
3. Commission : 1000 × 1% = **10.00 TND**
4. TVA : 10.00 × 19% = **1.90 TND**
5. **Total à payer** : 1000 + 10 + 1.90 = **1,011.90 TND**

### 6.2 Rachat de 1,000 TND (Niveau Bronze, Résident)

**Données** :
- VNI sortie : 130.00 TND
- PRM utilisateur : 125.751 TND
- Commission : 1.00%
- RAS : 10%

**Calcul** :
1. Tokens à racheter : 1000 / 130 = **7.692 CPEF**
2. Valeur brute : 7.692 × 130 = **1,000.00 TND**
3. Commission : 1000 × 1% = **10.00 TND**
4. TVA : 10.00 × 19% = **1.90 TND**
5. Plus-value : (130 - 125.751) × 7.692 = **32.68 TND**
6. RAS : 32.68 × 10% = **3.27 TND**
7. **Montant net crédité** : 1000 - 10 - 1.90 - 3.27 = **984.83 TND**

### 6.3 Achat de 1,000 TND (Niveau Platinum)

**Données** :
- VNI : 125.50 TND
- Spread : 0.16% (réduction -20%)
- Commission : 0.80%

**Calcul** :
1. Prix avec spread : 125.50 × 1.0016 = **125.701 TND**
2. Tokens reçus : 1000 / 125.701 = **7.955 CPEF**
3. Commission : 1000 × 0.80% = **8.00 TND**
4. TVA : 8.00 × 19% = **1.52 TND**
5. **Total à payer** : 1000 + 8 + 1.52 = **1,009.52 TND**

**Économie vs Bronze** : 2.38 TND (0.24%)

---

## 7. Comparatif par Niveau

### 7.1 Coût Total Achat 1,000 TND

| Niveau | Commission | TVA | Total | Économie vs Bronze |
|--------|-----------|-----|-------|-------------------|
| BRONZE | 10.00 | 1.90 | 1,011.90 | - |
| SILVER | 9.50 | 1.81 | 1,011.31 | 0.59 DT |
| GOLD   | 9.00 | 1.71 | 1,010.71 | 1.19 DT |
| DIAMOND | 8.50 | 1.62 | 1,010.12 | 1.78 DT |
| PLATINUM | 8.00 | 1.52 | 1,009.52 | 2.38 DT |

### 7.2 Coût Total Rachat 1,000 TND (sans RAS)

| Niveau | Commission | TVA | Total | Économie vs Bronze |
|--------|-----------|-----|-------|-------------------|
| BRONZE | 10.00 | 1.90 | 11.90 | - |
| SILVER | 9.50 | 1.81 | 11.31 | 0.59 DT |
| GOLD   | 9.00 | 1.71 | 10.71 | 1.19 DT |
| DIAMOND | 8.50 | 1.62 | 10.12 | 1.78 DT |
| PLATINUM | 8.00 | 1.52 | 9.52 | 2.38 DT |

---

## 8. Transparence Tarifaire

### 8.1 Affichage

**Avant transaction** :
- Prix d'exécution
- Commission détaillée
- TVA incluse
- Total à payer

**Après transaction** :
- Détail complet
- Reçu électronique
- Historique disponible

### 8.2 Calcul Automatique

**Smart Contracts** :
- Calculs automatiques
- Transparence totale
- Pas de frais cachés
- Traçabilité blockchain

---

## 9. Conditions Spéciales

### 9.1 Promotions

**Frais d'ouverture** :
- Premium : Gratuit (promotionnel)
- Standard : 12 DT

**Réductions temporaires** :
- Campagnes promotionnelles
- Conditions spécifiques

### 9.2 Volume

**Pas de réduction volume** :
- Tarifs identiques quel que soit le volume
- Transparence pour tous

---

## 10. Option de Réservation (sur stock CPEF)

### 10.1 Définition

L’**Option de Réservation** permet à un utilisateur de **bloquer un prix d’achat** \(K\) (fixé à la VNI au moment \(t_0\)) pour acheter une quantité \(Q\) de CPEF avant une échéance \(T\), en payant une **avance / prime** \(A\) à \(t_0\).

### 10.2 Calcul de l’avance (prime) \(A\)

Référence (note d’ingénierie) :
\[
A = (K \times Q) \times \left(r_{\text{base}} + \sigma_{\text{adj}}\right)
\]

- \(K\) : prix d’exercice (VNI à \(t_0\))
- \(Q\) : quantité réservée
- \(r_{\text{base}}\) : taux de réservation (ex : 5%)
- \(\sigma_{\text{adj}}\) : ajustement lié à la volatilité (en bps/%, selon le module de risque)

### 10.3 Règles de facturation & TVA

- **Avance \(A\)** : **non remboursable** (prime) ; elle rémunère le service de réservation et la mobilisation de stock.
- **TVA (19%)** : par défaut, **appliquée sur \(A\)** (considérée comme frais/commission de service).
  - **Note** : si le traitement fiscal de la prime d’option diffère (produit financier vs service), ce point est à **valider** (juridique/fiscalité).
- **RAS (retenue à la source)** : **non applicable** sur \(A\) (ce n’est pas une plus-value de cession). La RAS continue de s’appliquer uniquement aux plus-values lors d’un rachat (Burn) selon les règles du §5.

### 10.4 Flux financiers (résumé)

- **À \(t_0\) (réservation)** :
  - Débit utilisateur : \(A\) (+ TVA si applicable)
  - Crédit plateforme : \(A\) (vers `Treasury` / `TreasuryVault`)
  - Stock plateforme : \(Q\) jetons **verrouillés** (réservés)

- **À \(T\) (exercice)** :
  - L’utilisateur paie le reliquat : \(R = (K \times Q) - A\)
  - La plateforme **livre** les jetons réservés à l’utilisateur
  - Les frais de transaction “achat” classiques (piscine) peuvent être :
    - **Option A (recommandée)** : inclus dans \(A\) (pricing “tout compris”)
    - **Option B** : ajoutés au moment de l’exercice (mêmes règles que §3.1)

- **Annulation / non-exercice** :
  - L’utilisateur perd \(A\) (prime conservée par la plateforme)
  - Le stock \(Q\) est **libéré** vers la piscine de liquidité

### 10.5 Exemple (illustratif)

Hypothèses :
- \(K = 125.50\) TND
- \(Q = 10\) CPEF
- \(r_{\text{base}} = 5\%\)
- \(\sigma_{\text{adj}} = 1\%\)

Calcul :
- Notionnel \(K \times Q = 1,255.00\) TND
- \(A = 1,255.00 \times (0.05 + 0.01) = 75.30\) TND
- TVA (19%) sur \(A\) : \(14.31\) TND
- Total payé à \(t_0\) : \(89.61\) TND

À l’exercice :
- \(R = 1,255.00 - 75.30 = 1,179.70\) TND

---

## 11. Récapitulatif

### Frais Uniques

- **Ouverture Bronze** : 12 DT
- **Ouverture Premium** : Gratuit

### Frais Récurrents

- **Garde** : 0 DT (gratuit)
- **Gas** : 0 DT (gratuit)

### Commissions

- **Piscine** : 0.80% à 1.00% selon niveau
- **P2P** : 0.50% à 0.80% selon niveau
- **TVA** : 19% sur toutes les commissions

### Fiscalité

- **RAS Résident** : 10% sur plus-value
- **RAS Non-Résident** : 15% sur plus-value

### Pénalités

- **Aucune** : Liquidité totale, pas de pénalité de durée

---

*Document créé le 26 janvier 2026*
*Version 1.0 - Grille Tarifaire Officielle*
