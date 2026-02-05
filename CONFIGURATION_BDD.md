# Configuration des Bases de Données - FAN-Capital

## 📊 Bases de Données Supportées

Le projet FAN-Capital supporte **deux bases de données** selon l'environnement :

### 1. **H2** (Développement) - Par Défaut

**Configuration** : `backend/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: "jdbc:h2:file:./data/fancapital;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE"
    driverClassName: "org.h2.Driver"
    username: "sa"
    password: ""
```

**Caractéristiques** :
- ✅ Base de données **embarquée** (pas d'installation requise)
- ✅ Stockage en **fichier** (`./data/fancapital.mv.db`)
- ✅ Parfait pour le **développement** et les **tests**
- ✅ Console H2 accessible : `http://localhost:8081/h2-console`
- ⚠️ **Non recommandé pour la production** (limitations de performance et persistance)

**Utilisation** :
- Démarrage automatique sans configuration
- Données persistées dans `backend/data/fancapital.mv.db`

---

### 2. **MariaDB** (Production) - Conforme Livre Blanc v2.1

**Configuration** : `backend/src/main/resources/application-mariadb.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mariadb://127.0.0.1:3306/fancapital}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
    driver-class-name: org.mariadb.jdbc.Driver
```

**Caractéristiques** :
- ✅ Base de données **production-ready**
- ✅ **Persistance à long terme** (conforme Livre Blanc v2.1 - Section 5)
- ✅ Conservation des **logs d'audit sur plusieurs années**
- ✅ **Performance** et **scalabilité** optimisées
- ✅ Support des **sauvegardes** et **réplication**

**Conformité** :
> "Le registre off-chain, gérant la hash-chain d'audit et les métadonnées KYC, repose sur une base de données **MariaDB**. Ce choix garantit la persistance à long terme et la conformité avec les exigences de conservation des logs d'audit sur plusieurs années."
> 
> — Livre Blanc FAN-Capital v2.1, Section 5

---

## 🔄 Basculer entre H2 et MariaDB

### Utiliser H2 (Développement - Par Défaut)

Aucune action requise. Le backend utilise H2 par défaut.

### Utiliser MariaDB (Production)

#### 1. Installer MariaDB

**Windows** :
```powershell
# Télécharger depuis https://mariadb.org/download/
# Ou utiliser Chocolatey
choco install mariadb
```

**Linux** :
```bash
sudo apt-get install mariadb-server
sudo systemctl start mariadb
```

#### 2. Créer la Base de Données

```sql
CREATE DATABASE fancapital CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fancapital'@'localhost' IDENTIFIED BY 'votre_mot_de_passe';
GRANT ALL PRIVILEGES ON fancapital.* TO 'fancapital'@'localhost';
FLUSH PRIVILEGES;
```

#### 3. Configurer les Variables d'Environnement

```powershell
# URL de connexion
$env:DB_URL="jdbc:mariadb://127.0.0.1:3306/fancapital"

# Identifiants
$env:DB_USERNAME="fancapital"
$env:DB_PASSWORD="votre_mot_de_passe"

# Activer le profil MariaDB
$env:SPRING_PROFILES_ACTIVE="mariadb"
```

#### 4. Démarrer le Backend avec le Profil MariaDB

```powershell
cd backend
.\mvnw.cmd spring-boot:run -Dspring.profiles.active=mariadb
```

Ou via variable d'environnement :
```powershell
$env:SPRING_PROFILES_ACTIVE="mariadb"
.\mvnw.cmd spring-boot:run
```

---

## 📋 Dépendances Maven

Les deux drivers sont inclus dans `pom.xml` :

```xml
<!-- H2 (Développement) -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- MariaDB (Production) -->
<dependency>
    <groupId>org.mariadb.jdbc</groupId>
    <artifactId>mariadb-java-client</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## 🗄️ Structure des Tables

Les tables sont créées automatiquement par Hibernate (`ddl-auto: update`) :

### Tables d'Audit (Livre Blanc v2.1)
- `audit_log_entries` - Hash-chain d'audit (append-only)
- `audit_checkpoints` - Checkpoints tous les 10 000 blocs
- `audit_token_sync_state` - État de synchronisation
- `audit_user_token_balance` - Soldes utilisateurs
- `audit_alerts` - Alertes de non-conformité
- `business_context_mappings` - Mapping transactions → BusinessContextId

### Tables Utilisateurs
- `app_users` - Utilisateurs et portefeuilles WaaS
- `kyc_documents` - Documents KYC (si applicable)

### Tables Métier
- Tables spécifiques à votre domaine

---

## 🔍 Vérification

### H2 Console

Accédez à : `http://localhost:8081/h2-console`

**Paramètres de connexion** :
- JDBC URL : `jdbc:h2:file:./data/fancapital`
- User Name : `sa`
- Password : (vide)

### MariaDB

```bash
mysql -u fancapital -p fancapital
```

```sql
-- Vérifier les tables
SHOW TABLES;

-- Compter les entrées d'audit
SELECT COUNT(*) FROM audit_log_entries;
SELECT COUNT(*) FROM audit_checkpoints;
SELECT COUNT(*) FROM business_context_mappings;
```

---

## 📊 Comparaison

| Caractéristique | H2 | MariaDB |
|----------------|----|---------|
| **Environnement** | Développement | Production |
| **Installation** | Aucune | Requise |
| **Performance** | Bonne (petit volume) | Excellente (grand volume) |
| **Persistance** | Fichier local | Serveur dédié |
| **Sauvegardes** | Manuelle | Automatisées |
| **Conformité Livre Blanc** | ❌ | ✅ |
| **Conservation long terme** | ❌ | ✅ |

---

## 🚀 Recommandation

- **Développement** : Utilisez **H2** (par défaut, aucune configuration)
- **Production** : Utilisez **MariaDB** (conforme Livre Blanc v2.1)

---

## 📚 Documentation Complémentaire

- **Configuration MariaDB** : `docs/blockchain/MARIADB_CONFIGURATION.md`
- **Livre Blanc v2.1** : `docs/blockchain/Livre Blanc FAN-Capital v2.1 Finale.md`
