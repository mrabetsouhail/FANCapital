# Implémentation du Dossier de Sécurité Institutionnelle v2.0

**Date** : Février 2025  
**Document de Référence** : `Dossier de Sécurité Institutionnelle V2.md`  
**Objectif** : Implémenter les mécanismes de sécurité manquants selon le Dossier de Sécurité v2.0

---

## 📋 Analyse du Document

### Exigences du Dossier de Sécurité v2.0

1. **Micro-Segmentation Cryptographique (WaaS)** ✅ Partiellement
2. **Isolation Matérielle et Hiérarchie des Clés** ⚠️ Partiellement
3. **HSM (Hardware Security Module) FIPS 140-2** ❌ Non implémenté
4. **Gouvernance Multi-Signature (3/5)** ⚠️ Partiellement
5. **Intégrité du Registre et Watcher** ✅ Implémenté
6. **Plan de Continuité d'Activité (PCA)** ❌ Non implémenté

---

## ✅ Éléments Déjà Implémentés

### 1. Micro-Segmentation Cryptographique (WaaS)

✅ **Wallet-as-a-Service** :
- Chaque utilisateur a une clé privée chiffrée (AES-256-GCM)
- Stockage dans MariaDB
- Abstraction totale pour l'utilisateur (pas de MetaMask)

✅ **Séparation des Clés** :
- `PANIC_PRIVATE_KEY` : Pause globale
- `MINT_PRIVATE_KEY` : Création de tokens
- `BURN_PRIVATE_KEY` : Destruction de tokens
- `OPERATOR_PRIVATE_KEY` : Opérations courantes

### 2. Intégrité du Registre et Watcher

✅ **AuditReconciliationService** :
- Réconciliation entre blockchain et MariaDB
- Formule : `Δ = Σ Soldes Blockchain - Σ Soldes MariaDB`
- Surveillance constante

✅ **Checkpoints d'Audit** :
- Génération tous les 10 000 blocs
- Hash-chain pour intégrité
- Stockage dans `audit_checkpoints`

---

## ⚠️ Éléments Partiellement Implémentés

### 1. Isolation Matérielle et Hiérarchie des Clés

**État Actuel** :
- ✅ Clé MINTER : `MINT_PRIVATE_KEY` (séparée)
- ⚠️ Clé ORACLE : Utilise `OPERATOR_PRIVATE_KEY` (partagée)
- ⚠️ Clé ONBOARDING : Utilise `OPERATOR_PRIVATE_KEY` (partagée)

**À Faire** :
- [ ] Créer `ORACLE_PRIVATE_KEY` (séparer de Operator)
- [ ] Créer `ONBOARDING_PRIVATE_KEY` (séparer de Operator)
- [ ] Créer services dédiés pour Oracle et Onboarding

### 2. Gouvernance Multi-Signature (3/5)

**État Actuel** :
- ✅ `MultiSigCouncil` contract déployé
- ⚠️ Pas utilisé comme `DEFAULT_ADMIN_ROLE`
- ⚠️ Pas de répartition des 5 signataires

**À Faire** :
- [ ] Utiliser `MultiSigCouncil` comme admin sur tous les contrats
- [ ] Créer `MultiSigService` backend
- [ ] Documenter la répartition des 5 signataires
- [ ] Créer endpoints backoffice pour gérer les propositions

---

## ❌ Éléments Non Implémentés

### 1. HSM (Hardware Security Module) FIPS 140-2

**Exigence** : Les clés doivent être stockées dans un HSM certifié FIPS 140-2, sans extraction possible.

**État Actuel** :
- ❌ Utilise variables d'environnement (clés en clair)
- ❌ Pas d'intégration HSM
- ❌ Pas de certification FIPS 140-2

**À Implémenter** :
- [ ] Interface HSM (abstraction)
- [ ] Intégration avec HSM réel (ex: AWS CloudHSM, Azure Dedicated HSM)
- [ ] Service HSM pour Mint, Oracle, Onboarding
- [ ] Documentation procédures HSM
- [ ] Tests avec HSM simulé

### 2. Extraction et Chiffrement

**Exigence** : Aucune extraction de clé possible. Communications via API signées.

**État Actuel** :
- ⚠️ Clés stockées en variables d'environnement (extractibles)
- ✅ WaaS chiffre les clés utilisateurs (AES-256-GCM)
- ❌ Pas de protection HSM pour clés de service

**À Implémenter** :
- [ ] Migration vers HSM pour clés de service
- [ ] API signées pour communications HSM
- [ ] Suppression des variables d'environnement pour clés critiques

### 3. Plan de Continuité d'Activité (PCA)

**Exigence** : Géoredondance Besu, RTO < 4 heures, RPO = 0.

**État Actuel** :
- ❌ Un seul nœud Hardhat local
- ❌ Pas de géoredondance
- ❌ Pas de configuration Besu production

**À Implémenter** :
- [ ] Configuration réseau Besu multi-nœuds
- [ ] Géoredondance (2+ data centers)
- [ ] Configuration IBFT 2.0 avec validateurs distribués
- [ ] Tests de failover
- [ ] Documentation RTO/RPO

---

## 🎯 Plan d'Implémentation

### Phase 1 : Séparation Stricte des Clés (Priorité Haute)

#### 1.1 Créer ORACLE_PRIVATE_KEY

**Fichiers à créer** :
- `backend/src/main/java/com/fancapital/backend/blockchain/service/OracleKeyService.java`
- `blockchain/scripts/grant-oracle-role.ts`
- `blockchain/scripts/get-oracle-key-address.ts`

**Modifications** :
- `BlockchainProperties.java` : Ajouter `oraclePrivateKey`
- `application.yml` : Ajouter `oracle-private-key`
- `PriceOracleWriteService.java` : Utiliser `OracleKeyService` au lieu de `OPERATOR_PRIVATE_KEY`

#### 1.2 Créer ONBOARDING_PRIVATE_KEY

**Fichiers à créer** :
- `backend/src/main/java/com/fancapital/backend/blockchain/service/OnboardingKeyService.java`
- `blockchain/scripts/grant-kyc-validator-role.ts`
- `blockchain/scripts/get-onboarding-key-address.ts`

**Modifications** :
- `BlockchainProperties.java` : Ajouter `onboardingPrivateKey`
- `application.yml` : Ajouter `onboarding-private-key`
- `KYCRegistryWriteService.java` : Utiliser `OnboardingKeyService` au lieu de `OPERATOR_PRIVATE_KEY`

---

### Phase 2 : Multi-Sig Governance (Priorité Haute)

#### 2.1 Utiliser MultiSigCouncil comme Admin

**Modifications** :
- `blockchain/scripts/deploy.ts` : Utiliser `MultiSigCouncil` comme admin
- `blockchain/scripts/deploy-factory-funds.ts` : Utiliser `MultiSigCouncil` comme admin
- Tous les contrats : Admin = `MultiSigCouncil` au lieu de deployer EOA

#### 2.2 Créer MultiSigService Backend

**Fichiers à créer** :
- `backend/src/main/java/com/fancapital/backend/backoffice/service/MultiSigService.java`
- `backend/src/main/java/com/fancapital/backend/backoffice/controller/MultiSigController.java`
- `backend/src/main/java/com/fancapital/backend/backoffice/model/MultiSigDtos.java`

**Fonctionnalités** :
- `submitTransaction()` : Soumettre une proposition
- `confirmTransaction()` : Confirmer une proposition
- `executeTransaction()` : Exécuter une proposition (si 3/5 confirmations)
- `listPendingTransactions()` : Lister les propositions en attente

#### 2.3 Documenter Répartition des Signataires

**Document à créer** :
- `docs/blockchain/GOUVERNANCE_MULTISIG.md`

**Contenu** :
- Liste des 5 signataires (selon Dossier de Sécurité)
- Procédures de signature
- Règles de quorum (3/5)
- Procédures d'urgence

---

### Phase 3 : Interface HSM (Priorité Moyenne)

#### 3.1 Créer Interface HSM

**Fichiers à créer** :
- `backend/src/main/java/com/fancapital/backend/security/hsm/HSMService.java` (interface)
- `backend/src/main/java/com/fancapital/backend/security/hsm/HSMServiceMock.java` (implémentation mock pour dev)
- `backend/src/main/java/com/fancapital/backend/security/hsm/HSMServiceCloudHSM.java` (implémentation AWS CloudHSM)
- `backend/src/main/java/com/fancapital/backend/security/hsm/HSMServiceAzure.java` (implémentation Azure Dedicated HSM)

**Méthodes** :
- `sign(byte[] data, String keyId)` : Signer avec une clé HSM
- `getPublicKey(String keyId)` : Obtenir la clé publique
- `listKeys()` : Lister les clés disponibles

#### 3.2 Migrer Services vers HSM

**Modifications** :
- `MintKeyService.java` : Utiliser `HSMService` au lieu de clé privée
- `OracleKeyService.java` : Utiliser `HSMService`
- `OnboardingKeyService.java` : Utiliser `HSMService`

**Configuration** :
- `application.yml` : Ajouter `hsm.provider` (mock, aws, azure)
- `application.yml` : Ajouter `hsm.key-ids.mint`, `hsm.key-ids.oracle`, `hsm.key-ids.onboarding`

---

### Phase 4 : Plan de Continuité d'Activité (Priorité Basse)

#### 4.1 Configuration Besu Multi-Nœuds

**Fichiers à créer** :
- `blockchain/besu/config/besu-node1.conf`
- `blockchain/besu/config/besu-node2.conf`
- `blockchain/besu/config/besu-node3.conf`
- `blockchain/besu/docker-compose.yml` (pour tests locaux)

**Documentation** :
- `docs/blockchain/BESU_PRODUCTION_SETUP.md`
- `docs/blockchain/GEO_REDONDANCE.md`

#### 4.2 Tests de Failover

**Scripts à créer** :
- `blockchain/scripts/test-failover.ts`
- `blockchain/scripts/check-consensus.ts`

---

## 📊 État d'Avancement

| Exigence | État | Priorité | Fichiers à Créer |
|----------|------|----------|------------------|
| Clé MINTER séparée | ✅ Fait | - | - |
| Clé ORACLE séparée | ❌ À faire | Haute | OracleKeyService, scripts |
| Clé ONBOARDING séparée | ❌ À faire | Haute | OnboardingKeyService, scripts |
| HSM FIPS 140-2 | ❌ À faire | Moyenne | HSMService, implémentations |
| Multi-Sig 3/5 | ⚠️ Partiel | Haute | MultiSigService, Controller |
| Répartition 5 signataires | ❌ À faire | Haute | Documentation |
| Watcher/Réconciliation | ✅ Fait | - | - |
| Checkpoints 10k blocs | ✅ Fait | - | - |
| Géoredondance Besu | ❌ À faire | Basse | Config Besu, Docker |

---

## 🚀 Prochaines Étapes Immédiates

### Étape 1 : Séparer Oracle et Onboarding (1-2 heures)

1. Créer `OracleKeyService` et `OnboardingKeyService`
2. Créer scripts d'attribution de rôles
3. Modifier les services existants pour utiliser les nouvelles clés
4. Tester

### Étape 2 : Multi-Sig Governance (2-3 heures)

1. Modifier scripts de déploiement pour utiliser `MultiSigCouncil`
2. Créer `MultiSigService` et `MultiSigController`
3. Créer endpoints backoffice
4. Documenter répartition des signataires

### Étape 3 : Interface HSM (4-6 heures)

1. Créer interface `HSMService`
2. Implémenter version mock pour dev
3. Migrer `MintKeyService` vers HSM
4. Documenter intégration HSM production

---

## 📝 Notes Techniques

### HSM Integration

Pour la production, les options HSM incluent :
- **AWS CloudHSM** : HSM géré dans AWS
- **Azure Dedicated HSM** : HSM dédié dans Azure
- **Thales Luna HSM** : HSM physique
- **YubiHSM** : HSM USB pour dev/test

### Multi-Sig Signataires (Selon Dossier de Sécurité)

1. Direction Technique (CTO)
2. Responsable de la Conformité (Compliance)
3. Membre du Conseil d'Administration
4. Tiers de Confiance / Séquestre (Cabinet d'audit)
5. Représentant de l'Intermédiaire en Bourse

### Géoredondance Besu

Pour RTO < 4 heures et RPO = 0 :
- Minimum 3 nœuds validateurs
- Distribution géographique (2+ data centers)
- Configuration IBFT 2.0 avec quorum
- Monitoring et alertes automatiques

---

## ✅ Checklist Finale

- [x] Analyse du Dossier de Sécurité v2.0
- [x] Identification des éléments manquants
- [x] Plan d'implémentation créé
- [ ] Clé ORACLE séparée
- [ ] Clé ONBOARDING séparée
- [ ] Multi-Sig Governance complète
- [ ] Interface HSM créée
- [ ] Migration vers HSM
- [ ] Configuration Besu production
- [ ] Documentation complète

---

**Date de Création** : Février 2025  
**Statut** : 📋 Plan d'Implémentation Créé
