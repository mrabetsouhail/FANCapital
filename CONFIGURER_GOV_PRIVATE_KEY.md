# Configuration de GOV_PRIVATE_KEY

## Problème

Le dashboard fiscal affiche l'erreur :
```
GOV_PRIVATE_KEY not configured (backoffice.tax.governance-private-key).
```

Cette clé privée est nécessaire pour signer les transactions `withdrawToFisc` sur le contrat `TaxVault`.

## Solution

Pour le développement local, vous pouvez utiliser la **même clé privée que `OPERATOR_PRIVATE_KEY`** car le déployeur a automatiquement le `GOVERNANCE_ROLE` sur le `TaxVault` (voir `TaxVault.sol` ligne 23).

## Configuration

### Option 1 : IntelliJ IDEA (Recommandé)

1. Ouvrez **Run > Edit Configurations...**
2. Sélectionnez votre configuration de run (ex: `BackendApplication`)
3. Dans **Environment variables**, ajoutez :
   ```
   GOV_PRIVATE_KEY=<même-valeur-que-OPERATOR_PRIVATE_KEY>
   ```
4. Cliquez sur **Apply** puis **OK**

### Option 2 : PowerShell (Avant de lancer le backend)

```powershell
# Utiliser la même clé que OPERATOR_PRIVATE_KEY
$env:GOV_PRIVATE_KEY=$env:OPERATOR_PRIVATE_KEY

# Ou définir explicitement (exemple avec la clé Hardhat #0 par défaut)
$env:GOV_PRIVATE_KEY='0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80'
```

### Option 3 : Fichier `.env` (si supporté)

Créez un fichier `.env` à la racine du projet `backend/` :
```
GOV_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
```

## Vérification

Après configuration, redémarrez le backend et vérifiez que l'erreur a disparu dans le dashboard fiscal.

## Note de Sécurité

⚠️ **En production**, utilisez une clé privée différente et sécurisée pour `GOV_PRIVATE_KEY`, stockée dans un gestionnaire de secrets (ex: AWS Secrets Manager, HashiCorp Vault).

La clé doit correspondre à une adresse qui a le `GOVERNANCE_ROLE` sur le `TaxVault`.
