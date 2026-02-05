# Configurer MariaDB dans IntelliJ IDEA

## 🔍 Problème

Quand vous lancez le backend depuis IntelliJ IDEA, il utilise H2 par défaut au lieu de MariaDB.

## ✅ Solution : Configurer les Variables d'Environnement dans IntelliJ

### Méthode 1 : Configuration de Run (Recommandé)

#### Étape 1 : Ouvrir la Configuration de Run

1. En haut à droite d'IntelliJ, cliquez sur la configuration de run (probablement "BackendApplication")
2. Cliquez sur **"Edit Configurations..."** (ou `Edit Configurations...`)

#### Étape 2 : Ajouter les Variables d'Environnement

1. Dans la fenêtre qui s'ouvre, trouvez la section **"Environment variables"**
2. Cliquez sur le bouton **"..."** à droite de "Environment variables"
3. Cliquez sur **"+"** pour ajouter de nouvelles variables
4. Ajoutez ces 4 variables :

| Name | Value |
|------|-------|
| `DB_URL` | `jdbc:mariadb://127.0.0.1:3306/fancapital` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | `votre_mot_de_passe_root` |
| `SPRING_PROFILES_ACTIVE` | `mariadb` |

**Important** : Remplacez `votre_mot_de_passe_root` par le mot de passe que vous utilisez pour vous connecter à phpMyAdmin.

#### Étape 3 : Appliquer et Redémarrer

1. Cliquez sur **"OK"** pour fermer la fenêtre des variables d'environnement
2. Cliquez sur **"Apply"** puis **"OK"** pour fermer la configuration
3. **Arrêtez** le backend s'il est en cours d'exécution (bouton Stop)
4. **Redémarrez** le backend (bouton Run)

---

### Méthode 2 : Via application.properties (Alternative)

Si vous préférez, vous pouvez créer un fichier `application.properties` dans `backend/src/main/resources/` :

```properties
spring.profiles.active=mariadb
spring.datasource.url=jdbc:mariadb://127.0.0.1:3306/fancapital
spring.datasource.username=root
spring.datasource.password=votre_mot_de_passe_root
```

⚠️ **Attention** : Cette méthode stocke le mot de passe en clair dans le fichier. Ce n'est pas recommandé pour la production.

---

## ✅ Vérification

### Dans les Logs d'IntelliJ

Après redémarrage, vous devriez voir :

```
Active profiles: mariadb
url=jdbc:mariadb://127.0.0.1:3306/fancapital
```

Au lieu de :

```
No active profile set, falling back to 1 default profile: "default"
url=jdbc:h2:file:./data/fancapital
```

### Dans phpMyAdmin

1. Ouvrez phpMyAdmin : `http://localhost/phpmyadmin/`
2. Cliquez sur la base `fancapital`
3. Après le démarrage du backend, vous devriez voir toutes les tables créées automatiquement

---

## 📸 Guide Visuel (IntelliJ IDEA)

### Étape 1 : Configuration de Run
```
IntelliJ IDEA
  └─ En haut à droite : [BackendApplication ▼]
     └─ Cliquez sur la flèche ▼
        └─ "Edit Configurations..."
```

### Étape 2 : Variables d'Environnement
```
Edit Configurations
  └─ Environment variables: [  ...  ]  ← Cliquez sur "..."
     └─ Environment variables dialog
        └─ Cliquez sur "+" pour ajouter :
           ├─ DB_URL = jdbc:mariadb://127.0.0.1:3306/fancapital
           ├─ DB_USERNAME = root
           ├─ DB_PASSWORD = votre_mot_de_passe
           └─ SPRING_PROFILES_ACTIVE = mariadb
```

---

## 🐛 Dépannage

### Erreur : "Access denied for user 'root'@'localhost'"

**Solution** : Vérifiez que le mot de passe dans `DB_PASSWORD` est correct

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
1. Vérifiez que `SPRING_PROFILES_ACTIVE=mariadb` est bien défini dans les variables d'environnement
2. Vérifiez que vous avez bien cliqué sur "Apply" et "OK"
3. **Redémarrez complètement** le backend (Stop puis Run)
4. Vérifiez dans les logs que le profil est actif

---

## 🔐 Sécurité

⚠️ **Important** : 
- Ne commitez jamais les mots de passe dans le code source
- Utilisez les variables d'environnement plutôt que les fichiers de configuration
- En production, utilisez un gestionnaire de secrets

---

## ✨ Résumé Rapide

1. ✅ Créer la base `fancapital` dans phpMyAdmin
2. ✅ IntelliJ → Edit Configurations → Environment variables
3. ✅ Ajouter : `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE=mariadb`
4. ✅ Apply → OK → Redémarrer le backend
5. ✅ Vérifier dans les logs que MariaDB est utilisé
6. ✅ Vérifier dans phpMyAdmin que les tables sont créées
