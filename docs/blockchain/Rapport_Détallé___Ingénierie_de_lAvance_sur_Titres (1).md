# Rapport de Conception Finale : Ingénierie Financière

Écosystème de Tokenisation CPEF (FAN-Capital)

Département Ingénierie Financière & Blockchain

Version 3.5 — Janvier 2026

# Synthese Stratégique

Ce document présente l'architecture finale et détaillée de l'avance sur titres de FAN-Capital. L'objectif principal est d'affrir une liquidité immédiate tout en garantissant la conservation intégrale des actifs de l'investisseur. Le modele repose sur la priorité du remboursement en numérique, le déblocage progressif des titres et une gouvernance institutionnelle.

# 1 Architecture de Gestion et Governance

# 1.1 Gestion Centralisée des Actifs (Asset Management)

FAN-Capital opère comme un Portfolio Manager professionnel. La valeur du jeton CPEF n'est pas spécifique mais repose sur une gestion active :

- Allocation Prudente: Le panier sous-jacent est composé d'actions cotées, d'obligations et de Sukuks. Afin de minimiser le risque systémique, une règle de diversification stricte limite toute position individuelle à un maximum de  $10\%$  du portefeuille total.  
— Audit de Solvabilité : Un cabinet d'audit externe certifie trimestriellement que la réserve de cash en TND chez les banques dépositaires correspond exactement à  $100\%$  de la capitalisation des jetons émis.

# 1.2 Gouvernance et Sécurité Algorithmique (Multi-Sig)

Pour prévenir toute erreur humaine ou décision unilatérale, les paramètres critiques (taux d'intérêt, Hurdle Rate, ratios de partage) sont protégés par un protocole Multi-Signature. Une modification nécessite l'approbation de 3 signataires sur 5, garantissant la stabilité des règles pour l'investisseur.

# 2 Mécanique de Remboursement et Libération des Titres

# 2.1 Priorité au Remboursement en Numétaire (« Cash-First »)

Contrairement aux modèles classiques de liquidation automatique, FAN-Capital priorise la conservation du patrimoine :

— Mode par Défaut : À l'échéance, l'utilisateur effectue un versement en cash pour solder son avance.  
— Libération des Fonds : Une fois la dette honorée en numérique, le Smart Contract débloque l'intégrality des jetsons CPEF du Wallet Principal. L'investisseur conserve ainsi tous ses titres et bénéficiaie de leur appréciation totale.  
- Modèle PGP sans Vente : Dans le modele participatif, si une commission est due à la plateforme, l'utilisateur peut la régler en cash, évitant ainsi de réduire son nombre de jetons.

# 2.2 Versements Mensuels et Deblocage au Prorata

Pour soutenir la trésorerie du client, FAN-Capital introduit une libération fluide du collatéral :

— Coupons Mensuels : Les revenus (dividendes, intérêts) sont versés chaque mois sur le compte du client.  
— Déblocage au Prorata : Chaque coupon versé ou remboursement partiel entraine la libération immédiate d'une quantité équivalente de CPEF bloqués.  
- Activation de l'Option de Partage : Pour le Modèle B (PGP), le partage de gains est calculé et prélevé uniquement sur la portion de titres débloqués, permettant une réalisation progressive des profits pour les deux parties.

# 3 Analyse des Modèles d'Avance

# 3.1 Modèle A : Avance à Taux Fixe (2%)

Modèle de crédit Lombard traditionnel. Le client paie un intérêt fixe annuel de  $2\%$ . Ce modèle ne nécessiteaucun partage de gains;il estprivilégépar les investisseurs recherchant une prévisibilité totale des coûts.

# 3.2 Modèle B : Modèle Participatif (Partage Gains/Pertes - PGP)

Ce modèle aligne les intérêts de la plateforme sur ceux du client (Finance participative):

Hurdle Rate (Seuil de Performance): Fixé à 2,5%. Si la performance de la VNI est inférieure à ce seuil, FAN-Capital ne peutait aucune commission. Le client conserve 100% du gain.  
— Ratios de Partage : Au-delà du seuil, les gains sont partagés selon le niveau (ex : 80% Client / 20% FAN pour le niveau Gold).  
— Partage des Pertes : En cas de baisse de la VNI, la plateforme assume sa quote-part de la perte de valeur dans le cadre du contrat de partenariat.

# 4 Gestion de Crise et Liquidation de Dernier Recours

# 4.1 Protocoles de Liquidation

La liquidation n'intervient jamais par défaut. Elle est un filet de sécurité activé uniquement si :

— L'utilisateur ne procède àaucun remboursement cash après expiration du délaï de grâce (3 à 15 jours).  
— Le ratio LTV dépasse  $85\%$ , mettant en péril la solvabilité du système.

# 4.2 Sécurité de l'Execution (Anti-Flash Crash)

Pour protéger la valeur de sortie des titres, FAN-Capital utilise :

— Lissage TWAP : Prix de vente calculé sur une moyen temporelle pour éviter les chutes brusques de prix spot.  
— Spread Dynamique : Frais de sortie indexés sur la volatilité du marché (VIX) pour protégger la Réserve de Stabilisation.

# 5 Transparency et Fiscalité B2B

# 5.1 Dashboard de Performance et Allocation

Le client dispose d'une visibilité totale sur :

— L'allocation précise des actifs (Actions, Obligations, Sukuks).  
Le taux de couverture de la Réserve Circulaire (alimentée par  $100\%$  des penalités).  
— Le suivi en temps réel du déblocage progressif des titres.

# 5.2 Conformité Fiscale B2B (Tunisie)

Pour les entreprises, les pertes éventuelles subies dans le cadre PGP sont traitées comme des charges financières déductibles. FAN-Capital fournit un relevé certifié annuel permettant de justifier ces opérations auprès de l'administration fiscale.

# 6 Matrice de Synthese Opérationnelle

<table><tr><td>Niveau</td><td>Durée Max</td><td>Délai Grêce</td><td>Ratio PGP</td><td>Hurdle Rate</td></tr><tr><td>SILVER</td><td>3 mois</td><td>3 jours</td><td>70 / 30</td><td>2,5%</td></tr><tr><td>GOLD</td><td>6 mois</td><td>7 jours</td><td>80 / 20</td><td>2,5%</td></tr><tr><td>DIAMOND</td><td>12 mois</td><td>15 jours</td><td>90 / 10</td><td>2,5%</td></tr></table>
