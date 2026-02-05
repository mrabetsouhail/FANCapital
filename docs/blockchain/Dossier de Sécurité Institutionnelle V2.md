# FAN-Capital

Dossier de Sécurité Institutionnelle v2.0 Architecture de Micro-Segmentation et Conformité Avancée

Direction Technique

Février 2026

# Résumé

Ce dossier expose les mécanismes de sécurité de la plateforme FAN-Capital, révisés pour inclure une micro-segmentation cryptographique et une gouvernance multi-signature distribuée. Ces mesures visent à éliminer tout point de défaillance unique (SPoF) et à garantir une synchronisation parfaite entre les actifs réels et les registres numériques, conformément aux directives du Conseil du Marché financier (CMF).

# Table des matieres

1 Introduction 3   
2 Micro-Segmentation Cryptographique (WaaS) 3

2.1 Isolation Matérielle et Hierarchie des Clés 3   
2.2 Extraction et Chiffrement 3

3 Gouvernance et Protocole Multi-Signature (3/5) 3   
3.1 Répartition des Signataires 3   
4 Integrité du Registre et Watcher 4   
4.1 Algorithm de Réconciliation 4

5 Matrice de Conformité Réglementaire 4   
6 Plan de Continuite d'Activite (PCA) 4

# INTRODUCTION

La strategie de FAN-Capital repose sur une Défense en Profondeur active. Cette version 2.0 du dossier met l'accent sur la souverinaté technologique en circuit fermé, où chaque service critique dispose de son propre périmètre de sécurité isolé.

# MICRO-SEGMENTATION CRYPTOGRAPHIQUE (WAAS)

L'architecture de gestion des clés privées est désormais segmentée par service pour limiter la portée d'un incident potentiel.

# Isolation Matérielle et Hierarchy des Clés

L'infrastructure utilise un HSM (Hardware Security Module) certifié FIPS 140-2. Plutôt qu'une clé unique, le système génére des clés de service distinctes :

- Clé MINTER : Réservée exclusivement à l'émission des jetons Atlas et Didon.   
- Clé ORACLE : Dédiée à la mise à jour des prix en provenance de la BVMT.   
- Clé ONBOARDING : Utilisée pour la validation KYC et la création de comptes.

Impact: Une vulnérabilité sur le service Oracle ne peut enaucun cas comprométtre les fonctions de Minting ou les avons des utilisateurs.

# Extraction et Chiffrement

Aucune extraction de clé n'est possible. Les communications entre le backend Spring Boot et le HSM s'effectuent via des appel API signés, sans jamais exposer les clés en clair dans l'environnement applicatif.

# Gouvernance ET PROTOCOLE MULTI-SIGNATURE (3/5)

Les actions critiques (modification des paramètres de Smart Contract, accès aux fonds de réserve) sont soumises à un consensus de type M-of-N.

# Répartition des Signataires

Pour prévenir toute collusion interne, les 5 parts de la clé de gouvernance sont réparties comme suit :

1. Direction Technique (CTO): Responsible de l'intégrité logicielle.   
2. Responsible de la Conformité (Compliance): Garant du respect LBA/FT.   
3. Membre du Conseil d'Administration: Représentant des actionnaires.   
4. Tiers de Confiance / Séquestre : Cabinet d'audit externe.   
5. Représentant de l'Intermédiaire en Bourse : Garant de l'adossement aux actifs réels.

# INTEGRITÉ DU REGISTRE ET WATCHER

# Algorithm de Réconciliation

Le service "Watcher" opère une surveillance constante selon la formule :

$$
\Delta = \sum \text {S o l d e s B l o c k c h a i n} - \sum \text {S o l d e s M a r i a D B}
$$

Pour optimiser les performances, le Watcher s'appuie sur les Checkpoints d'Audit généres tous les 10 000 blocs, permettant une validation différentielle sans saturer les nœuds Hyperledger Besu.

# MATRICE DE CONFORMITE RÉGLEMENTAIRE

Table 1: Synthèse de l'alignement réglementaire.   

<table><tr><td>Exigence CMF</td><td>Spécification Technique FAN-Capital</td></tr><tr><td>Traçabilité</td><td>Registre DLT IBFT 2.0 (Hyperledger Besu)</td></tr><tr><td>Non-Collusion</td><td>Gouvernance Multi-sig 3/5 distribuée</td></tr><tr><td>Souverinaté</td><td>Modèle WaaS en Circuit Fermé (No External Wallets)</td></tr><tr><td>Intégrité LBA/FT</td><td>Whitelisting On-Chain : isWhitelisted(address) == true</td></tr><tr><td>Résilience</td><td>Géo-rendondance Besu + MariaDB (RPO = 0)</td></tr></table>

# PLAN DE CONTINUITE D'ACTIVITE (PCA)

La résilience est assurée par une redondance totale. Les nœuds validateurs du réseau Besu sont distribués géographiquement. Si un data-center complet devient indisponible, le consensus IBFT 2.0 continue de valider les blocs grâce aux nœuds du site de secours, garantissant un Recovery Time Objective (RTO) $< 4$ heures.

Fin du Document - Version 2.0 Certifiée
