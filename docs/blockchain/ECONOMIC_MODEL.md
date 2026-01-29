# Modèle Économique - FAN-Capital

## Vue d'ensemble

Le modèle économique de FAN-Capital repose sur une stratégie **Freemium 70/30**, combinant inclusion financière et monétisation via services premium.

---

## 1. Stratégie Freemium 70/30

### Concept

- **70%** : Offre gratuite (Bronze) pour atteindre la masse critique d'utilisateurs
- **30%** : Offre Premium (Silver à Platinum) pour générer des revenus

### Objectifs

- **Inclusion financière** : Accès démocratisé à l'investissement
- **Pérennité** : Modèle économique viable
- **Croissance** : Acquisition utilisateurs via gratuité
- **Monétisation** : Conversion vers Premium

---

## 2. Architecture des Actifs CPEF

Le système gère **deux types de CPEF** via des Smart Contracts ERC-1404 distincts :

### 2.1 CPEF-EQUITY-HIGH (Panier d'Actions - Rendement Élevé)

**Caractéristiques** :
- **Actifs** : Panier d'actions cotées tunisiennes à fort potentiel
- **Risque** : Très élevé
- **Rendement** : Élevé, variable
- **Spread** : Dynamique selon volatilité (plus élevé)
- **LTV** : 50% (pour avance)

**Composition typique** :
- Actions de croissance : 40%
- Actions bancaires : 30%
- Actions industrielles : 20%
- Autres : 10%

**Profil** : Pour investisseurs recherchant un rendement élevé avec acceptation du risque

### 2.2 CPEF-EQUITY-MEDIUM (Panier d'Actions - Rendement Moyen)

**Caractéristiques** :
- **Actifs** : Panier d'actions cotées tunisiennes équilibré
- **Risque** : Élevé (mais inférieur à HIGH)
- **Rendement** : Moyen, plus stable
- **Spread** : Dynamique selon volatilité (modéré)
- **LTV** : 50% (pour avance)

**Composition typique** :
- Actions bancaires : 35%
- Actions industrielles : 30%
- Actions services : 25%
- Autres : 10%

**Profil** : Pour investisseurs recherchant un équilibre rendement/risque

---

## 3. Structure des Niveaux de Service

### Comparatif Standard vs Premium

| Service | Standard (Bronze) | Premium (Silver-Platinum) |
|---------|-------------------|---------------------------|
| **Frais d'ouverture** | 12 DT (KYC inclus) | Offerts (Promotionnel) |
| **Spread Piscine** | S_base (standard) | Réduit de -20% à -50% |
| **Avance sur Titres** | ❌ Non disponible | ✅ Disponible (Taux fixe 2%) |
| **IA Expert** | Limitée | Illimitée + Alertes Alpha |
| **Support** | Standard | Prioritaire |

### Niveaux Premium

**SILVER** :
- Réduction spread : -5%
- Commission : 0.95% (piscine), 0.75% (P2P)
- Avance : Disponible
- Durée max avance : 3 mois

**GOLD** :
- Réduction spread : -10%
- Commission : 0.90% (piscine), 0.70% (P2P)
- Avance : Disponible
- Durée max avance : 6 mois
- Ratio PGP : 80/20

**DIAMOND** :
- Réduction spread : -15%
- Commission : 0.85% (piscine), 0.60% (P2P)
- Avance : Disponible
- Durée max avance : 12 mois
- Ratio PGP : 90/10

**PLATINUM** :
- Réduction spread : -20%
- Commission : 0.80% (piscine), 0.50% (P2P)
- Avance : Disponible
- Durée max avance : 12 mois+
- Ratio PGP : 90/10
- Support dédié

---

## 4. Sources de Revenus

### 4.1 Commissions de Transaction

**Piscine de Liquidité** :
- BRONZE : 1.00%
- SILVER : 0.95%
- GOLD : 0.90%
- DIAMOND : 0.85%
- PLATINUM : 0.80%

**Marché P2P** :
- BRONZE : 0.80%
- SILVER : 0.75%
- GOLD : 0.70%
- DIAMOND : 0.60%
- PLATINUM : 0.50%

**TVA** : 19% sur toutes les commissions

### 4.2 Avance sur Titres

**Modèle A (Taux Fixe)** :
- Taux : 2% annuel
- Revenus : Intérêts prélevés

**Modèle B (PGP)** :
- Hurdle Rate : 2.5%
- Partage gains : 10-30% selon niveau
- Partage pertes : Quote-part plateforme

### 4.3 Frais d'Ouverture

**Standard** : 12 DT (Bronze)
- Couvre KYC et initialisation

**Premium** : Gratuit (promotionnel)

### 4.4 Services Premium

**IA Expert** :
- Analyse avancée
- Alertes personnalisées
- Recommandations

**Support Prioritaire** :
- Réponse rapide
- Support dédié (Platinum)

---

## 5. Ingénierie du Spread

### 5.1 Formule du Spread Dynamique

```
P_client = P_ref × (1 ± Spread_dyn)

Spread_dyn = S_base + α(σ) + β(1/R)

Où:
- S_base = 0.2% (marge fixe)
- α(σ) = prime de volatilité
- β(1/R) = prime selon ratio de réserve
```

### 5.2 Réduction selon Niveau

**Premium** : Réduction de 20% à 50% du spread

**Exemple** :
- Spread base : 0.2%
- Réduction Silver (-5%) : 0.19%
- Réduction Gold (-10%) : 0.18%
- Réduction Diamond (-15%) : 0.17%
- Réduction Platinum (-20%) : 0.16%

---

## 6. Cycle de Vie du Collatéral (Avances)

### 6.1 Mise en Séquestre

**Processus** :
1. Demande avance utilisateur
2. Calcul LTV selon type d'actif
3. Blocage tokens dans EscrowRegistry
4. Émission crédit vers Credit Wallet

**Sécurité** :
- Tokens bloqués : `transfer` et `burn` désactivés
- Séquestre jusqu'à échéance ou remboursement

### 6.2 Remboursement

**Cash-First** :
- Priorité au remboursement en numéraire
- Conservation du patrimoine utilisateur
- Déblocage intégral après remboursement

**Déblocage Progressif** :
- Coupons mensuels libèrent tokens au prorata
- Remboursements partiels libèrent tokens équivalents

### 6.3 Liquidation (Dernier Recours)

**Conditions** :
- Non-remboursement après délai de grâce
- LTV dépasse 85%

**Processus** :
- Vente forcée via piscine
- Calcul TWAP (anti-flash crash)
- Remboursement intégral FAN-Capital

---

## 7. Modèle de Partage PGP

### 7.1 Hurdle Rate

**Seuil** : 2.5% de performance VNI

**Logique** :
- Si performance < 2.5% : FAN-Capital ne prélève rien
- Client conserve 100% du gain
- Alignement d'intérêts

### 7.2 Ratios de Partage

**Au-delà du Hurdle Rate** :

| Niveau | Client | FAN-Capital |
|--------|--------|-------------|
| SILVER | 70% | 30% |
| GOLD | 80% | 20% |
| DIAMOND | 90% | 10% |

### 7.3 Partage des Pertes

**Principe** : La plateforme assume sa quote-part

**Exemple** :
- Perte de 5% sur VNI
- Niveau Gold (80/20)
- Client : -4% (80% de -5%)
- FAN-Capital : -1% (20% de -5%)

---

## 8. Projections Économiques

### 8.1 Hypothèses

**Utilisateurs** :
- Année 1 : 10,000 utilisateurs
- Conversion Premium : 20%
- Croissance : 50% annuelle

**Volume** :
- Volume moyen par utilisateur : 5,000 TND/an
- Taux de rotation : 2x/an

### 8.2 Revenus Estimés

**Commissions** :
- Volume total : 50M TND
- Commission moyenne : 0.90%
- Revenus : 450,000 TND/an

**Avances** :
- 20% utilisateurs Premium
- Taux utilisation : 30%
- Revenus intérêts : 60,000 TND/an

**Total estimé** : 510,000 TND/an (année 1)

---

## 9. Optimisation Fiscale

### 9.1 Pour l'Utilisateur

**PRM** :
- Calcul automatique
- Optimisation plus-value
- Réduction base imposable

**Avance sur Titres** :
- Liquidité sans liquidation
- Conservation patrimoine
- Optimisation fiscale

### 9.2 Pour FAN-Capital

**TVA** :
- Collectée sur commissions
- Reversée au fisc
- Conformité assurée

**Fiscalité B2B** :
- Pertes PGP déductibles
- Relevés certifiés fournis

---

## 10. Stratégie de Croissance

### 10.1 Acquisition

**Gratuité Bronze** :
- Barrière d'entrée faible
- Acquisition massive
- Viralité

**Promotions** :
- Frais d'ouverture offerts (Premium)
- Réductions temporaires

### 10.2 Conversion

**Incitations** :
- Réduction commissions
- Accès avance sur titres
- Services premium

**Seuil de conversion** :
- Volume > 10,000 TND
- Fréquence élevée
- Besoin de liquidité

### 10.3 Rétention

**Services Premium** :
- IA Expert
- Support prioritaire
- Conditions préférentielles

**Fidélisation** :
- Programmes de récompense
- Niveaux progressifs

---

*Document créé le 26 janvier 2026*
*Version 1.0*
