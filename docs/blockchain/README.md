# Documentation Blockchain - FAN-Capital

## Vue d'ensemble

La partie blockchain de FAN-Capital est responsable de la tokenisation des actifs réels (Real World Assets - RWA) en **Certificats de Propriété Économique Fractionnée (CPEF)**. Cette infrastructure utilise une blockchain permissionnée basée sur **Hyperledger Besu** ou **Quorum** pour garantir la sécurité, la conformité réglementaire et la transparence.

## Caractéristiques principales

- **Blockchain Permissionnée** : Infrastructure sécurisée et contrôlée
- **Tokens CPEF** : Security Tokens conformes ERC-1404
- **Parité 1:1** : Chaque jeton est couvert à 100% par des actifs réels
- **Fractionnement** : Division jusqu'à 8 décimales
- **Validation Rapide** : Transactions validées en moins de 2 secondes
- **Zéro Frais de Gaz** : Aucun coût de transaction pour les utilisateurs
- **Audit en Temps Réel** : Nœud d'audit pour le CMF (Conseil du Marché Financier)

## Structure de la documentation

### Documents Techniques

1. [ARCHITECTURE.md](./ARCHITECTURE.md) - Architecture technique et infrastructure
2. [CPEF_TOKEN.md](./CPEF_TOKEN.md) - Spécifications du token CPEF
3. [SMART_CONTRACTS.md](./SMART_CONTRACTS.md) - Contrats intelligents (consolidation)
4. [NODE_AUDIT.md](./NODE_AUDIT.md) - Nœud d'audit CMF
5. [API_INTEGRATION.md](./API_INTEGRATION.md) - Intégration avec le backend Spring Boot
6. [DEPLOYMENT.md](./DEPLOYMENT.md) - Déploiement et configuration
7. [COMPLIANCE.md](./COMPLIANCE.md) - Conformité réglementaire et KYC

### Documents Économiques et Financiers

8. [ECONOMIC_MODEL.md](./ECONOMIC_MODEL.md) - Modèle économique Freemium 70/30
9. [PRICING.md](./PRICING.md) - Grille tarifaire officielle (inclut l’**Option de Réservation** §10)
10. [CREDIT_LOMBARD.md](./CREDIT_LOMBARD.md) - Avance sur titres (Crédit Lombard)

### Documents d'Analyse

11. [ANALYSE_DOCUMENTS_COMPLETE.md](./ANALYSE_DOCUMENTS_COMPLETE.md) - Analyse complète de tous les documents
12. [Analyse de lOption de Réservation CPEF.md](./Analyse%20de%20lOption%20de%20R%C3%A9servation%20CPEF.md) - Note d'ingénierie (option de réservation sur stock CPEF)

## Technologies utilisées

- **Hyperledger Besu** ou **Quorum** : Blockchain permissionnée Ethereum
- **Solidity** : Langage pour les smart contracts
- **Web3.js / Ethers.js** : Bibliothèques d'interaction avec la blockchain
- **Truffle / Hardhat** : Framework de développement et déploiement

## Workflow principal

1. **Émission de tokens** : Lorsqu'un actif réel est déposé chez l'IB, des tokens CPEF correspondants sont émis
2. **Transactions** : Les utilisateurs peuvent acheter/vendre des tokens via la plateforme
3. **Liquidité** : La "Piscine de Liquidité" rachète instantanément les tokens en cas de vente
4. **Audit** : Le nœud d'audit vérifie en continu la parité 1:1 entre tokens et actifs réels
5. **Conformité** : Les règles KYC et de plafonds sont appliquées automatiquement via les smart contracts
6. **Réservation (option)** : L’utilisateur peut réserver un prix \(K\) pour acheter \(Q\) CPEF avant \(T\) en payant une prime \(A\) (voir [PRICING.md](./PRICING.md) §10 et la note [Analyse de lOption de Réservation CPEF.md](./Analyse%20de%20lOption%20de%20R%C3%A9servation%20CPEF.md))

## Liens avec les autres parties du projet

- **Backend Spring Boot** : Gère la logique métier et communique avec la blockchain via Web3
- **Frontend Angular** : Interface utilisateur qui interagit avec le backend, qui à son tour communique avec la blockchain
