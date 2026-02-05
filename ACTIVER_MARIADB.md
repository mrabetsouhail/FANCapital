# Activer MariaDB dans le Backend

## 🔍 Problème Actuel

D'après les logs, le backend utilise **H2** au lieu de **MariaDB** :

```
url=jdbc:h2:file:./data/fancapital
No active profile set, falling back to 1 default profile: "default"
```

## ✅ Solution : Activer le Profil MariaDB

### Méthode 1 : Script Automatique (Recommandé)

1. **Exécutez le script** :
   ```powershell
   .\configurer-mariadb.ps1
   ```

2. **Entrez votre mot de passe root MariaDB** quand demandé

3. **Démarrez le backend** :
   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

---

### Méthode 2 : Configuration Manuelle

#### Étape 1 : Arrêter le Backend
- Appuyez sur `Ctrl+C` dans la fenêtre où le backend tourne

#### Étape 2 : Configurer les Variables d'Environnement

Dans PowerShell, exécutez :

```powershell
# URL de connexion MariaDB
$env:DB_URL="jdbc:mariadb://127.0.0.1:3306/fancapital"

# Identifiants (utilisez les mêmes que pour phpMyAdmin)
$env:DB_USERNAME="root"
$env:DB_PASSWORD="votre_mot_de_passe_root"

# ⚠️ IMPORTANT : Activer le profil MariaDB
$env:SPRING_PROFILES_ACTIVE="mariadb"
```

**Remplacez** `votre_mot_de_passe_root` par le mot de passe que vous utilisez pour vous connecter à phpMyAdmin.

#### Étape 3 : Redémarrer le Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

---

## ✅ Vérification

### Dans les Logs du Backend

Vous devriez voir :

```
Active profiles: mariadb
url=jdbc:mariadb://127.0.0.1:3306/fancapital
```

Au lieu de :

```
No active profile set
url=jdbc:h2:file:./data/fancapital
```

### Dans phpMyAdmin

1. Ouvrez phpMyAdmin : `http://localhost/phpmyadmin/`
2. Cliquez sur la base `fancapital`
3. Après le démarrage du backend, vous devriez voir toutes les tables créées :
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

### Erreur : "Access denied for user 'root'@'localhost'"

**Solution** : Vérifiez le mot de passe dans `$env:DB_PASSWORD`

### Erreur : "Unknown database 'fancapital'"

**Solution** : 
1. Créez la base dans phpMyAdmin (voir `CREER_BASE_PHPMYADMIN.md`)
2. Ou exécutez le script SQL dans `create-database.sql`

### Erreur : "Communications link failure"

**Solution** :
1. Vérifiez que MariaDB est démarré
2. Vérifiez que le port 3306 est accessible
3. Vérifiez l'URL : `jdbc:mariadb://127.0.0.1:3306/fancapital`

### Le backend utilise toujours H2

**Solution** :
1. Vérifiez que `$env:SPRING_PROFILES_ACTIVE="mariadb"` est bien défini
2. Redémarrez complètement le backend (fermez et rouvrez le terminal)
3. Vérifiez dans les logs que le profil est actif

---

## 📝 Configuration Permanente (Optionnel)

Pour éviter de reconfigurer à chaque fois, vous pouvez créer un fichier `.env` ou modifier directement `application.yml`, mais **ce n'est pas recommandé** pour la sécurité (mots de passe en clair).

**Meilleure pratique** : Utilisez toujours les variables d'environnement.

---

## 🔐 Sécurité

⚠️ **Important** : Ne commitez jamais les mots de passe dans le code source !

- Utilisez toujours les variables d'environnement
- En production, utilisez un gestionnaire de secrets (Azure Key Vault, AWS Secrets Manager, etc.)

---

## ✨ Résumé Rapide

1. ✅ Créer la base `fancapital` dans phpMyAdmin
2. ✅ Configurer les variables d'environnement (DB_URL, DB_USERNAME, DB_PASSWORD, SPRING_PROFILES_ACTIVE)
3. ✅ Redémarrer le backend
4. ✅ Vérifier dans les logs que MariaDB est utilisé
5. ✅ Vérifier dans phpMyAdmin que les tables sont créées
