# Nœud d'Audit CMF - Spécifications Techniques

## Vue d'ensemble

Le nœud d'audit est un nœud blockchain dédié au **Conseil du Marché Financier (CMF)** de Tunisie, permettant une surveillance réglementaire en temps réel de l'écosystème FAN-Capital.

---

## 1. Objectifs

### Surveillance Réglementaire

- **Parité 1:1** : Vérification continue que les tokens émis correspondent exactement aux actifs réels
- **Conformité** : Respect des règles KYC et plafonds
- **Transparence** : Accès complet à l'historique des transactions
- **Traçabilité** : Audit trail complet pour investigations

---

## 2. Architecture du Nœud

### Type de Nœud

- **Full Node** : Réplication complète de la blockchain
- **Read-Only** : Aucune capacité d'écriture
- **Dédié** : Infrastructure séparée pour le CMF

### Infrastructure

```
┌─────────────────────────────────────┐
│      Infrastructure CMF              │
├─────────────────────────────────────┤
│                                     │
│  ┌──────────────────────────────┐   │
│  │   Nœud Audit (Full Node)     │   │
│  │   - Réplication blockchain   │   │
│  │   - Lecture seule            │   │
│  └──────────────┬───────────────┘   │
│                 │                    │
│  ┌──────────────▼───────────────┐   │
│  │   API d'Audit                │   │
│  │   - Endpoints dédiés         │   │
│  │   - Authentification         │   │
│  └──────────────┬───────────────┘   │
│                 │                    │
│  ┌──────────────▼───────────────┐   │
│  │   Dashboard CMF               │   │
│  │   - Visualisation temps réel  │   │
│  │   - Rapports automatiques    │   │
│  └──────────────────────────────┘   │
│                                     │
└─────────────────────────────────────┘
```

---

## 3. Fonctionnalités d'Audit

### 3.1 Vérification Parité 1:1

**Objectif** : S'assurer que `TotalSupply` (blockchain) = `Actifs en réserve` (IB)

**Processus** :
1. Lecture `totalSupply()` depuis CPEFToken
2. Récupération données actifs réels depuis IB (API)
3. Comparaison en temps réel
4. Alerte si écart détecté

**Fréquence** : Vérification continue (chaque bloc)

**Seuil d'Alerte** : Écart > 0.01% (tolérance technique)

### 3.2 Audit des Transactions

**Types de Transactions Surveillées** :

- **Mint** : Émission de nouveaux tokens
- **Burn** : Rachat et destruction de tokens
- **Transfer** : Transferts P2P
- **Advance** : Demandes d'avance sur titres
- **VNI Updates** : Mises à jour de la VNI

**Vérifications** :
- Conformité KYC
- Respect des plafonds
- Calculs fiscaux corrects
- Prix d'exécution conformes

### 3.3 Surveillance KYC

**Vérifications** :
- Adresses whitelistées uniquement
- Respect des restrictions Green List
- Plafonds respectés
- Révocations effectives

### 3.4 Audit Fiscal

**Vérifications** :
- Calcul RAS correct
- Taux appliqués conformes (10% / 15%)
- TVA sur commissions (19%)
- Transferts vers TaxVault

---

## 4. API d'Audit

### Endpoints Principaux

#### 4.1 État Général

```http
GET /api/audit/status
```

**Réponse** :
```json
{
  "totalSupply": "1000000.00000000",
  "assetsInReserve": "1000000.00000000",
  "parity": "1.00000000",
  "status": "OK",
  "lastUpdate": "2026-01-26T10:30:00Z"
}
```

#### 4.2 Vérification Parité

```http
GET /api/audit/parity-check
```

**Réponse** :
```json
{
  "blockchainSupply": "1000000.00000000",
  "realAssets": "1000000.00000000",
  "difference": "0.00000000",
  "percentage": "0.00%",
  "status": "COMPLIANT",
  "timestamp": "2026-01-26T10:30:00Z"
}
```

#### 4.3 Transactions Récentes

```http
GET /api/audit/transactions?limit=100&type=mint
```

**Paramètres** :
- `limit` : Nombre de transactions (max 1000)
- `type` : mint, burn, transfer, advance
- `from` : Date début (optionnel)
- `to` : Date fin (optionnel)

**Réponse** :
```json
{
  "transactions": [
    {
      "hash": "0x...",
      "type": "mint",
      "from": "0x...",
      "to": "0x...",
      "amount": "100.00000000",
      "price": "125.50",
      "timestamp": "2026-01-26T10:25:00Z",
      "status": "SUCCESS"
    }
  ],
  "total": 100
}
```

#### 4.4 Utilisateurs par Niveau

```http
GET /api/audit/users-by-level
```

**Réponse** :
```json
{
  "greenList": 1500,
  "whiteList": 500,
  "total": 2000
}
```

#### 4.5 Audit Fiscal

```http
GET /api/audit/tax-report?from=2026-01-01&to=2026-01-31
```

**Réponse** :
```json
{
  "period": {
    "from": "2026-01-01",
    "to": "2026-01-31"
  },
  "totalRAS": "5000.00",
  "totalTVA": "1500.00",
  "residents": {
    "count": 100,
    "ras": "3000.00"
  },
  "nonResidents": {
    "count": 20,
    "ras": "2000.00"
  }
}
```

---

## 5. Dashboard CMF

### Vue d'Ensemble

**Métriques Principales** :
- Parité 1:1 (indicateur temps réel)
- Total Supply vs Actifs Réels
- Nombre de transactions (24h)
- Volume échangé (24h)
- Nombre d'utilisateurs actifs
- État de la piscine de liquidité

### Alertes

**Types d'Alertes** :
- ⚠️ **Critique** : Écart parité > 1%
- ⚠️ **Important** : Écart parité > 0.1%
- ℹ️ **Info** : Transaction suspecte
- ℹ️ **Info** : Mise à jour VNI > 10%

**Notifications** :
- Email automatique
- SMS (pour alertes critiques)
- Dashboard temps réel

### Rapports

**Rapports Automatiques** :
- **Quotidien** : Résumé des transactions
- **Hebdomadaire** : Statistiques consolidées
- **Mensuel** : Rapport fiscal complet
- **Trimestriel** : Audit de solvabilité

---

## 6. Sécurité et Accès

### Authentification

- **Certificats SSL/TLS** : Communication chiffrée
- **API Keys** : Authentification des requêtes
- **Rate Limiting** : Protection contre abus
- **Whitelist IP** : Accès restreint aux IPs CMF

### Autorisations

**Rôles** :
- **Auditeur** : Lecture seule, accès dashboard
- **Administrateur** : Accès complet, génération rapports
- **Super Admin** : Configuration nœud

### Logs et Traçabilité

- **Tous les accès** : Loggés avec timestamp
- **Requêtes API** : Traçées
- **Alertes** : Historique complet
- **Rétention** : 7 ans (conformité réglementaire)

---

## 7. Intégration avec IB (Intermédiaire en Bourse)

### Synchronisation des Données

**Source de Vérité** : IB (actifs réels)

**Processus** :
1. IB expose API avec état des actifs
2. Nœud audit interroge périodiquement
3. Comparaison avec blockchain
4. Alerte si divergence

**Fréquence** : Toutes les heures (ou temps réel si possible)

### Format des Données IB

```json
{
  "timestamp": "2026-01-26T10:30:00Z",
  "totalAssets": "1000000.00",
  "breakdown": {
    "equityHigh": "500000.00",
    "equityMedium": "500000.00"
  },
  "cash": "0.00"
}
```

---

## 8. Performance

### Métriques Cibles

- **Latence** : < 1 seconde pour requêtes API
- **Disponibilité** : 99.9%
- **Synchronisation** : < 2 secondes de retard max
- **Throughput** : 1000+ requêtes/seconde

### Optimisations

- **Indexation** : Base de données indexée pour requêtes rapides
- **Cache** : Mise en cache des données fréquentes
- **CDN** : Pour dashboard (si web)
- **Load Balancing** : Si plusieurs instances

---

## 9. Maintenance

### Tâches Régulières

- **Backup** : Sauvegarde quotidienne
- **Mise à jour** : Mise à jour logiciel mensuelle
- **Monitoring** : Surveillance 24/7
- **Tests** : Tests de récupération trimestriels

### Support

- **Hotline** : Support technique dédié
- **Documentation** : Guide utilisateur CMF
- **Formation** : Formation des auditeurs CMF

---

## 10. Conformité Réglementaire

### Exigences CMF

- **Accès permanent** : 24/7
- **Données complètes** : Historique intégral
- **Rapports** : Format conforme CMF
- **Audit trail** : Traçabilité complète

### Documentation

- **Manuel utilisateur** : Guide CMF
- **API Documentation** : Swagger/OpenAPI
- **Schémas de données** : Formats JSON
- **Procédures** : Guides opérationnels

---

*Document créé le 26 janvier 2026*
*Version 1.0*
