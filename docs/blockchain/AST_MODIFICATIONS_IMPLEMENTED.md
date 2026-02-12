# Modifications AST (Avance sur Titres) - Implémentées

## 2.1 Levier et Calculs

| Modification | Status |
|--------------|--------|
| LTV 50% → 70% (MAX_LTV_EMISSION) | ✅ CreditModelA/B, frontend |
| Retenue 5% sur montant brut | ✅ Déjà implémenté |
| Credit Wallet strict, pas de Cash-Out | ✅ Circuit fermé (existant) |

## 2.2 Gestion Temporelle

| Modification | Status |
|--------------|--------|
| Intérêts = Taux fixe du tier (pas durée) | ✅ TIER_RATES utilisés |
| Calendrier de collecte / scheduler | ⏳ Backend à implémenter |
| Auto-remboursement par coupons | ⏳ DividendService ↔ DebtManager à lier |

## 3. Smart Contract

| Modification | Status |
|--------------|--------|
| Level_Effective = min(Level_Score, Level_KYC) | ✅ InvestorRegistry (feeLevel) |
| isLocked = true pour collatéral | ✅ EscrowRegistry + CPEFToken.setEscrowLocked |
| Libération au prorata | ⏳ Nécessite CPEFToken: lockedAmount (uint) au lieu de isLocked (bool) |

**Formule prorata** (à implémenter après changement CPEFToken) :
```
Tokens_Libérés = (Montant_Remboursé / Dette_Totale) × Tokens_Séquestrés
```

## 4. Interface Angular

| Modification | Status |
|--------------|--------|
| LTV Variable (Dette / Valeur collatéral) | ✅ Dashboard Crédit |
| Barre progression "Jours restants: X/Y" | ✅ Par tier (Silver 90j, Gold 120j, etc.) |
| Nudge ±2 pts seuil | ✅ SciNudgeBanner |
| Incitation KYC 2 | ✅ SciNudgeBanner |

## 5. Freemium

| Modification | Status |
|--------------|--------|
| isPremium dans AppUser | ✅ Champ ajouté |
| Frais d'ouverture → Trigger_Wallet_Creation | ⏳ À brancher sur API paiement |
| Premium requis AST / Platinum-Diamond | ✅ InvestorRegistry.canUseCreditModelA (sub required) |
