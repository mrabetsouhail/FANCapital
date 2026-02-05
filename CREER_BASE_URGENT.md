# ⚠️ URGENT : Créer la Base de Données

## Erreur Actuelle

```
Unknown database 'fancapital'
```

Le backend essaie de se connecter à MariaDB mais la base de données n'existe pas encore.

## ✅ Solution Rapide (2 minutes)

### Via phpMyAdmin

1. **Ouvrez phpMyAdmin** : `http://localhost/phpmyadmin/`

2. **Cliquez sur l'onglet "SQL"** en haut de la page

3. **Copiez-collez cette commande** :

```sql
CREATE DATABASE IF NOT EXISTS fancapital 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;
```

4. **Cliquez sur "Exécuter"**

5. **Vérifiez** : La base `fancapital` doit apparaître dans le menu de gauche

6. **Redémarrez le backend** dans IntelliJ (Stop puis Run)

---

## ✅ Vérification

### Dans phpMyAdmin
- La base `fancapital` apparaît dans la liste à gauche
- Cliquez dessus pour voir qu'elle est vide (c'est normal)

### Dans les Logs du Backend
Après redémarrage, vous devriez voir :
```
Active profiles: mariadb
url=jdbc:mariadb://127.0.0.1:3306/fancapital
```

Et **PAS** d'erreur "Unknown database".

Les tables seront créées automatiquement par Hibernate au démarrage.

---

## 📝 Script SQL

Le script complet est disponible dans `create-database.sql` à la racine du projet.

---

## 🎯 Résumé

1. ✅ phpMyAdmin → Onglet SQL
2. ✅ Exécuter : `CREATE DATABASE IF NOT EXISTS fancapital CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
3. ✅ Redémarrer le backend
4. ✅ Vérifier que ça fonctionne
