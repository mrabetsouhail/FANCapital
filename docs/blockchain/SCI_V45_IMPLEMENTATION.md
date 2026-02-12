# Implémentation SCI v4.5

Implémentation des spécifications du fichier `Spécifications SCI v4.5 et Logique dUpgrade.md`.

## 1. Formule du Score SCI

```
SCI = (AUM_90j × 0.50) + (KYC_depth × 0.20) + (Behavior × 0.15) + (Risk × 0.15)
```

- **AUM (50%)** : Volume des actifs (portfolio + cash). Normalisation sur 1M TND.
- **KYC Depth (20%)** : 10 pts Niveau 1, 20 pts Niveau 2.
- **Behavior (15%)** : Récurrence transactionnelle (nombre de tx / 50).
- **Risk (15%)** : Placeholder 100 (surveillance ratio AST à intégrer).

## 2. Loi du Minimum (Conformité LBA/FT)

```
Level_Effective = min(Level_Score, Level_KYC)
```

- **KYC 1** : Tier max = BRONZE (0), quel que soit le score.
- **KYC 2** : Pas de cap, tier selon le score.

## 3. Composants modifiés / créés

### Backend
- `SciScoreService` : calcul du score et du tier effectif.
- `SciScorePushService` : push score + feeLevel (tier effectif) vers InvestorRegistry.
- `BlockchainController` : GET `/api/blockchain/investor/sci`, POST `/api/blockchain/investor/sci/push`.
- `OnchainBootstrapService` : appelle `sciPush.pushForUser()` après whitelist + mint.

### Blockchain (InvestorRegistry.sol)
- Les gates `canUseCreditModelA`, `canUseReservation`, `canUseCreditModelB` utilisent `getFeeLevel` (tier effectif) au lieu de `getTier` (tier brut).

### Frontend
- `SciNudgeBanner` : bandeau contextuel lorsqu’un utilisateur KYC1 est proche d’un seuil supérieur.
- `BlockchainApiService` : `getSciScore()`, `pushSciScore()`.
- Navbar : affichage du nudge et appel SCI lors du refresh wallet.

## 4. Modèle Freemium

- **Abonnement Premium** : requis pour les services Platinum/Diamond (déjà géré via `isSubscriptionActive`).
- **Frais d’ouverture** : passage au Bronze au premier paiement (à brancher sur l’API de paiement).

## 5. Mise à jour on-chain

- Lors de la validation KYC : `bootstrapUser` → `sciPush.pushForUser`.
- Manuel : POST `/api/blockchain/investor/sci/push?user=0x...`.
- Cadence recommandée (prod) : tous les 10 000 blocs (à implémenter en scheduled job).
