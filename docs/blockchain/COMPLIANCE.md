# Conformité Réglementaire et KYC - FAN-Capital

## Vue d'ensemble

Ce document décrit les mécanismes de conformité réglementaire et de KYC (Know Your Customer) intégrés dans l'écosystème blockchain FAN-Capital.

---

## 1. Conformité Réglementaire

### 1.1 Cadre Légal Tunisien

**Régulateur** : Conseil du Marché Financier (CMF)

**Exigences** :
- **Parité 1:1** : Chaque token couvert par actif réel
- **Audit** : Nœud d'audit CMF en temps réel
- **Transparence** : Accès complet aux données
- **KYC** : Vérification identité obligatoire
- **Fiscalité** : Conformité avec code fiscal tunisien

### 1.2 Tokenisation d'Actifs

**Conformité** :
- Actifs réels déposés chez Intermédiaire en Bourse (IB)
- Certificats de dépôt vérifiables
- Audit trimestriel externe
- Traçabilité complète sur blockchain

---

## 2. KYC (Know Your Customer)

### 2.1 Processus d'Enrôlement

**Étapes** :

1. **Inscription** : Création compte utilisateur
2. **Documents** : Fourniture documents d'identité
   - Carte d'identité nationale
   - Justificatif de domicile
   - RIB (Relevé d'Identité Bancaire)
3. **Vérification** : Validation par FAN-Capital
4. **Whitelisting** : Ajout adresse wallet à la whitelist
5. **Activation** : Compte activé selon niveau

### 2.2 Niveaux KYC

#### Niveau 1 : Green List

**Caractéristiques** :
- **Processus** : Simplifié (documents de base)
- **Plafond** : 5000 TND maximum
- **Transferts P2P** : ❌ Bloqués
- **Opérations autorisées** :
  - ✅ Mint (achat via piscine)
  - ✅ Burn (rachat via piscine)
  - ❌ Transfer (P2P interdit)

**Utilisation** : Épargne de masse, petits investisseurs

#### Niveau 2 : White List

**Caractéristiques** :
- **Processus** : Complet (vérification approfondie)
- **Plafond** : ✅ Aucun (levée des plafonds)
- **Transferts P2P** : ✅ Autorisés
- **Opérations autorisées** :
  - ✅ Mint (achat via piscine)
  - ✅ Burn (rachat via piscine)
  - ✅ Transfer (P2P autorisé)

**Utilisation** : Investisseurs confirmés, volumes importants

### 2.3 Attributs Utilisateur

**Statut Fiscal** :
- **Résident** : Résident fiscal tunisien
  - RAS : 10% sur plus-value
- **Non-Résident** : Non-résident fiscal
  - RAS : 15% sur plus-value (sauf convention bilatérale)

**Niveau de Service** :
- BRONZE (gratuit)
- SILVER
- GOLD
- DIAMOND
- PLATINUM

---

## 3. Restrictions de Transfert (ERC-1404)

### 3.1 Codes de Restriction

| Code | Signification | Action |
|------|---------------|--------|
| 0 | Aucune restriction | ✅ Transfert autorisé |
| 1 | Adresse non whitelistée | ❌ Transfert bloqué |
| 2 | Plafond dépassé | ❌ Transfert bloqué |
| 3 | Transfert P2P non autorisé (Green List) | ❌ Transfert bloqué |
| 4 | Tokens en séquestre | ❌ Transfert bloqué |
| 5 | Contrat en pause | ❌ Transfert bloqué |

### 3.2 Vérification Automatique

**Fonction** : `detectTransferRestriction()`

**Processus** :
1. Vérification whitelist (from et to)
2. Vérification niveau KYC
3. Vérification plafonds (si Green List)
4. Vérification séquestre (si avance active)
5. Vérification état contrat (pause)
6. Retour code de restriction

---

## 4. Gestion des Plafonds

### 4.1 Plafonds Green List

**Plafond Total** : 5000 TND

**Calcul** :
- Solde tokens × VNI actuelle ≤ 5000 TND
- Vérification à chaque mint
- Blocage si dépassement

**Levée** : Passage à White List

### 4.2 Plafonds par Transaction

**Minimum** :
- Achat : 100 TND (souscription minimale)

**Maximum** :
- Green List : 5000 TND total
- White List : Aucun

---

## 5. Révocation et Suspension

### 5.1 Révocation d'Adresse

**Conditions** :
- Soupçon de fraude
- Blanchiment d'argent
- Non-conformité réglementaire
- Décision administrative

**Processus** :
1. Décision FAN-Capital / CMF
2. Appel `removeFromWhitelist(address)`
3. Blocage immédiat de tous les transferts
4. Notification utilisateur

**Effets** :
- ❌ Tous les transferts bloqués
- ❌ Mint/Burn bloqués
- ✅ Consultation solde possible
- ✅ Rachat via processus exceptionnel (si autorisé)

### 5.2 Suspension Temporaire

**Conditions** :
- Enquête en cours
- Vérification documents
- Suspicion légère

**Processus** :
- Suspension temporaire (durée limitée)
- Réactivation après vérification

---

## 6. Fiscalité Automatisée

### 6.1 Retenue à la Source (RAS)

**Calcul** :
```
Plus-value = (VNI_sortie - PRM) × Quantité
RAS = Plus-value × Taux_RAS
```

**Taux** :
- **Résident** : 10%
- **Non-Résident** : 15%

**Moment** : Prélevée lors du `burn()` (rachat)

**Destination** : TaxVault → Reversement au fisc

**Règle P2P (décision de design)** :
- **Aucune RAS n’est prélevée au moment d’un transfert ou d’un règlement P2P**.
- La RAS est **prélevée uniquement lors du rachat via la Piscine** (sortie en TND), conformément au modèle “différé”.
- Justification : liquidité du P2P + complexité PRM/plus-value en temps réel côté vendeur.

### 6.2 TVA sur Commissions

**Taux** : 19%

**Application** : Sur toutes les commissions
- Commissions piscine
- Commissions P2P

**Calcul** :
```
Commission_réelle = Commission_niveau × (1 + 0.19)
```

### 6.3 PRM (Prix de Revient Moyen)

**Objectif** : Calculer la plus-value réelle

**Mise à jour** :
- À chaque `mint()` (achat)
- À chaque `transfer()` P2P (conservation PRM)

**Stockage** : Par adresse utilisateur

---

## 7. Audit et Traçabilité

### 7.1 Nœud d'Audit CMF

**Fonctionnalités** :
- Vérification parité 1:1 en temps réel
- Audit des transactions
- Surveillance KYC
- Rapports automatiques

**Accès** : Lecture seule, API dédiée

### 7.2 Traçabilité Complète

**Événements Blockchain** :
- Tous les Mint (émissions)
- Tous les Burn (rachats)
- Tous les Transfer (P2P)
- Toutes les avances
- Toutes les mises à jour VNI

**Historique** : Immutable, consultable à tout moment

---

## 8. Conformité Internationale

### 8.1 Standards

**ERC-1404** : Security Token Standard
- Restrictions de transfert intégrées
- Conformité native

**AML/CFT** : Anti-Money Laundering / Combating Financing of Terrorism
- Vérification identité
- Surveillance transactions
- Reporting suspect

### 8.2 Conventions Fiscales

**Non-Résidents** :
- Taux standard : 15%
- Réduction possible selon convention bilatérale
- Documentation requise

---

## 9. Procédures de Conformité

### 9.1 Onboarding Utilisateur

**Checklist** :
- [ ] Documents identité fournis
- [ ] Vérification documents
- [ ] Vérification liste noire (sanctions)
- [ ] Détermination niveau KYC
- [ ] Ajout whitelist
- [ ] Configuration statut fiscal
- [ ] Activation compte

### 9.2 Monitoring Continu

**Surveillance** :
- Transactions suspectes
- Dépassements plafonds
- Patterns anormaux
- Conformité continue

**Alertes** :
- Transactions importantes
- Dépassements seuils
- Anomalies détectées

---

## 10. Documentation et Reporting

### 10.1 Rapports Réglementaires

**Fréquence** :
- **Quotidien** : Résumé transactions
- **Hebdomadaire** : Statistiques consolidées
- **Mensuel** : Rapport fiscal complet
- **Trimestriel** : Audit de solvabilité

### 10.2 Documentation Utilisateur

**Documents** :
- Guide utilisateur
- Conditions générales
- Politique de confidentialité
- Informations fiscales

---

## 11. Gestion des Données Personnelles

### 11.1 Protection des Données

**Conformité** : RGPD (si applicable) + Loi tunisienne

**Stockage** :
- Données KYC : Base de données sécurisée (backend)
- Adresses blockchain : Publiques (par design)
- Données personnelles : Chiffrées

### 11.2 Droits Utilisateurs

- Accès aux données
- Rectification
- Suppression (si autorisé)
- Portabilité

---

## 12. Checklist Conformité

### Déploiement

- [ ] Processus KYC documenté
- [ ] Whitelisting automatisé
- [ ] Restrictions ERC-1404 implémentées
- [ ] Fiscalité automatisée
- [ ] Nœud audit CMF configuré
- [ ] Documentation réglementaire complète

### Opérationnel

- [ ] Monitoring continu actif
- [ ] Rapports générés automatiquement
- [ ] Procédures de révocation testées
- [ ] Support utilisateur formé
- [ ] Conformité vérifiée régulièrement

---

*Document créé le 26 janvier 2026*
*Version 1.0*
