# P2P Hybrid Order Book - Spécifications

## Vue d'ensemble

Le modèle P2P Hybrid-Order-Book combine le marché de gré à gré avec la liquidité automatique de la plateforme. L'investisseur définit une **période de validité** pour son ordre ; à l'expiration, le reliquat non matché est automatiquement exécuté via la piscine de liquidité.

## Flux d'exécution

1. **Dépôt** : L'ordre est publié sur l'Order Book P2P avec une période (ex: 24h, 48h, 7j).
2. **Phase de matching** : Le moteur tente de matcher avec les ordres opposés (matching partiel possible).
3. **À l'expiration** : Si `heure > T_expiration` :
   - **0% matché** : Transfert total vers la piscine.
   - **X% matché** : X% exécuté en P2P ; (100-X)% vers la piscine.
4. **Finalisation** : Mise à jour SCI (Pilier Behavior), notification utilisateur.

## Conditions d'accès

| Critère | Valeur |
|---------|--------|
| Tier minimum | Silver (Score 16-35, tier ≥ 1) |
| KYC | Niveau 2 (Domicile) obligatoire |
| Spread fallback | Différent du P2P — notifier l'utilisateur à la création |

## Composants implémentés

### 1. Smart Contract — `OrderFallbackExecutor.sol`

- Fonction `executeFallbackToPool(token, user, amount, isBuy, orderId)`
- Délègue à `LiquidityPool.buyFor` ou `sellFor`
- Émet `FallbackToPoolExecuted` pour audit

### 2. Backend — Spring Boot

- **OrderScheduler** : `@Scheduled` vérifie les ordres PENDING expirés, déclenche le fallback.
- **Order étendu** : `filledTokenAmount` pour le matching partiel.
- **OrderBookService** : Vérification Tier Silver + KYC2 avant soumission.
- **Spread notification** : Retourne le spread pool estimé dans la réponse.

### 3. Frontend — Angular

- Sélecteur de période : 24h, 48h, 7 jours (ou date/heure personnalisée).
- Affichage du avertissement de spread si fallback possible.

### 4. API Matching Probability

- `GET /api/blockchain/p2p/matching-probability?token=&periodHours=24`
- Heuristique basée sur le nombre d'ordres ouverts (buy + sell) pour le token.
- Retourne `probabilityLabel` (Élevée/Moyenne/Faible), `message`, `openOrdersOpposite`.

### 5. Déploiement

- **OrderFallbackExecutor** : déployé avec `deploy.ts`, reçoit OPERATOR_ROLE sur le LiquidityPool.
- Le backend utilise `blockchain.operator-private-key` pour appeler `executeFallbackToPool`.
- En production : accorder `OPERATOR_ROLE` sur OrderFallbackExecutor à l'adresse du backend operator.
