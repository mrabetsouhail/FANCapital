# Création de la Base de Données FAN-Capital

## Méthode 1 : Via phpMyAdmin (Recommandé)

### Étapes :

1. **Ouvrir phpMyAdmin** : `http://localhost/phpmyadmin/`

2. **Cliquer sur l'onglet "SQL"** en haut de la page

3. **Copier-coller cette commande SQL** :

```sql
CREATE DATABASE IF NOT EXISTS fancapital 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;
```

4. **Cliquer sur "Exécuter"**

5. **Vérifier** : La base `fancapital` devrait apparaître dans la liste à gauche

---

## Méthode 2 : Via l'Interface phpMyAdmin (Graphique)

1. **Cliquer sur "Nouvelle base de données"** dans le menu de gauche

2. **Remplir le formulaire** :
   - **Nom de la base de données** : `fancapital`
   - **Interclassement** : `utf8mb4_unicode_ci` (déjà sélectionné par défaut)

3. **Cliquer sur "Créer"**

---

## Méthode 3 : Via la Ligne de Commande

### Windows (PowerShell ou CMD)

```powershell
# Se connecter à MariaDB
mysql -u root -p

# Entrer votre mot de passe root
# Puis exécuter :
```

```sql
CREATE DATABASE IF NOT EXISTS fancapital 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

-- Vérifier
SHOW DATABASES;

-- Utiliser la base
USE fancapital;

-- Quitter
EXIT;
```

---

## Vérification

### Dans phpMyAdmin :

1. La base `fancapital` doit apparaître dans la liste à gauche
2. Cliquez dessus pour voir qu'elle est vide (c'est normal, les tables seront créées automatiquement par Hibernate)

### Via la ligne de commande :

```sql
SHOW DATABASES LIKE 'fancapital';
```

---

## Configuration du Backend

Une fois la base créée, configurez le backend pour l'utiliser :

### 1. Variables d'Environnement

```powershell
# URL de connexion
$env:DB_URL="jdbc:mariadb://127.0.0.1:3306/fancapital"

# Identifiants (utilisez root ou créez un utilisateur dédié)
$env:DB_USERNAME="root"
$env:DB_PASSWORD="votre_mot_de_passe_root"

# Activer le profil MariaDB
$env:SPRING_PROFILES_ACTIVE="mariadb"
```

### 2. Redémarrer le Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### 3. Vérifier la Connexion

Le backend va automatiquement :
- Se connecter à la base `fancapital`
- Créer toutes les tables nécessaires (via Hibernate `ddl-auto: update`)
- Initialiser les tables d'audit (checkpoints, business context, etc.)

---

## Tables qui seront Créées Automatiquement

Une fois le backend démarré avec MariaDB, ces tables seront créées automatiquement :

### Tables d'Audit (Livre Blanc v2.1)
- `audit_log_entries`
- `audit_checkpoints`
- `audit_token_sync_state`
- `audit_user_token_balance`
- `audit_alerts`
- `business_context_mappings`

### Tables Utilisateurs
- `app_users`
- `kyc_documents` (si applicable)

### Tables Métier
- Tables spécifiques à votre domaine

---

## Dépannage

### Erreur : "Access denied"

Vérifiez que l'utilisateur a les droits :
```sql
GRANT ALL PRIVILEGES ON fancapital.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
```

### Erreur : "Database already exists"

La base existe déjà, c'est normal. Vous pouvez la supprimer et la recréer si nécessaire :
```sql
DROP DATABASE IF EXISTS fancapital;
CREATE DATABASE fancapital CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Le backend ne se connecte pas

1. Vérifiez que MariaDB est démarré
2. Vérifiez les variables d'environnement
3. Vérifiez que le profil `mariadb` est activé
4. Consultez les logs du backend pour plus de détails

---

## Script SQL Complet

Un fichier `create-database.sql` est disponible à la racine du projet avec le script complet.
