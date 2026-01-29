# Structure Tarifaire et Conditions Financières

Plateforme de Tokenisation CPEF - FAN-Capital

Document de Transparency Réglementaire

Département Stratégie & Conformité

Déprétre 2025

# 1 Introduction

Ce document définit l'intégralité des frais et commissions applicables aux opérations sur les certificats CPEF via la plateforme FAN-Capital. La structure repose sur un module *Freemium* hierarchisé, concu pour maximiser l'inclusion financière tout en assurant la pérennité de l'infrastructure blockchain.

# 2 Architecture des Portefeuilles

Pour garantir une gestion fluide de l'épargne, l'écosystème FAN-Capital désigne deux types d'avoirs pour chaque utiliser :

- Portefeuille de Tokens (CPEF): Contient les titres financiers tokenisés dont la valeur évouée selon la VNI.  
- Compte de Liquidité (Cash Wallet) : Portefeuille interne en TND servant de compte de passage pour le réinvestissement ou le retrait vers un compte bancaire/postal.

# 3 Frais d'Accès au Service

FAN-Capital favorise l'accessibilité avec une structure de frais fixes réduite au minimum :

- Ouverture de compte : 12 DT (frais uniques couvrant le KYC et l'initialisation du portefeuille).  
- Frais de garde : 0 DT (Gratuit).  
- Frais de transaction réseau (Gas) : 0 DT (Pris en charge par l'infrastructure).

# 4 Commissions de Transaction (Niveau de Service)

Les commissions sont dégressives selon le niveau de l'utilisateur. Elles sont prélevées lors du mouvement entre le capital monétaire et le portefeuille de tokens.

<table><tr><td>Niveau</td><td>P2P (Achat/Vente)</td><td>Piscine (Achat/Rachat)</td><td>Support IA</td></tr><tr><td>BRONZE ( Gratisuit)</td><td>0,80 %</td><td>1,00 %</td><td>Basique</td></tr><tr><td>SILVER</td><td>0,75 %</td><td>0,95 %</td><td>Avancé</td></tr><tr><td>GOLD</td><td>0,70 %</td><td>0,90 %</td><td>Expert</td></tr><tr><td>DIAMOND</td><td>0,60 %</td><td>0,85 %</td><td>Illimité</td></tr><tr><td>PLATINUM</td><td>0,50 %</td><td>0,80 %</td><td>Dédié</td></tr></table>

TABLE 1 - Structure des commissions par niveau d'utilisateur.

# 5.1 Absence de Pénalités de Sortie

Conformément à notre stratégie d'inclusion financière, aucune pénalité de durée n'est appliquée. L'utilisateur peut racheter ses titres à tout moment sans frais supplémentaires de détention.

# 5.2 Moment du Prélevement de la Fiscalité (RAS)

La Retenue à la Source (RAS) est géné de manière automatisée par le Smart Contract pour garantir la conformité avec le fisc tunisien :

- Événement déclencheur : La RAS est calculée et prélevée exclusivement lors du rachat des jetons (Burn) vers le Compte de Liquidité.  
- Calcul : La taxa s'applique uniquement sur la plus-value réalisée (Prix de sortie - PRM).  
- Disponibilité : Une fois les fonds sur le Compte de Liquidité, le montant affché est Net de fiscalité. Le transfert ultérieur vers un compte bancaire ou postal ne déclenché aucun prélevement supplémentaire.

# 6 Fiscalité (Taux applicables)

- Résident Tunisien : 10 % sur la plus-value réelle.  
- Non-Résident : 15 % sur la plus-value réelle (sauf convention bilatérale).

# 7 Exemple de Simulation de Rachat

Pour un rachat via la Piscine de 1 000 TND (Utilisateur Bronze Résident) :

- Valeur de sortie brute : 1 000,000 DT  
- Commission FAN-Capital (1%): - 10,000 DT  
- TVA sur commission (19%): - 1,900 DT  
- RAS (sur plus-value hypothétique de 100 DT) : - 10,000 DT  
- Montant créédité sur le Cash Wallet : 978,100 DT (Libre de tout retrait bancaire).
