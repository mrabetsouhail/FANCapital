# Spécifications Techniques Détailleées

# Infrastructure Blockchain & Smart Contracts CPEF

Département R&D Blockchain - FAN-Capital

22 décembre 2025

# Résumé du Document

Ce document définit l'implémentation technique des certificates CPEF sur une DLT permissionnée. Il détaille la logique du standard ERC-1404, l'algorithm de pricing dynamique et l'automatisation de la fiscalité tunisienne.

# 1 Architecture de l'Infrastructure Résseau

# 1.1 Sélection du Protocole DLT

Le choix s'est porté sur une blockchain de type Permissionnée (Hyperledger Besu / Quorum) pour répondre aux exigences suivantes :

— Finalité Immediate : Utilisation du consensus IBFT 2.0 (temps de bloc < 2s).  
— Gestion du Gaz : Suppression des frais de transaction pour l'utilisateur final (modèle Gas-Free).  
Gouvernance: Contrôle total sur les validateurs (nœuds opérés par FAN-Capital et l'Intermédiaire en Bourse).

# 2 Standard de Jeton : ERC-1404 (Security Token)

Le certificat CPEF est implémenté via le standard ERC-1404, permettant d'intégrer la conformité directement dans le registre.

# 2.1 Gestion du KYC Progressif (Listes Blanches)

Le contrat intéège une logique de restriction via la fonction detectTransferRestriction :

— Niveau 1 (Green List): Uniquement Mint et Burn autorisés. Les transferts P2P sont bloqués par le contrat. Plafond de solde: 5000 TND.  
— Niveau 2 (White List): Activation des fonctions de transfert (transfer, transferFrom) pour le marché secondaire. Levée des plafonds.

# 3 Ingénierie Financière et Pricing Dynamique

Le Smart Contract de la Piscine de Liquidité calcule le prix d'exécution en temps réel.

# 3.1 L'Equation du Spread Dynamique

Le prix client  $(P_{\text{client}})$  est dérivé de la VNI de référence  $(P_{\text{ref}})$  via l'algorithmme suivant :

$$
P _ {\text {c l i e n t}} = P _ {\text {r e f}} \times (1 \pm \text {S p r e a d} _ {\text {d y n}}) \tag {1}
$$

Le  $\text{Spread}_{\text{dyn}}$  est une variable calculée par le contrat :

$$
\operatorname {S p r e a d} _ {\text {d y n}} = S _ {\text {b a s e}} + \alpha (\sigma) + \beta \left(\frac {1}{R}\right) \tag {2}
$$

Où  $S_{base}$  est la marge fixe (0.2%),  $\alpha(\sigma)$  la volatilité et  $\beta(1/R)$  la prime liée au ratio de réserve de la piscine.

# 4 Automatisation Fiscale (Conformité Tunisienne)

Le Smart Contract agit comme un agent fiduciaire automatisé pour le fisc.

# 4.1 Calcul du PRM et de la Retenue à la Source

Stockage du PRM : Chaque adresse est associée à son Prix de Revient Moyen, mis à jour lors de chaque achat (Mint).  
— Prélevement Automatique : Lors du rachat (Burn), le contrat calcule la plus-value réelle et déduit automatiquement la retenue à la source selon le statut de l'investisseur (Résident ou Non-Résident).  
— Frais de Sortie : Une pénalité de  $2\%$  est appliquée si le temps écoué depuis l'acquisition est inférieur à 180 jours.

# 5 Sécurité et Gouvernance Technique

— Multi-Signature (3/5): Nécessaire pour toute modification des adresses d'Oracle ou des paramètres financiers (taux de spread).  
— Circuit Breaker : Suspension automatique des rachats si la réserve de la piscine tombe sous le seuil critique de  $20\%$ .  
— Oracle Guard : Le contrat rejette les mises à jour de prix si l'écart avec la valeur précédente est supérieur à  $10\%$ .

# 6 Nœud d'Audit Réglementaire

Le contrat expose une interface spécifique pour le CMF permettant :

— La vérification en temps réel du TotalSupply (encours global).  
— Le rapprochement entre les actifs réels chez l'IB et les jetsons émis sur la DLT.
