# RÉFÉRENTIEL TECHNIQUE: REGISTRE D'AUDIT ET CONFORMITE DLT

Système Intégré de Gestion des CPEF (Atlas & Didon)

Département Conformité & Technologie — FAN-Capital

Février 2026

# INTRODUCTION ET OBJECT

Ce document définit l'architecture du Registre de Propriété Immuable de FAN-Capital. L'objet est de garantir au régulateur (CMF) et aux auditeurs internes une transparence totale et une intégrité absolue des données relatives aux détenteurs de parts de fonds Atlas et Didon.

# ARCHITECTURE DU REGISTRE "DOUBLE-COUCHE"

La plateforme ne repose pas sur une base de données isolée, mais sur une synchronisation force entre deux environnementes :

# Couche Identitaire (Off-Chain)

Stockée dans un environnement sécurisé et chiffré, cette couche contient les données personnelles (KYC) nécessaires à l'identification légale :

— Nom, Prénom et Coordonnées.   
— Numéro de Carte d'Identité Nationale (CIN).   
- Adresse de messagerie électronique certifiée.

# Couche Transactionnelle (On-Chain / DLT)

Le Distributed Ledger (DLT) sert de Grand Livre comptable. Il contient l'histoire non modifiable des avons :

- Immuabité : Aucun solde ne peut être modifié sans une transaction signée sur la blockchain.   
— Horodatage : Chaque mouvement est lié à un bloc spécifique avec une preuve temporelle.   
— Solde Réel : Le nombre de jetsons Atlas et Didon est extrait directement du contrat intelligent (Smart Contract).

# LE REGISTRE D'AUDIT BACK-OFFICE

Le Back-office de FAN-Capital présente une vue unifiée agissant comme la Source de Vérité pour l'audit.

# Structure de l'Interface d'Inspection

L'interface de consultation pour les auditeurs et régulateurs présente la réconciliation suivante :

<table><tr><td>Identité (KYC)</td><td>ID Fiscal</td><td>Wallet Address</td><td>Solde Atlas</td><td>Solde Didon</td></tr><tr><td>M. Ben Salem</td><td>08XXXXXXXX7</td><td>0x71C...a4f</td><td>150.00</td><td>45.00</td></tr><tr><td>Mme S. Mansour</td><td>09XXXXXXXX2</td><td>0x42B...e9d</td><td>0.00</td><td>120.50</td></tr></table>

# Garanties d'Intégrité

1. Accès Read-Only : Les comptes d'audit disposent d'un accès en lecture seule, interdisant toute modification manuelle des données.   
2. Non-Répudiation : Toute transaction sur le DLT est signée par la clé de service de la plateforme, rendant l'opération juridiquement opposable.   
3. Audit Log : Chaque accès au registre par un administrateur est enregistré dans un journal d'audit infalsifiable.

# MÉCANISME DE PREUVE POUR LE RÉGULATEUR

En cas d'inspection, la plateforme permet de vérifier qu'une ligne du registre correspond à un état réel du DLT :

$$
\operatorname {R e g i s t r e} _ {U s e r} \equiv \sum (\text {T r a n s a c t i o n s} _ {\text {o n - c h a i n}}) - \sum (\text {R e d e m p t i o n s} _ {\text {o n - c h a i n}}) \tag {1}
$$

Cette égalité mathématique prouve qu'aucun jeton n'a été créé ou détruit de manière "occulte" en dehors des processus réglementés de la plateforme.

# CONCLUSION

L'utilisation du DLT comme socle de conservation transforme le registre de FAN-Capital en un outil de conformité dynamique. Ce système offre une sécurité supérieure aux registres centralisés classiques en rendant la fraude techniquement impossible et l'audit instantané.
