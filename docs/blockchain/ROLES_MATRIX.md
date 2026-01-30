## Matrice des rôles (Gouvernance)

Cette matrice formalise les **3 niveaux** de pouvoir pour le MVP :

- **DEFAULT_ADMIN_ROLE (“Conseil”)** : multi‑sig (ex: 3/5). Peut nommer/révoquer les autres rôles.
- **OPERATOR_ROLE (“Exécutif”)** : keepers + backend (opérations courantes).
- **GOVERNANCE_ROLE (“Urgence”)** : actions sensibles (circuit breaker, force oracle, etc.).

### Conventions

- **Council** : adresse du multi‑sig.
- **Backend/Keepers** : adresses EOA/Services (clé privée).
- **Bank** : adresse(s) autorisées à émettre/brûler TND.
- **Fisc** : adresse officielle recevant les retraits du `TaxVault`.

### Contrats & rôles

#### `PriceOracle`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council (peut `forceUpdateVNI`)
- **ORACLE_ROLE** : Price‑Bot (keeper)

#### `LiquidityPool`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council (paramètres, wiring)
- **OPERATOR_ROLE** : Backend (buyFor/sellFor si besoin)

#### `CircuitBreaker`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council (pause/reprise, threshold)

#### `KYCRegistry`
- **DEFAULT_ADMIN_ROLE** : Council
- **KYC_VALIDATOR_ROLE** : Validateurs KYC (backend)

#### `InvestorRegistry`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council
- **OPERATOR_ROLE** : Subscription‑Manager / backend (score, feeLevel, subscription)

#### `CashTokenTND`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council
- **MINTER_ROLE** : Bank (mint)
- **BURNER_ROLE** : Bank (burn)

#### `TaxVault`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Urgence/Trésorerie (withdraw)
- **fiscAddress** : adresse officielle (configurée par Council)
- **isAuthorizedCaller** : pools autorisés à `recordRAS`

#### `P2PExchange`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council (paramètres, treasury)

#### `ReservationOption`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council (wiring)
- **OPERATOR_ROLE** : (réservé si on automatise des actions côté backend)

#### `CreditModelA` / `CreditModelBPGP`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council (paramètres, wiring)
- **OPERATOR_ROLE** : Backend crédit (activate/close/cancel)

#### `EscrowRegistry`
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council
- **isAuthorizedCaller** : contrats crédit autorisés

#### `CPEFToken` (CPEF)
- **DEFAULT_ADMIN_ROLE** : Council
- **GOVERNANCE_ROLE** : Council (wiring: pool/oracle/kyc, escrowManager)
- **liquidityPool** : adresse du `LiquidityPool` dédié (mint/burn)

