# Avance sur Titres (Crédit Lombard) - FAN-Capital

## Vue d'ensemble

L'avance sur titres permet aux utilisateurs d'obtenir des liquidités immédiates sans liquider leur portefeuille, optimisant ainsi leur fiscalité et leur rendement.

---

## 1. Caractéristiques Générales

### 1.1 Objectif

**Liquidité immédiate** sans liquidation du portefeuille :
- Conservation des titres
- Bénéfice de l'appréciation
- Optimisation fiscale
- Flexibilité financière

### 1.2 Disponibilité

**Niveaux** : Premium uniquement (Silver à Platinum)
- BRONZE : ❌ Non disponible
- SILVER : ✅ Disponible
- GOLD : ✅ Disponible
- DIAMOND : ✅ Disponible
- PLATINUM : ✅ Disponible

---

## 2. Types d'Avance

### 2.1 Modèle A : Avance à Taux Fixe (2%)

**Caractéristiques** :
- **Taux** : 2% annuel fixe
- **Prévisibilité** : Coûts totaux connus à l'avance
- **Partage** : Aucun partage de gains
- **Disponibilité** : Tous niveaux Premium

**Avantages** :
- Simplicité
- Prévisibilité
- Pas de partage de gains

**Utilisation** : Utilisateurs recherchant prévisibilité

### 2.2 Modèle B : Modèle Participatif (PGP)

**Caractéristiques** :
- **Hurdle Rate** : 2.5% (seuil de performance)
- **Partage gains** : Selon niveau (10-30% FAN-Capital)
- **Partage pertes** : Quote-part plateforme
- **Disponibilité** : Silver, Gold, Diamond

**Avantages** :
- Alignement d'intérêts
- Partage des risques
- Potentiel meilleur rendement

**Utilisation** : Utilisateurs confiants en performance

---

## 3. Loan-to-Value (LTV)

### 3.1 LTV par Type d'Actif

| Type d'Actif | LTV Maximum |
|--------------|-------------|
| CPEF-EQUITY-HIGH | 50% |
| CPEF-EQUITY-MEDIUM | 50% |

### 3.2 Calcul LTV

**Formule** :
```
Valeur_Collatéral = Quantité_Tokens × VNI_actuelle
Montant_Avance_Max = Valeur_Collatéral × LTV
```

**Exemple** :
- 100 CPEF-EQUITY
- VNI : 125.50 TND
- Valeur : 12,550 TND
- LTV : 50%
- **Avance max** : 6,275 TND

---

## 4. Mécanisme de Séquestre

### 4.1 Escrow Registry

**Processus** :
1. Demande avance utilisateur
2. Calcul LTV et montant
3. Blocage tokens dans EscrowRegistry
4. Émission crédit vers Credit Wallet

**Sécurité** :
- Tokens bloqués : `transfer` et `burn` désactivés
- Blocage jusqu'à échéance ou remboursement
- Protection contre double utilisation

### 4.2 Credit Wallet

**Destination** : Fonds versés sur solde spécifique
- Réinvestissement interne possible
- Retrait bancaire possible
- Utilisation flexible

---

## 5. Modèle A : Taux Fixe (2%)

### 5.1 Caractéristiques

**Taux** : 2% annuel fixe

**Calcul intérêts** :
```
Intérêts = Montant_Avance × Taux × (Durée / 365)
```

**Exemple** :
- Avance : 10,000 TND
- Durée : 6 mois (183 jours)
- Intérêts : 10,000 × 0.02 × (183/365) = **100.27 TND**

### 5.2 Remboursement

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

## 6. Modèle B : Partage Gains/Pertes (PGP)

### 6.1 Hurdle Rate

**Seuil** : 2.5% de performance VNI

**Logique** :
- Si performance < 2.5% : FAN-Capital ne prélève rien
- Client conserve 100% du gain
- Alignement d'intérêts

**Exemple** :
- Performance : 2.0%
- Hurdle : 2.5%
- Résultat : Client conserve 100% (pas de commission)

### 6.2 Ratios de Partage

**Au-delà du Hurdle Rate** :

| Niveau | Client | FAN-Capital | Hurdle Rate |
|-------|--------|-------------|-------------|
| SILVER | 70% | 30% | 2.5% |
| GOLD | 80% | 20% | 2.5% |
| DIAMOND | 90% | 10% | 2.5% |

**Exemple (Gold, performance 5%)** :
- Performance : 5%
- Au-delà du seuil : 5% - 2.5% = 2.5%
- Client : 2.5% × 80% = **2.0%**
- FAN-Capital : 2.5% × 20% = **0.5%**

### 6.3 Partage des Pertes

**Principe** : La plateforme assume sa quote-part

**Exemple (Gold, perte -5%)** :
- Performance : -5%
- Client : -5% × 80% = **-4%**
- FAN-Capital : -5% × 20% = **-1%**

### 6.4 Durées et Délais

| Niveau | Durée Max | Délai Grâce |
|--------|-----------|-------------|
| SILVER | 3 mois | 3 jours |
| GOLD | 6 mois | 7 jours |
| DIAMOND | 12 mois | 15 jours |

---

## 7. Mécanique de Remboursement

### 7.1 Cash-First (Priorité Numéraire)

**Principe** : Priorité au remboursement en numéraire

**Processus** :
1. À l'échéance : Versement cash pour solder l'avance
2. Une fois dette honorée : Déblocage intégral des jetons CPEF
3. Conservation : L'investisseur conserve tous ses titres
4. Appréciation : Bénéfice de la valorisation totale

**Avantages** :
- Conservation maximale du patrimoine
- Pas de liquidation forcée
- Flexibilité

### 7.2 Versements Mensuels et Déblocage Progressif

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

## 8. Gestion de Crise et Liquidation

### 8.1 Protocoles de Liquidation

**Conditions d'Activation** (dernier recours uniquement) :
1. **Non-remboursement** : Aucun remboursement cash après expiration délai de grâce
2. **LTV critique** : Ratio LTV dépasse 85%, mettant en péril la solvabilité

**Processus** :
1. Notification utilisateur
2. Délai de grâce (selon niveau)
3. Si non-respect : Liquidation forcée

### 8.2 Sécurité d'Exécution

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

## 9. Exemples de Simulation

### 9.1 Avance Taux Fixe (Modèle A)

**Données** :
- Collatéral : 100 CPEF-EQUITY-MEDIUM
- VNI : 125.50 TND
- Valeur : 12,550 TND
- LTV : 50%
- Avance : 6,275 TND
- Durée : 6 mois
- Taux : 2%

**Calcul** :
1. Intérêts : 6,275 × 0.02 × (183/365) = **62.92 TND**
2. **Total à rembourser** : 6,275 + 62.92 = **6,337.92 TND**

**Scénario Appréciation** :
- VNI finale : 135.00 TND
- Valeur finale : 13,500 TND
- Gain : 950 TND
- **Net après remboursement** : 13,500 - 6,337.92 = **7,162.08 TND**
- **Gain net** : 7,162.08 - 6,275 = **887.08 TND**

### 9.2 Avance PGP (Modèle B - Gold)

**Données** :
- Collatéral : 100 CPEF-EQUITY-MEDIUM
- VNI initiale : 125.50 TND
- Avance : 6,275 TND (50% LTV)
- Durée : 6 mois
- Niveau : Gold (80/20)
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

### 10.2 Allocation des Actifs

**Visibilité** :
- Allocation précise (Actions HIGH et MEDIUM)
- Taux de couverture Réserve Circulaire
- Suivi temps réel déblocage progressif

---

## 11. Conformité Fiscale B2B

### 11.1 Traitement Fiscal

**Entreprises** :
- Pertes PGP : Charges financières déductibles
- Relevés certifiés : Fournis annuellement
- Justification : Administration fiscale

**Particuliers** :
- Intérêts : Déductibles selon réglementation
- Documentation : Relevés fournis

---

## 12. Checklist Utilisateur

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
*Version 1.0 - Ingénierie Avance sur Titres*
