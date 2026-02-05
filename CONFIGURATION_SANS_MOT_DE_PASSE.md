# Configuration MariaDB sans Mot de Passe

## ✅ Configuration pour MariaDB sans Mot de Passe

Si votre MariaDB est configuré **sans mot de passe** pour l'utilisateur `root` (c'est courant en développement local), voici comment configurer :

### Dans IntelliJ IDEA - Variables d'Environnement

Dans la fenêtre "Environment Variables" d'IntelliJ, configurez :

| Name | Value |
|------|-------|
| `DB_URL` | `jdbc:mariadb://127.0.0.1:3306/fancapital` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | *(laissez vide ou supprimez cette variable)* |

**OU** ne définissez simplement pas `DB_PASSWORD` du tout.

### Dans application-mariadb.yml

Le fichier de configuration utilise déjà une valeur par défaut vide :

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD:}
```

Cela signifie : "Utilise la variable d'environnement `DB_PASSWORD`, ou une chaîne vide si elle n'existe pas".

---

## 🔧 Configuration Complète dans IntelliJ

### 1. Active profiles
```
mariadb
```

### 2. Environment variables
- `DB_URL` = `jdbc:mariadb://127.0.0.1:3306/fancapital`
- `DB_USERNAME` = `root`
- *(Pas de `DB_PASSWORD` ou laissez vide)*

### 3. Apply → OK → Redémarrer

---

## ✅ Vérification

Après redémarrage, dans les logs vous devriez voir :

```
Active profiles: mariadb
url=jdbc:mariadb://127.0.0.1:3306/fancapital
```

Et **pas d'erreur** de connexion.

---

## 🐛 Si vous avez une Erreur de Connexion

Si vous voyez une erreur comme "Access denied", cela signifie que MariaDB nécessite un mot de passe. Dans ce cas :

### Option 1 : Définir un Mot de Passe pour root

```sql
-- Dans phpMyAdmin, onglet SQL
ALTER USER 'root'@'localhost' IDENTIFIED BY 'nouveau_mot_de_passe';
FLUSH PRIVILEGES;
```

Puis configurez `DB_PASSWORD` avec ce mot de passe.

### Option 2 : Créer un Utilisateur sans Mot de Passe

```sql
-- Dans phpMyAdmin, onglet SQL
CREATE USER 'fancapital'@'localhost' IDENTIFIED BY '';
GRANT ALL PRIVILEGES ON fancapital.* TO 'fancapital'@'localhost';
FLUSH PRIVILEGES;
```

Puis utilisez :
- `DB_USERNAME` = `fancapital`
- `DB_PASSWORD` = *(vide ou non défini)*

---

## ✨ Résumé

1. ✅ **Active profiles** : `mariadb`
2. ✅ **DB_URL** : `jdbc:mariadb://127.0.0.1:3306/fancapital`
3. ✅ **DB_USERNAME** : `root`
4. ✅ **DB_PASSWORD** : *(vide ou non défini)*
5. ✅ **Apply → OK → Redémarrer**

Si votre MariaDB accepte les connexions sans mot de passe, cela devrait fonctionner directement !
