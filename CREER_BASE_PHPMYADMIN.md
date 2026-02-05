# Créer la Base de Données via phpMyAdmin - Guide Pas à Pas

## 📋 Méthode 1 : Via l'Onglet SQL (Plus Rapide)

### Étape 1 : Ouvrir phpMyAdmin
- Accédez à : `http://localhost/phpmyadmin/`
- Connectez-vous avec vos identifiants (généralement `root`)

### Étape 2 : Cliquer sur l'Onglet "SQL"
- En haut de la page phpMyAdmin, vous verrez plusieurs onglets
- Cliquez sur l'onglet **"SQL"**

### Étape 3 : Copier-Coller le Script SQL
- Dans la zone de texte SQL, copiez-collez exactement ceci :

```sql
CREATE DATABASE IF NOT EXISTS fancapital 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;
```

### Étape 4 : Exécuter
- Cliquez sur le bouton **"Exécuter"** (ou appuyez sur `Ctrl+Enter`)
- Vous devriez voir un message de succès : "Requête SQL exécutée avec succès"

### Étape 5 : Vérifier
- Dans le menu de gauche, la base `fancapital` devrait maintenant apparaître
- Cliquez dessus pour voir qu'elle est vide (c'est normal, les tables seront créées par le backend)

---

## 📋 Méthode 2 : Via l'Interface Graphique

### Étape 1 : Cliquer sur "Nouvelle base de données"
- Dans le menu de gauche de phpMyAdmin
- Cliquez sur **"Nouvelle base de données"** (ou "New database" en anglais)

### Étape 2 : Remplir le Formulaire
- **Nom de la base de données** : `fancapital`
- **Interclassement** : Sélectionnez `utf8mb4_unicode_ci` dans le menu déroulant
  - (C'est généralement déjà sélectionné par défaut)

### Étape 3 : Créer
- Cliquez sur le bouton **"Créer"** (ou "Create")
- La base de données sera créée immédiatement

### Étape 4 : Vérifier
- La base `fancapital` apparaît dans la liste à gauche
- Cliquez dessus pour l'ouvrir (elle sera vide, c'est normal)

---

## ✅ Vérification de la Création

### Dans phpMyAdmin :
1. La base `fancapital` doit apparaître dans la liste à gauche
2. Cliquez dessus pour voir qu'elle est vide (aucune table)
3. C'est normal ! Les tables seront créées automatiquement par le backend Spring Boot

### Test Rapide :
- Cliquez sur la base `fancapital`
- Vous devriez voir : "Aucune table trouvée dans la base de données"

---

## 🔧 Configuration du Backend

Une fois la base créée, configurez le backend :

### 1. Ouvrir PowerShell dans le dossier du projet

### 2. Configurer les Variables d'Environnement

```powershell
# URL de connexion MariaDB
$env:DB_URL="jdbc:mariadb://127.0.0.1:3306/fancapital"

# Identifiants (utilisez les mêmes que pour phpMyAdmin)
$env:DB_USERNAME="root"
$env:DB_PASSWORD="votre_mot_de_passe_root"

# Activer le profil MariaDB
$env:SPRING_PROFILES_ACTIVE="mariadb"
```

**Note** : Remplacez `votre_mot_de_passe_root` par le mot de passe que vous utilisez pour vous connecter à phpMyAdmin.

### 3. Redémarrer le Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### 4. Vérifier les Tables Créées

Après le démarrage du backend, retournez dans phpMyAdmin :
1. Cliquez sur la base `fancapital` à gauche
2. Vous devriez maintenant voir toutes les tables créées automatiquement :
   - `app_users`
   - `audit_log_entries`
   - `audit_checkpoints`
   - `audit_token_sync_state`
   - `audit_user_token_balance`
   - `audit_alerts`
   - `business_context_mappings`
   - Et d'autres...

---

## 🐛 Dépannage

### Erreur : "Access denied"
- Vérifiez que vous êtes connecté avec un utilisateur ayant les droits (généralement `root`)
- Vérifiez le mot de passe dans les variables d'environnement

### Erreur : "Database already exists"
- La base existe déjà, c'est normal
- Vous pouvez l'utiliser directement ou la supprimer et la recréer si nécessaire

### Le backend ne se connecte pas
1. Vérifiez que MariaDB est démarré
2. Vérifiez les variables d'environnement (DB_URL, DB_USERNAME, DB_PASSWORD)
3. Vérifiez que le profil `mariadb` est activé
4. Consultez les logs du backend pour plus de détails

---

## 📝 Script SQL Complet

Si vous préférez, vous pouvez aussi utiliser le fichier `create-database.sql` à la racine du projet :
1. Ouvrez phpMyAdmin
2. Cliquez sur "SQL"
3. Cliquez sur "Importer" ou copiez le contenu du fichier
4. Exécutez

---

## ✨ Résumé Rapide

1. **phpMyAdmin** → Onglet **"SQL"**
2. **Copier** : `CREATE DATABASE IF NOT EXISTS fancapital CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
3. **Exécuter**
4. **Vérifier** : La base apparaît à gauche
5. **Configurer** le backend avec les variables d'environnement
6. **Redémarrer** le backend
7. **Vérifier** : Les tables sont créées automatiquement
