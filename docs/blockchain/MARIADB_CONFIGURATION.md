# Configuration MariaDB pour Persistance à Long Terme

## Conformité Livre Blanc FAN-Capital v2.1 - Section 5

Le registre off-chain, gérant la hash-chain d'audit et les métadonnées KYC, repose sur une base de données **MariaDB**. Ce choix garantit la persistance à long terme et la conformité avec les exigences de conservation des logs d'audit sur plusieurs années, contrairement aux solutions de stockage volatiles.

## Configuration

### 1. Fichier de Configuration

Le backend Spring Boot supporte deux profils de base de données :

- **H2** (développement) : `application.yml` - Base de données en mémoire/fichier pour le développement
- **MariaDB** (production) : `application-mariadb.yml` - Base de données persistante pour la production

### 2. Activation du Profil MariaDB

Pour utiliser MariaDB en production, activez le profil Spring Boot :

```bash
# Via variable d'environnement
export SPRING_PROFILES_ACTIVE=mariadb

# Ou via ligne de commande
java -jar backend.jar --spring.profiles.active=mariadb
```

### 3. Variables d'Environnement

Configurez les variables d'environnement suivantes :

```bash
# URL de connexion MariaDB
export DB_URL=jdbc:mariadb://127.0.0.1:3306/fancapital

# Identifiants
export DB_USERNAME=root
export DB_PASSWORD=votre_mot_de_passe

# Activer le profil
export SPRING_PROFILES_ACTIVE=mariadb
```

### 4. Structure de la Base de Données

Les tables suivantes sont créées automatiquement via Hibernate (`ddl-auto: update`) :

#### Tables d'Audit
- `audit_log_entries` : Hash-chain d'audit (append-only)
- `audit_checkpoints` : Checkpoints d'audit tous les 10 000 blocs
- `audit_token_sync_state` : État de synchronisation des tokens
- `audit_user_token_balance` : Soldes des utilisateurs par token
- `audit_alerts` : Alertes de non-conformité
- `business_context_mappings` : Mapping transactions → BusinessContextId

#### Tables Utilisateurs
- `app_users` : Utilisateurs et portefeuilles WaaS
- `kyc_documents` : Documents KYC (si applicable)

#### Tables Métier
- Tables spécifiques à votre domaine métier

### 5. Sauvegarde et Conservation

#### Sauvegarde Régulière

```bash
# Sauvegarde complète
mysqldump -u root -p fancapital > backup_$(date +%Y%m%d).sql

# Sauvegarde avec compression
mysqldump -u root -p fancapital | gzip > backup_$(date +%Y%m%d).sql.gz
```

#### Conservation des Logs d'Audit

Conformément aux exigences réglementaires tunisiennes, les logs d'audit doivent être conservés pendant **plusieurs années**. 

**Recommandations** :
- Sauvegarde quotidienne avec rétention de 7 jours
- Sauvegarde hebdomadaire avec rétention de 4 semaines
- Sauvegarde mensuelle avec rétention de 12 mois
- Sauvegarde annuelle avec rétention permanente

### 6. Performance et Optimisation

#### Index Recommandés

Les index suivants sont créés automatiquement par Hibernate, mais peuvent être optimisés :

```sql
-- Index sur les transactions pour recherche rapide
CREATE INDEX idx_business_context_tx_hash ON business_context_mappings(transaction_hash);
CREATE INDEX idx_business_context_id ON business_context_mappings(business_context_id);

-- Index sur les checkpoints pour recherche par bloc
CREATE INDEX idx_checkpoint_block ON audit_checkpoints(block_number);
CREATE INDEX idx_checkpoint_token_block ON audit_checkpoints(token_address, block_number);

-- Index sur les logs d'audit pour recherche temporelle
CREATE INDEX idx_audit_log_created ON audit_log_entries(created_at);
```

#### Partitionnement (Optionnel)

Pour les très grandes tables, considérez le partitionnement par date :

```sql
-- Exemple de partitionnement mensuel pour audit_log_entries
ALTER TABLE audit_log_entries
PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202601 VALUES LESS THAN (202602),
    PARTITION p202602 VALUES LESS THAN (202603),
    -- ... etc
);
```

### 7. Sécurité

#### Chiffrement des Données Sensibles

Les clés privées des portefeuilles WaaS sont stockées chiffrées (AES-GCM) dans la colonne `wallet_private_key_enc` de la table `app_users`.

#### Accès Restreint

- Utilisez des utilisateurs MySQL avec privilèges minimaux
- Activez SSL/TLS pour les connexions distantes
- Limitez l'accès réseau à la base de données

### 8. Migration depuis H2

Si vous migrez depuis H2 vers MariaDB :

1. **Export depuis H2** :
   ```bash
   # Via H2 Console ou script d'export
   ```

2. **Import dans MariaDB** :
   ```bash
   mysql -u root -p fancapital < export_h2.sql
   ```

3. **Vérification** :
   ```sql
   SELECT COUNT(*) FROM audit_log_entries;
   SELECT COUNT(*) FROM app_users;
   ```

### 9. Monitoring

#### Vérification de l'État

```sql
-- Nombre de checkpoints générés
SELECT COUNT(*) FROM audit_checkpoints;

-- Dernier checkpoint par token
SELECT token_address, MAX(block_number) as last_checkpoint
FROM audit_checkpoints
GROUP BY token_address;

-- Nombre de transactions enregistrées
SELECT COUNT(*) FROM business_context_mappings;
```

#### Alertes

Configurez des alertes pour :
- Espace disque < 20%
- Nombre de checkpoints manquants
- Écarts dans la hash-chain d'audit

## Conclusion

La configuration MariaDB garantit la persistance à long terme des données d'audit conformément aux exigences réglementaires. Cette infrastructure permet de conserver l'historique complet des transactions et des métadonnées KYC sur plusieurs années, assurant la traçabilité totale requise par le CMF.
