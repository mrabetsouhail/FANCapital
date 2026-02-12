# Avance sur Titres (Crédit Lombard) - FAN-Capital v4.51

## Vue d'ensemble

L'avance sur titres permet aux utilisateurs d'obtenir des liquidités immédiates sans liquider leur portefeuille, optimisant ainsi leur fiscalité et leur rendement.

---

## 1. Conditions d'Éligibilité

L'accès au crédit est un **privilège réservé aux membres actifs** de la communauté :

| Critère | Exigence |
|---------|----------|
| **Abonnement Premium** | Obligatoire pour accéder à l'interface de prêt |
| **Statut KYC** | Minimum KYC 1 (Green List) |
| **Circuit Fermé** | Les fonds sont versés exclusivement dans le **Credit Wallet** |

**Restriction majeure** : L'option "Cash-Out" vers une banque externe est **inexistante**. La liquidité reste investie dans les actifs CPEF, garantissant l'adossement total et la conformité réglementaire.

---

## 2. Caractéristiques Générales

### 2.1 Objectif

**Liquidité immédiate** sans liquidation du portefeuille :
- Conservation des titres
- Bénéfice de l'appréciation
- Optimisation fiscale
- Flexibilité financière

### 2.2 Disponibilité par Tier

**Niveaux** : Premium uniquement, selon Score SCI (voir Modèle A et B)

---

## 3. Types d'Avance

### 3.1 Modèle A : Avance à Taux Fixe (Dégressif)

Ce modèle permet d'augmenter sa capacité d'investissement à un **coût prévisible**. Le taux **diminue** à mesure que l'investisseur monte en grade (Score SCI).

| Niveau (Tier) | Seuil SCI | Taux Annuel (Modèle A) | Usage Autorisé |
|---------------|-----------|------------------------|----------------|
| BRONZE | 0 - 15 | ❌ Non disponible | - |
| SILVER | 16 - 35 | 5.0% | Réinvestissement Credit Wallet |
| GOLD | 36 - 55 | 4.5% | Réinvestissement Credit Wallet |
| PLATINUM | 56 - 84 | 3.5% | Réinvestissement Credit Wallet |
| DIAMOND | 85+ | 3.0% | Réinvestissement Credit Wallet |

**Avantages** :
- Simplicité et prévisibilité
- Pas de partage de gains
- Incitation à monter en tier (taux plus bas : 3% vs 5%)

**Utilisation** : Utilisateurs recherchant prévisibilité et coût connu à l'avance

### 3.2 Modèle B : Modèle Participatif (PGP)

**Exclusivement pour les hauts niveaux (PLATINUM & DIAMOND).**

Ce modèle ne repose pas sur un taux fixe, mais sur un **alignement de performance** entre FAN-Capital et l'investisseur.

| Caractéristique | Spécification |
|-----------------|---------------|
| **Hurdle Rate** | 2.5% (seuil de déclenchement du partage) |
| **Partage des Pertes** | En cas de baisse de la VNI, la plateforme absorbe une partie de la perte (selon le ratio du tier) |
| **Disponibilité** | PLATINUM et DIAMOND uniquement |

**Ratios de Partage (Gains)** :

| Niveau | Client | Plateforme |
|--------|--------|------------|
| PLATINUM | 80% | 20% |
| DIAMOND | 90% | 10% |

**Avantages** :
- Alignement d'intérêts
- Partage des risques
- Potentiel meilleur rendement pour le client

---

## 4. Loan-to-Value (LTV)

### 4.1 LTV par Type d'Actif (Mod AST 2.1)

| Type d'Actif | LTV Maximum |
|--------------|-------------|
| CPEF-EQUITY-HIGH | 70% |
| CPEF-EQUITY-MEDIUM | 70% |

### 4.2 Calcul LTV

**Formule** :
```
Valeur_Collatéral = Quantité_Tokens × VNI_actuelle
Montant_Avance_Max = Valeur_Collatéral × LTV (50%)
Montant_Crédité = Montant_Avance_Max × (1 - 5%)  // Retenue 5% avant crédit Credit Wallet
```

L'investisseur saisit un **nombre entier** de tokens. Le montant crédité au Credit Wallet est inférieur de 5% à l'avance max (LTV 50%).

**Exemple** :
- 100 CPEF-EQUITY
- VNI : 125.50 TND
- Valeur : 12,550 TND
- LTV : 50%
- **Avance max** : 6,275 TND

---

## 5. Mécanisme de Séquestre

### 5.1 Escrow Registry

**Processus** :
1. Demande avance utilisateur
2. Calcul LTV et montant
3. Blocage tokens dans EscrowRegistry
4. Émission crédit vers Credit Wallet

**Sécurité** :
- Tokens bloqués : `transfer` et `burn` désactivés
- Blocage jusqu'à échéance ou remboursement
- Protection contre double utilisation

### 5.2 Credit Wallet

**Circuit Fermé** : Fonds versés exclusivement sur le **Credit Wallet** interne.

| Caractéristique | Spécification |
|-----------------|---------------|
| **Destination** | Credit Wallet uniquement |
| **Réinvestissement** | Achats de nouveaux jetons CPEF uniquement |
| **Cash-Out bancaire** | ❌ **Inexistant** – La liquidité reste investie dans les actifs CPEF |

---

## 6. Modèle A : Taux Fixe Dégressif

### 6.1 Caractéristiques

Le taux annuel **décroît** avec le niveau (Score SCI). Voir tableau section 3.1.

**Calcul intérêts** :
```
Intérêts = Montant_Avance × Taux_Annuel × (Durée / 365)
```

**Exemple (Gold, 4.5%)** :
- Avance : 10,000 TND
- Durée : 6 mois (183 jours)
- Intérêts : 10,000 × 0.045 × (183/365) = **225.62 TND**

**Exemple (Diamond, 3%)** :
- Avance : 10,000 TND
- Durée : 6 mois (183 jours)
- Intérêts : 10,000 × 0.03 × (183/365) = **150.41 TND**

### 6.2 Remboursement

**Mode** : Cash-First (priorité numéraire)

**Processus** :
1. Remboursement cash à l'échéance
2. Déblocage intégral des tokens
3. Conservation du patrimoine

**Avantages** :
- Conservation des titres
- Bénéfice de l'appréciation
- Pas de liquidation

---

## 7. Modèle B : Partage Gains/Pertes (PGP)

**Disponibilité** : PLATINUM et DIAMOND uniquement.

### 7.1 Hurdle Rate

**Seuil** : 2.5% de performance VNI

**Logique** :
- Si performance < 2.5% : FAN-Capital ne prélève rien
- Client conserve 100% du gain
- Alignement d'intérêts

**Exemple** :
- Performance : 2.0%
- Hurdle : 2.5%
- Résultat : Client conserve 100% (pas de commission)

### 7.2 Ratios de Partage

**Au-delà du Hurdle Rate (2.5%)** :

| Niveau | Client | FAN-Capital | Hurdle Rate |
|--------|--------|-------------|-------------|
| PLATINUM | 80% | 20% | 2.5% |
| DIAMOND | 90% | 10% | 2.5% |

**Exemple (PLATINUM, performance 5%)** :
- Performance : 5%
- Au-delà du seuil : 5% - 2.5% = 2.5%
- Client : 2.5% × 80% = **2.0%**
- FAN-Capital : 2.5% × 20% = **0.5%**

### 7.3 Partage des Pertes

**Principe** : En cas de baisse de la VNI, la plateforme absorbe une partie de la perte selon le ratio du tier.

**Exemple (PLATINUM, perte -5%)** :
- Performance : -5%
- Client : -5% × 80% = **-4%**
- FAN-Capital : -5% × 20% = **-1%**

### 7.4 Durées et Délais

| Niveau | Durée Max | Délai Grâce |
|--------|-----------|-------------|
| PLATINUM | 6 mois | 7 jours |
| DIAMOND | 12 mois | 15 jours |

---

## 8. Mécanisme de Remboursement "Cash-First"

### 8.1 Principe

Pour **protéger le patrimoine** de l'utilisateur, le système privilégie le remboursement en numéraire :

| Élément | Description |
|---------|-------------|
| **Conservation des Titres** | L'investisseur garde la pleine propriété de ses jetons mis en séquestre (Escrow) |
| **Remboursement par Coupons** | Les dividendes ou intérêts versés par les actifs sous-jacents (Actions/Obligations du portefeuille) sont **automatiquement affectés** au remboursement de l'avance |
| **Libération de Collatéral** | Chaque remboursement (manuel ou via coupon) libère **instantanément** une quantité proportionnelle de jetons du compte de séquestre vers le portefeuille principal |

**Processus** :
1. À l'échéance : Versement cash pour solder l'avance
2. Une fois dette honorée : Déblocage intégral des jetons CPEF
3. Conservation : L'investisseur conserve tous ses titres
4. Appréciation : Bénéfice de la valorisation totale

**Avantages** :
- Conservation maximale du patrimoine
- Pas de liquidation forcée
- Flexibilité

### 8.2 Versements Mensuels et Déblocage Progressif

**Coupons Mensuels** :
- Revenus (dividendes, intérêts) versés chaque mois
- Sur le compte utilisateur

**Déblocage au Prorata** :
- Chaque coupon versé libère tokens équivalents
- Chaque remboursement partiel libère tokens équivalents
- Libération immédiate

**Exemple** :
- Avance : 10,000 TND
- Collatéral : 20,000 TND (100 CPEF)
- Coupon mensuel : 200 TND
- **Tokens libérés** : 1 CPEF (200/20000 × 100)

**Option de Partage (PGP)** :
- Partage de gains calculé uniquement sur portion débloquée
- Réalisation progressive des profits

---

## 9. Sécurité et Liquidation (LTV)

Le ratio **Loan-to-Value** est le garde-fou du système :

| Seuil | LTV | Action |
|-------|-----|--------|
| **Émission** | 50% max | Montant maximum d'avance à l'ouverture : 50% de la valeur des titres |
| **Appel de marge (Margin Call)** | 75% | Si la VNI baisse et que le LTV atteint 75%, notification utilisateur |
| **Liquidation forcée** | 85% | Le Smart Contract vend **automatiquement** la portion nécessaire de titres pour couvrir la dette, protégeant la solvabilité de la plateforme |

### 9.1 Protocoles de Liquidation

**Conditions d'Activation** (dernier recours uniquement) :
1. **Non-remboursement** : Aucun remboursement cash après expiration délai de grâce
2. **LTV critique** : Ratio LTV atteint 85%, déclenchement automatique

**Processus** :
1. Notification utilisateur (à 75% LTV)
2. Délai de grâce (selon niveau)
3. Si non-respect ou LTV ≥ 85% : Liquidation forcée automatique

### 9.2 Sécurité d'Exécution

**Lissage TWAP** :
- Prix de vente calculé sur moyenne temporelle
- Protection anti-flash crash
- Évite ventes à prix déprécié

**Spread Dynamique** :
- Frais indexés sur volatilité (VIX)
- Protection Réserve de Stabilisation
- Ajustement selon conditions marché

**Formule de Liquidation** :
```
Quantité_liquidée = (Dette_totale × (1 + Frais_pénalité)) 
                     / (VNI_actuelle × (1 - Spread_sortie))
```

---

## 10. Exemples de Simulation

### 10.1 Avance Taux Fixe (Modèle A)

**Données** :
- Collatéral : 100 CPEF-EQUITY-MEDIUM
- VNI : 125.50 TND
- Valeur : 12,550 TND
- LTV : 50%
- Avance : 6,275 TND
- Durée : 6 mois
- Niveau : Gold (taux 4.5%)

**Calcul** :
1. Intérêts : 6,275 × 0.045 × (183/365) = **141.57 TND**
2. **Total à rembourser** : 6,275 + 141.57 = **6,416.57 TND**

**Scénario Appréciation** :
- VNI finale : 135.00 TND
- Valeur finale : 13,500 TND
- Gain : 950 TND
- **Net après remboursement** : 13,500 - 6,416.57 = **7,083.43 TND**
- **Gain net** : 7,083.43 - 6,275 = **808.43 TND**

### 10.2 Avance PGP (Modèle B - PLATINUM)

**Données** :
- Collatéral : 100 CPEF-EQUITY-MEDIUM
- VNI initiale : 125.50 TND
- Avance : 6,275 TND (50% LTV)
- Durée : 6 mois
- Niveau : PLATINUM (80/20)
- Hurdle Rate : 2.5%

**Scénario 1 : Performance 5%** :
- VNI finale : 131.78 TND
- Performance : 5%
- Au-delà seuil : 5% - 2.5% = 2.5%
- Commission FAN : 2.5% × 20% = 0.5%
- Valeur finale : 13,178 TND
- Commission : 13,178 × 0.5% = 65.89 TND
- **Net utilisateur** : 13,178 - 6,275 - 65.89 = **6,837.11 TND**

**Scénario 2 : Performance -3%** :
- VNI finale : 121.74 TND
- Performance : -3%
- Partage perte : -3% × 20% = -0.6%
- Valeur finale : 12,174 TND
- Quote-part FAN : 12,174 × 0.6% = 73.04 TND
- **Net utilisateur** : 12,174 - 6,275 + 73.04 = **5,972.04 TND**

---

## 10. Transparence et Dashboard

### 10.1 Informations Disponibles

**Dashboard Utilisateur** :
- Montant avance actif
- Collatéral bloqué
- Intérêts/commissions dus
- Date échéance
- Déblocage progressif (si applicable)
- Performance VNI (PGP)

### 11.2 Allocation des Actifs

**Visibilité** :
- Allocation précise (Actions HIGH et MEDIUM)
- Taux de couverture Réserve Circulaire
- Suivi temps réel déblocage progressif

---

## 12. Conformité Fiscale B2B

### 12.1 Traitement Fiscal

**Entreprises** :
- Pertes PGP : Charges financières déductibles
- Relevés certifiés : Fournis annuellement
- Justification : Administration fiscale

**Particuliers** :
- Intérêts : Déductibles selon réglementation
- Documentation : Relevés fournis

---

## 13. Résumé de la Valeur Ajoutée pour FAN-Capital

| Pilier | Description |
|--------|-------------|
| **Fidélisation** | L'utilisateur est incité à rester Premium pour bénéficier des taux bas (3% Diamond vs 5% Silver) |
| **Croissance des AUM** | Puisque le retrait bancaire est impossible, **100% du crédit** est réinjecté dans l'achat de nouveaux jetons, augmentant les actifs sous gestion et les frais de transaction |
| **Sécurité** | Le modèle "Credit Wallet" élimine le risque de fuite de capitaux |
| **Conformité** | Simplifie la conformité avec le régulateur (CMF) |

---

## 14. Checklist Utilisateur

### Avant Demande

- [ ] Niveau Premium actif
- [ ] Collatéral suffisant
- [ ] Compréhension modèle choisi
- [ ] Capacité remboursement

### Pendant Avance

- [ ] Suivi performance (PGP)
- [ ] Remboursements partiels possibles
- [ ] Déblocage progressif (si applicable)

### À l'Échéance

- [ ] Remboursement cash
- [ ] Déblocage tokens
- [ ] Vérification calculs

---

*Document créé le 26 janvier 2026*
*Version 4.51 - Ingénierie Avance sur Titres*
