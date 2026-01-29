**Modèle Économique & Ingénierie Financière**

Écosystème de Tokenisation CPEF (FAN-Capital)

Département Stratégie & R&D Blockchain

Décembre 2025

Objectif Stratégique![](f94996c6-a406-40db-b11e-aaf9ccc87dcf_00000.001.png)

Ce document définit le modèle économique de FAN-Capital, reposant sur une stra- tégie **Freemium 70/30**. L’offre gratuite assure la masse critique d’utilisateurs, tandis que l’offre Premium monétise les services avancés, le levier financier (Avance sur titres) et l’accès exclusif, tout en garantissant une étanchéité totale des risques via un séquestre automatisé.

1  **Architecture des Actifs et Jetons (CPEF)**

Le système gère trois classes d’actifs via des Smart Contracts ERC-1404 :

- **CPEF-EQUITY :** Panier d’actions. Spread dynamique selon la volatilité.
- **CPEF-BOND :** Titres de créances (*<* 1 an). Liquidité stable, spread minimal.
- **CPEF-SUKUK :** Financements participatifs éthiques à court terme.
2  **Structure de l’Offre et Niveaux de Service**



|**Service**|**Standard (Bronze) P**|**remium(Silver-Platinum)**|
| - | - | - |
|**Frais d’ouverture Spread Piscine Avance sur Titres IA Expert**|<p>12DT (KYC inclus) O *Sbase*</p><p>Nondisponible</p><p>Limité</p>|<p>fferts (Promotionnel)</p><p>Réduit de -20% à -50% **Disponible (Taux fixe 2%)** Illimité+ Alertes Alpha</p>|

Table 1 – Comparatif des niveaux de service

3  **Ingénierie du Spread et Fiscalité Indirecte**
1. **Commissions de Transaction et TVA**

En conformité avec la réglementation tunisienne, les commissions sont soumises à la TVA de 19%.

*Commr*é*elle* = *Commniveau* × (1 + 0*,*19) (1)

2. **Grille des Commissions (Taux de Base)**



|**Niveau**|**Commission Piscine C**|**ommissionP2P**|
| - | - | - |
|**BRONZE SILVER GOLD PLATINUM**|<p>1,00%</p><p>0,95% 0,90%</p><p>0,80%</p>|<p>0,80%</p><p>0,75% 0,70%</p><p>0,50%</p>|

4  **Avance sur Titres (Crédit Lombard)**

L’avance permet aux utilisateurs d’obtenir des liquidités immédiates sans liquider leur portefeuille, optimisant ainsi leur fiscalité et leur rendement.

- **Taux d’intérêt préférentiel : 2% fixe**. Ce taux compétitif est justifié par l’ab- sence de risque de défaut (collatéralisation totale).
- **LTV (Loan to Value) :** Jusqu’à 70% pour les Bonds et 50% pour les Equity.
- **Appel de Marge :** Déclenché si la valeur du collatéral chute sous un seuil critique.
5  **Cycle de Vie du Collatéral : Blocage et Indemni- sation**
1. **Mécanisme de Mise en Séquestre (Lock-up)**

Lorsqu’une avance est émise, les jetons CPEF sont transférés vers un **Escrow Regis-**

**try**.

- **Sécurité :** Les jetons sont bloqués jusqu’à l’échéance. Les fonctions *transfer* et *burn* sont désactivées pour le montant nanti.
- **Credit Wallet :** Les fonds sont versés sur un solde spécifique dédié au réinvestis- sement interne.
2. **Procédure d’Indemnisation Automatique**

En cas de non-remboursement ou d’absence d’indemnisation à l’échéance, le Smart Contract procède à la vente forcée des titres :

*Qt*é = *Dettetotale* × (1 + *Fraisp*é*nalit*é)

*liquid*é*e V NI* × (1 − *Spread* ) (2)

*actuelle sortie*

La vente directe via la Piscine assure le remboursement intégral de FAN-Capital.

6  **Logique d’Exécution et Fiscalité (RAS)**
1. **Calcul de la Quantité Nette et PRM**

L’achat intègre le spread et la commission réelle. Le Smart Contract enregistre le **PRM** (Prix de Revient Moyen) pour optimiser l’assiette fiscale lors de la sortie.

2. **Circuit Breaker et Gouvernance**

Si *R <* 20%, le rachat via piscine est suspendu. Toute modification des paramètres critiques est soumise à un vote **Multi-Signature** des administrateurs autorisés.
3
