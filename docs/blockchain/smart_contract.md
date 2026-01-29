# Dossier de Spécifications Fonctionnelles et Techniques Système de Gestion de Certificats CPEF

Équipe Projet FAN-Capital

Déprétre 2025

# 1 Introduction et Objectifs

Ce document définit les règles de gestion du Smart Contract CPEF. L'objet est de digitaliser le cycle de vie d'un titre financier tout en respectant strictement les contraintes réglementaires du marché financier tunisien.

# 2 Gestion de l'Identité (KYC et Whitelisting)

L'accès à l'actif est restreint par un registre d'adresses approuvées.

- Processus d'enrollement : L'investisseur doit fournir ses documents d'identité à FAN-Capital. Une fois validée, son adresse wallet est ajoutée à la Whitelist.  
- Attributes des investisseurs : Chaque adresse peut etre associée a un statut (Résident, Non-Résident) pour le calcul automatique de la fiscalité.  
- Révocation : En cas de soupçon de fraude ou de blanchiment, l'administrateur peut révoquer l'adresse instantanément, bloquant ainsi tout mouvement de jetsons.

# 3 Mécanisme d'émission et de Souscription

L'émission des titres se fait au fil de l'eau (Émission Permanente).

- Calcul du prix d'entrée : Le prix est basé sur la première VNI (Valeur Nete d'Inventaire) publiée par le dépositaire.  
- Plafonds d'investissement : Le contrat peut imposer une souscription minimale (ex : 100 TND) et un plafond par investisseur pour limiter la concentration des risques.  
- Validation de paiement : Les jetons ne sont transférés à l'investisseur qu'après confirmation de la réception des fonds sur le compte fiduciaire du fonds.

# 4 Valorisation et Oracle de Prix

La transparence du prix est assurée par une mise a jour reguliere sur la blockchain.

- Mise à jour de la VNI : Une fonction sécurisée permet demettre à jour le prix. Cette action est réservée au role PRICE ORACLE.  
- Historique des prix : Toutes les anciennes valeurs de VNI restent consultables dans l'histoire du contrat, garantissant une traçabilité totale pour les auditeurs.  
- Stabilité : Un mécanisme empêche les variations de prix extrêmes dues à des erreurs de saisie (validation par seuils de pourcentage).

# 5 Sortie et Rachat (Redemption)

Le rachat permet à l'investisseur de liquider ses positions.

- Demande de rachat : L'investisseur envoie ses jetons vers une adresse de "burn" (destruction) ou un compte de séquestre.  
- Frais de sortie : Des frais dégressifs peuvent être appliqués en fonction de la durée de détention (ex : 2% si détenu < 6 mois, 0% après).

# 6 Fiscalité et Retenue à la Source

Automatisation des prélevements légaux.

- Calcul de la plus-value : Le contrat stocke le prix d'achat moyen pour chaque investisseur, afin de calculer la plus-value réelle au moment du rachat.  
- Prélevement automatique : Lors du rachat, le montant de la taxa est calculé selon la résidence fiscale de l'investisseur (Retenue à la source).

# 7 Governance et Sécurité

- Multi-Signature : Les décisions critiques nécessitent la validation de plusieurs administrateurs.  
- Pause d'Urgence : En cas de falile de sécurité, le "Circuit Breaker" permet de geler toutes les transactions.
