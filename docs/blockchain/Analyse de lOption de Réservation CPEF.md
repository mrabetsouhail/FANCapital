## Note d'Ingénierie Financière

## Mécanisme d'Option de Réservation sur Stock CPEF

Département R&D FAN-Capital

Janvier 2026

## 1. Modèle de fonctionnement

L'utilisateur souhaite **bloquer un prix d'achat** pour \(Q\) jetons CPEF.

- **Instant \(t_0\) (Réservation)** : l'utilisateur paie une prime \(A\) (avance).
- **Prix d'exercice \(K\)** : fixé à la VNI à l'instant \(t_0\).
- **Échéance \(T\)** : date limite pour compléter l'achat.

## 2. Formalisation mathématique

### 2.1 Valeur de l'avance

L'avance \(A\) est calculée comme un pourcentage du notionnel total, majoré d'une prime de risque liée à la volatilité :

\[
A = (K \times Q) \times \left(r_{\text{base}} + \sigma_{\text{adj}}\right) \tag{1}
\]

Où \(r_{\text{base}}\) est le taux de réservation (ex : 5%) et \(\sigma_{\text{adj}}\) un ajustement selon la volatilité du panier sous-jacent.

### 2.2 Scénarios à l'échéance \(T\)

Soit \(VNI_T\) la valeur du jeton à l'échéance :

1. Si \(VNI_T > K\) (profit pour l'utilisateur) : l'utilisateur paie le reliquat \(R = (K \times Q) - A\). Il réalise un gain immédiat de \((VNI_T - K) \times Q\).
2. Si \(VNI_T \leq K\) (annulation) : l'utilisateur peut choisir de ne pas exercer. La plateforme conserve \(A\). Les jetons réservés sont libérés pour la piscine de liquidité.

## 3. Impacts et avantages

### Analyse stratégique

- **Pour l'utilisateur** : effet de levier important. Avec une avance de 100 TND, il contrôle 1000 TND d'actifs. Risque limité à la perte de l'avance.
- **Pour la plateforme** : génération de revenus récurrents (primes collectées). Optimisation de la gestion des stocks.
- **Risque** : la plateforme doit s'assurer de détenir les actifs réels chez l'IB dès la réservation pour éviter tout risque de défaut de livraison (squeezing).

## 4. Logique smart contract (pseudo-code)

Le contrat doit gérer un `mapping` de réservations.

- `struct Reservation { uint256 qty; uint256 strikePrice; uint256 expiry; uint256 advancePaid; address user; bool exercised; bool cancelled; }`

- **Validation** : vérification que le stock (inventaire) de la plateforme est suffisant, puis **réservation** de \(Q\) jetons.
- **Clôture** : si `block.timestamp > expiry`, une fonction `purge()` transfère l'avance vers `TreasuryVault` puis libère les jetons réservés.

### Notes d'implémentation

- **Source de \(K\)** : `K = VNI(t0)` via `PriceOracle`.
- **Paiement de \(A\)** : débité du `Cash Wallet` (recommandé) ou d'un module de paiement TND off-chain (backend) avant l'appel on-chain.
- **Volatilité \(\sigma_{\text{adj}}\)** : fournie par l'oracle (si calculable on-chain) ou par le backend (si calcul off-chain), mais **auditée** (event + traces).
