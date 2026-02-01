# Rapport Technique : Architecture de Gestion Intégrée en Circuit Fermé

FAN-Capital - Direction Technique

Février 2026

# INTRODUCTION

Leprésent document expose l'architecture logicielle de la plateforme FAN-Capital. Pour garantir une conformité stricte avec la réglementation financière tunisienne et les exigences du Conseil du Marché Financier (CMF), nous avons opté pour une approche en "Circuit Fermé". Cette architecture élimine la dépendance aux portefeuilles tiers (type MetaMask) au profit d'une gestion interne souveraine.

# GESTION DES PORTEFEUILLES (WAAS)

La plateforme intègre un service de Wallet-as-a-Service (WaaS) qui masque la complexité de la blockchain pour l'investisseur final.

— Création Native: Une adresse blockchain est généraee automatiquement par le Backend Spring Boot lors de l'inscription.   
— Sécurité des Clés : Les clés privées sont chiffrées via l'algorithmme AES-GCM et stockées dans une zone isolée.   
— Abstraction UX : L'utilisateur n'a aucune "Seed Phrase" à gérer ; son accès est protégé par son identité vérifiée (KYC).

# MÉCANISME DE CRÉATION DES JETONS (MINTING)

La création des jetsons CPEF (Atlas et Didon) est une opération strictement adossée à la réalité des actifs sous-jacents détenus par la plateforme.

# Équilibre Actif/Passif

Le nombre de jetons à émettre (Mint) est déterminé par la valeur nette du portefeuille de sous-jacents acquis en bourse. La formule de calcul appliquée par le moteur de la plateforme est la suivante :

$$
N _ {\text {t o k e n s}} = \frac {V _ {\text {p o r t e f e u i l l e}}}{P _ {\text {t o k e n}}} \tag {1}
$$

Où :

— $N_{\text{token}}$ : Nombre de nouveaux jetons à créé.   
— $V_{\text{portefeuille}}$ : Valeur nette totale des actifs réels acquis via l'intermédiaire en bourse.   
- $P_{token}$ : Valeur Liquidative (VNI) actuelle du jeton.

# Flux de Distribution

Les jetons créés sont initialement stockés dans le Stock Wallet de FAN-Capital. Ils sont ensuite distribués aux utilisateurs selon le processus suivant :

1. Réception des fonds (TND) sur le compte de la plateforme.   
2. Émission de CashTokenTND sur le compte interne de l'utilisateur.   
3. Échange atomique entre le Stock Wallet et l'utilisateur via la Liquidity Pool.

# Gouvernance et Sécurité

L'absence de portefeuilles externes est compensée par un système de Clés de Service (Master Keys) gériées programmatiquement par le backend :

— Rôle MINTER : Autorise l'émission de nouveaux titres.   
— Rôle ORACLE : Met à jour les prix en provenance de la BVMT.   
— Rôle GOVERNANCE: Permet au conseil (Multi-sig 3/5) de modifier les paramètres du système.

# CONCLUSION

Cette architecture garantit que FAN-Capital demeure le seul tiers de confiance responsable devant le régulateur. Elle offre une sécurité maximale en empêchant toute fuite de capitaux hors du système KYC tout en assurant une transparence totale via le registre partagé.
