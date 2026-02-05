# Configuration : clé de chiffrement des wallets (WALLET_ENC_KEY)

## Quand le wallet est créé

- **À l’inscription** : aucun wallet n’est créé. L’utilisateur peut s’inscrire sans que `WALLET_ENC_KEY` soit configuré.
- **Après validation KYC1** : le wallet est créé **automatiquement** lorsque l’admin valide l’utilisateur au niveau KYC1 (ou supérieur) dans le backoffice. La validation KYC est manuelle par l’admin jusqu’à l’intégration future de l’API.

Donc **WALLET_ENC_KEY** n’est nécessaire que si vous utilisez la validation KYC et que vous validez des utilisateurs au niveau KYC1 (création de wallet). Sans cette clé, l’admin pourra toujours mettre le niveau KYC à 1, mais la création du wallet échouera avec « WALLET_ENC_KEY not configured ».

## Configurer WALLET_ENC_KEY (pour la création de wallet après KYC1)

1. Générer une clé AES-256 (32 octets) en base64, par exemple :
   ```powershell
   [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
   ```
2. Définir la variable d’environnement avant de lancer le backend :
   ```powershell
   $env:WALLET_ENC_KEY = "<la_clé_base64_générée>"
   ```
   Ou dans `application.yml` (à ne pas committer en production) :
   ```yaml
   app:
     wallet:
       enc-key: ${WALLET_ENC_KEY:}
   ```

## Résumé

| Moment              | Wallet créé ? | WALLET_ENC_KEY requis ? |
|---------------------|---------------|--------------------------|
| Inscription         | Non           | Non                      |
| Connexion (login)   | Non           | Non                      |
| Admin valide KYC1   | Oui           | Oui                      |
