# Spécifications Financieres et Techniques

Modele Freemium & Avance sur Titres (AST)

Version 4.7- Révision : Durée Diamond

Direction Technique & Financiere

Février 2026

# Introduction

Ce document definit les parametres de segmentation des utilisateurs, les tarifs des abonnements Premium et les conditions d'octroi de I'Avance sur Titres (AST). La version 4.7 introduit une limitation de durée pour le palier Diamond à 12 mois.

# Structure Tarifaire et Accés Premium

L'acces aux services avancés (P2P, AST, Gestion de Patrimoine) est conditionné par un abonnement. Les tarifs sont structurés pour favoriser la rétention annuelle.

**Table 1 : Grille Tarifaire des Abonnements (TND)**

| Niveau | Trimestriel | Semestriel | Annuel |
|--------|-------------|------------|--------|
| BRONZE | — | — | — |
| SILVER | 45 | 80 | 150 |
| GOLD | 75 | 135 | 250 |
| PLATINUM | 150 | 270 | 500 |
| DIAMOND | 300 | 540 | 1000 |

# Paramètres de l'Avance sur Titres (AST)

L'AST permet aux investisseurs d'obtenir des liquidités en utilisant leur portefeuille comme collatéral.

# Conditions Générales

- **LTV (Loan-to-Value)** : 70% de la valeur des titres mis en gage.
- **Usage** : Fonds versés sur le Credit Wallet (Minting de CPEFTokens).

# Conditions par Palier

Les taux d'intérêt et les durées de remboursement varient selon le rang de l'investisseur.

**Table 2 : Taux et Durées de l'AST**

| Tier | Taux (Annuel) | Durée Maximale |
|------|---------------|----------------|
| SILVER | 5,0 % | 3 mois |
| GOLD | 4,5 % | 4 mois |
| PLATINUM | 3,5 % | 5 mois |
| DIAMOND | 3,0 % | 12 mois (1 an) |

# Moteur de Scoring et Conformité

Le Smart Contract d'Investissement (SCI v4.5) calcule dynamiquement le score de l'utilisateur.

# Formule du Score (SCI)

Le score holistique est calculé comme suit :

$$
S C I = (A U M _ {9 0 j} \times 0. 5) + (K Y C _ {d e p t h} \times 0. 2) + (B e h a v i o r \times 0. 1 5) + (R i s k \times 0. 1 5)
$$

# Verrous de Sécurité

- Inertie AUM : Les actifs sous gestion sont calculés sur une moyenne de 90 jours pour éviter les sauts de niveau opportunistes.   
- Blocage KYC : Le passage aux niveaux Gold et supérieurs nécessite impérativement la validation du KYC Niveau 2.

# Logique de l'Escrow (Smart Contract)

Le contrat EscrowRegistry gère le séquestre des jetons :

1. Blocage : Les jetons sont verrouillés lors de l'émission de l'avance.   
2. Libération au prorata : Pour chaque remboursement de mensualité, une fraction proportionnelle des jetons est libérée.
3. Calcul : Titres Libérés = Collatéral Total × (Remboursement / Dette Totale).
4. Libération complète : À l'échéance ou remboursement intégral, l'ensemble des jetons séquestrés est débloqué.

---

# Implémentation Technique (Référence)

| Élément | Fichier / Service |
|---------|-------------------|
| Formule SCI | SciScoreService |
| AUM 90j (Inertie) | AumSnapshotService, AumSnapshotJob (cron 1h) |
| Verrou KYC Gold+ | SciScoreService.kycTierCap / effectiveTier |
| Risk LTV AST | SciScoreService.computeRiskPoints |
| Paramètres AST (LTV 70%, durées par tier) | SpecFinancieresV47 |
| Grille tarifaire abonnements (Table 1) | SpecFinancieresV47.TIER_SUBSCRIPTION_TND |

---

# Architecture des Compartiments (La Matrice)

Le système repose sur une séparation stricte des fonds en quatre compartiments distincts pour garantir la solvabilité et la transparence.

| Compartiment | Nom | Fonction | Flux Entrants | Flux Sortants |
|--------------|-----|----------|---------------|---------------|
| **Piscine A** | Réserve de Liquidité | Contient le capital des investisseurs | Dépôts validés, remboursements capital AST | Retraits investisseurs, décaissements AST |
| **Piscine B** | Sas Partenaires | Zone de transit pour la réconciliation | D17, Flouci, Virements Bancaires | Transferts vers Piscine A (après validation) |
| **Piscine C** | Compte de Revenus | Isole la rentabilité de la plateforme | Intérêts AST, frais de gestion, spread | Dividendes, frais opérationnels |
| **Piscine D** | Fonds de Garantie | Sécurité contre les défauts (Bad Debt) | Prélèvement sur spread, fonds propres | Couverture des pertes AST |

**Implémentation (blockchain)** :
- `CompartmentWallet.sol` : contrats B, C, D (reçoivent TND, `transferTo` réservé gouvernance)
- `CompartmentsRegistry.sol` : registre des 4 adresses
- `LiquidityPool.sol` : frais → C (treasury), partie du spread → D (`guaranteeFundBps`, ex. 5 %)
- `deploy-factory-funds.ts` : déploiement des compartiments, Piscine C = treasury des pools
- API `GET /api/blockchain/compartments` : adresses des 4 compartiments

**Flux AST (remboursements)** :
- `AdvanceRepaymentService` : capital → mint vers pool (Piscine A), intérêts → mint vers Piscine C (Compte de Revenus)
- Taux par tier : SpecFinancieresV47.TIER_INTEREST_RATES (SILVER 5 %, GOLD 4,5 %, etc.)
