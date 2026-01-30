# Keepers (Automation Off-Chain)

Ces scripts rendent le système **autonome** en appelant les smart contracts de façon proactive.

## Pré-requis (local)

Lancer une blockchain locale persistante, déployer, puis exécuter les keepers sur `localhost`.

```bash
npm run node
```

Dans un autre terminal :

```bash
npm run deploy:localhost
```

Cela génère un fichier `deployments/localhost.json` utilisé par les keepers.

## 1) Price-Bot (VNI + Volatilité)

Met à jour la VNI (guard ±10%) et la volatilité (bps) dans `PriceOracle`.

```bash
# Dev key (Hardhat node account #0)
set PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
npx ts-node keepers/price-bot.ts --config keepers/price-bot.example.json
```

- Par défaut: `updateVNI()` (guard 10%).
- En cas de variation réelle >10%: mettez `"force": true` pour appeler `forceUpdateVNI()`.

## 2) Circuit-Breaker Watchdog (réserve)

Surveille le ratio de réserve et appelle `LiquidityPool.checkAndTripRedemptions()` si le ratio passe sous le threshold.

```bash
npx ts-node keepers/circuit-breaker-watchdog.ts --config keepers/circuit-breaker-watchdog.example.json
```

## 3) Subscription-Manager

Met à jour `InvestorRegistry.subscriptionActive` depuis une vérité off-chain (billing).

```bash
npx ts-node keepers/subscription-manager.ts --config keepers/subscription-manager.example.json
```

Le fichier de config peut fournir :

- `active`: vrai/faux (force)
- ou `expiresAt`: timestamp UNIX (sec), le bot calcule `active = now <= expiresAt`.

## Notes importantes

- Sur un réseau Besu/Quorum, utilisez `--rpc` (ou `RPC_URL`) + `PRIVATE_KEY` d’un compte autorisé.
- Les fonctions `updateVNI/updateVolatilityBps` exigent un compte ayant `ORACLE_ROLE`.
- `forceUpdateVNI` exige `GOVERNANCE_ROLE`.

