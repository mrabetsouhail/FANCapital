import { Component, signal, computed, OnInit, effect, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import { ConfirmOrderModal, type ConfirmOrderSummary } from '../confirm-order-modal/confirm-order-modal';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import { CreditWalletTrackingService } from '../../../auth/services/credit-wallet-tracking.service';
import type { Fund } from '../../../blockchain/models/fund.models';
import type { QuoteBuyResponse, QuoteSellResponse } from '../../../blockchain/models/pricing.models';

type OrderMode = 'piscine' | 'p2p';
type OrderType = 'buy' | 'sell';
type TokenType = 'Atlas' | 'Didon';
type AmountType = 'tnd' | 'tokens';
type ValidityPeriod = '24h' | '48h' | '7d';

const VALIDITY_SECONDS: Record<ValidityPeriod, number> = {
  '24h': 24 * 3600,
  '48h': 48 * 3600,
  '7d': 7 * 24 * 3600,
};

@Component({
  selector: 'app-passer-ordre-page',
  imports: [CommonModule, FormsModule, NavbarClient, BackButton, ConfirmOrderModal],
  templateUrl: './passer-ordre-page.html',
  styleUrl: './passer-ordre-page.css',
})
export class PasserOrdrePage implements OnInit {
  private readonly api = inject(BlockchainApiService);
  private readonly authApi = inject(AuthApiService);
  private readonly creditTracking = inject(CreditWalletTrackingService);
  @ViewChild(NavbarClient) navbarClient?: NavbarClient;

  tokenType = signal<TokenType>('Atlas');
  orderType = signal<OrderType>('buy');
  
  orderMode = signal<OrderMode>('piscine');

  /** Période de validité pour P2P Hybrid Order Book (24h, 48h, 7j). */
  validityPeriod = signal<ValidityPeriod>('24h');

  /** Options pour le sélecteur de période (exposé au template). */
  validityPeriodOptions: ValidityPeriod[] = ['24h', '48h', '7d'];

  amountType = signal<AmountType>('tnd');
  amount = signal<number>(0);
  
  // Funds mapping: Alpha/Beta (legacy UI) -> Atlas/Didon (blockchain)
  fundAlpha = signal<Fund | null>(null);
  fundBeta = signal<Fund | null>(null);

  // Oracle/VNI (display, 1 token price in TND)
  alphaPrice = signal<number>(0);
  betaPrice = signal<number>(0);

  // Pool quotes (piscine)
  lastQuoteBuy = signal<QuoteBuyResponse | null>(null);
  lastQuoteSell = signal<QuoteSellResponse | null>(null);
  quoteError = signal<string | null>(null);
  isQuoting = signal<boolean>(false);
  isSubmitting = signal<boolean>(false);
  isSeeding = signal<boolean>(false);
  submitMessage = signal<string | null>(null);
  isConfirmOpen = signal<boolean>(false);

  // Wallet context (temporary; backend can also infer from session later)
  userAddress = signal<string>('0x0000000000000000000000000000000000000000');

  /** Niveau de frais on-chain (0=Bronze, 1=Silver, 2=Gold, 3=Diamond, 4=Platinum). */
  feeLevel = signal<number>(0);

  /** Frais P2P par tier (bps) — P2PExchange.sol: Bronze 80, Silver 75, Gold 70, Diamond 60, Platinum 50. */
  private static readonly P2P_FEE_BPS = [80, 75, 70, 60, 50];

  /** Frais Piscine par tier (bps) — LiquidityPool.sol: Bronze 100, Silver 95, Gold 90, Diamond 85, Platinum 80. */
  private static readonly POOL_FEE_BPS = [100, 95, 90, 85, 80];

  /** Vérifie si les frais du devis piscine correspondent au tier attendu (tolerance 5%). */
  poolFeesValid = computed(() => {
    if (this.orderMode() !== 'piscine' || this.amount() <= 0) return true;
    const level = Math.min(Math.max(this.feeLevel(), 0), 4);
    const bps = PasserOrdrePage.POOL_FEE_BPS[level];
    const expectedRate = (bps / 10_000) * 1.19;

    if (this.orderType() === 'buy') {
      const q = this.lastQuoteBuy();
      if (!q) return true;
      const tndIn = this.amountType() === 'tnd' ? this.amount() : this.amount() * this.currentPrice();
      const quoteFee = this.from1e8(q.totalFee);
      const expectedFee = tndIn * expectedRate;
      return this.feesWithinTolerance(quoteFee, expectedFee);
    } else {
      const q = this.lastQuoteSell();
      if (!q) return true;
      const tokenAmt = this.amountType() === 'tokens' ? this.amount() : this.amount() / Math.max(this.currentPrice(), 1e-9);
      const grossTnd = tokenAmt * this.from1e8(q.priceClient);
      const quoteFee = this.from1e8(q.totalFee);
      const expectedFee = grossTnd * expectedRate;
      return this.feesWithinTolerance(quoteFee, expectedFee);
    }
  });

  /** Message d'avertissement si les frais piscine semblent incohérents. */
  poolFeesWarning = computed(() => {
    if (this.poolFeesValid()) return null;
    return 'Les frais du devis semblent incohérents avec votre tier. Vérifiez votre profil ou actualisez.';
  });

  private feesWithinTolerance(actual: number, expected: number): boolean {
    if (expected <= 0) return actual <= 0;
    const ratio = actual / expected;
    return ratio >= 0.95 && ratio <= 1.05;
  }

  /** Taux effectif P2P (base + TVA 19%) pour l'affichage et le calcul. */
  p2pFeeRate = computed(() => {
    const level = Math.min(Math.max(this.feeLevel(), 0), 4);
    const bps = PasserOrdrePage.P2P_FEE_BPS[level];
    return (bps / 10_000) * 1.19; // base + TVA
  });

  /** Pourcentage affiché (base + TVA, ex: 0.95 pour Bronze). */
  p2pFeePercent = computed(() => {
    const level = Math.min(Math.max(this.feeLevel(), 0), 4);
    const bps = PasserOrdrePage.P2P_FEE_BPS[level];
    return ((bps / 10_000) * 1.19 * 100).toFixed(2);
  });

  cashBalance = signal<number>(0);
  creditDebt = signal<number>(0);  // Avance en cours (Credit Wallet)
  /** Montants réservés par ordres P2P en attente (non utilisables pour d'autres ordres). */
  p2pReservedCash = signal<number>(0);
  p2pReservedTokens = signal<Record<string, number>>({});  // token addr -> amount
  /** Soldes token par fond (Atlas/Didon) pour validation vente P2P. */
  tokenBalanceAtlas = signal<number>(0);
  tokenBalanceDidon = signal<number>(0);
  /** Priorité Crédit : utiliser le Credit Wallet en premier (recommandé), puis Cash Wallet. */
  priorityCredit = signal<boolean>(true);
  
  currentPrice = computed(() => {
    return this.tokenType() === 'Atlas' ? this.alphaPrice() : this.betaPrice();
  });

  tokenLabel = computed(() => (this.tokenType() === 'Atlas' ? 'CPEF Atlas' : 'CPEF Didon'));
  tokenSymbol = computed(() => (this.tokenType() === 'Atlas' ? 'ATLAS' : 'DIDON'));
  
  tokenAmount = computed(() => {
    // In piscine mode, prefer server quote outputs
    if (this.orderMode() === 'piscine') {
      if (this.orderType() === 'buy') {
        const q = this.lastQuoteBuy();
        return q ? this.from1e8(q.minted) : 0;
      } else {
        // sell: tokenAmount is the input (approx)
        return this.amountType() === 'tokens' ? this.amount() : this.amount() / Math.max(this.currentPrice(), 1e-9);
      }
    }

    // P2P fallback (local estimation)
    if (this.amountType() === 'tokens') return this.amount();
    return this.amount() / Math.max(this.currentPrice(), 1e-9);
  });
  
  tndAmount = computed(() => {
    // In piscine mode, prefer server quote outputs
    if (this.orderMode() === 'piscine') {
      if (this.orderType() === 'buy') {
        // tndIn is the input (approx, for display)
        return this.amountType() === 'tnd' ? this.amount() : this.amount() * this.currentPrice();
      } else {
        const q = this.lastQuoteSell();
        return q ? this.from1e8(q.tndOut) : 0;
      }
    }

    // P2P fallback (local estimation)
    if (this.amountType() === 'tnd') return this.amount();
    return this.tokenAmount() * this.currentPrice();
  });
  
  fees = computed(() => {
    if (this.orderMode() === 'piscine') {
      if (this.orderType() === 'buy') {
        const q = this.lastQuoteBuy();
        return q ? this.from1e8(q.totalFee) : 0;
      } else {
        const q = this.lastQuoteSell();
        return q ? this.from1e8(q.totalFee) : 0;
      }
    }
    return this.tndAmount() * this.p2pFeeRate();
  });
  
  totalCost = computed(() => {
    if (this.orderMode() === 'piscine') {
      if (this.orderType() === 'buy') {
        // Total paid = input tndIn (user pays that to pool in our UI)
        return this.amountType() === 'tnd' ? this.amount() : this.amount() * this.currentPrice();
      } else {
        // Sell: net credited already in tndAmount()
        return this.tndAmount();
      }
    }

    // P2P fallback (local estimation)
    if (this.orderType() === 'buy') return this.tndAmount() + this.fees();
    return this.tndAmount() - this.fees();
  });
  
  /** Cash disponible = solde - réservé par ordres P2P en attente. */
  availableCash = computed(() => Math.max(0, this.cashBalance() - this.p2pReservedCash()));

  cashAfter = computed(() => {
    if (this.orderType() === 'buy') {
      return this.cashBalance() - this.totalCost();
    }
    return this.cashBalance() + this.totalCost();
  });

  /** Tokens disponibles pour le token sélectionné (vente P2P) = solde - réservés. */
  availableTokensForSell = computed(() => {
    const fund = this.getSelectedFund();
    if (!fund?.token) return 0;
    const bal = this.tokenType() === 'Atlas' ? this.tokenBalanceAtlas() : this.tokenBalanceDidon();
    const reserved = this.p2pReservedTokens()[fund.token.toLowerCase()] ?? 0;
    return Math.max(0, bal - reserved);
  });

  canConfirm = computed(() => {
    if (this.amount() <= 0) return false;
    if (this.orderType() === 'buy') {
      return this.totalCost() <= this.availableCash();
    }
    if (this.orderType() === 'sell') {
      return this.tokenAmount() <= this.availableTokensForSell();
    }
    return true;
  });

  /** Répartition achat : montant pris sur Credit Wallet (Priorité Crédit = crédit disponible via getCreditDisplay). */
  fromCreditWallet = computed(() => {
    if (this.orderType() !== 'buy' || this.creditDebt() <= 0) return 0;
    if (this.priorityCredit()) {
      const creditDisplay = this.creditTracking.getCreditDisplay(
        this.cashBalance(), this.creditDebt(), this.userAddress()
      );
      return Math.min(this.totalCost(), creditDisplay);
    }
    const cashDisplay = this.creditTracking.getCashDisplay(
      this.cashBalance(), this.creditDebt(), this.userAddress()
    );
    return Math.max(0, this.totalCost() - Math.min(this.totalCost(), cashDisplay));
  });

  /** Répartition achat : montant pris sur Cash Wallet. */
  fromCashWallet = computed(() => {
    if (this.orderType() !== 'buy') return 0;
    return Math.max(0, this.totalCost() - this.fromCreditWallet());
  });

  /** Affichage Credit Wallet (avec Priorité Crédit). */
  creditDisplay = computed(() =>
    this.creditTracking.getCreditDisplay(this.cashBalance(), this.creditDebt(), this.userAddress())
  );

  /** Affichage Cash Wallet (avec Priorité Crédit). */
  cashDisplay = computed(() =>
    this.creditTracking.getCashDisplay(this.cashBalance(), this.creditDebt(), this.userAddress())
  );

  confirmSummary = computed<ConfirmOrderSummary | null>(() => {
    if (this.amount() <= 0) return null;
    return {
      tokenLabel: this.tokenLabel(),
      tokenSymbol: this.tokenSymbol(),
      orderType: this.orderType(),
      orderMode: this.orderMode(),
      amountType: this.amountType(),
      amount: this.amount(),
      price: this.currentPrice(),
      fees: this.fees(),
      totalCost: this.totalCost(),
      cashAfter: this.cashAfter(),
    };
  });

  constructor(
    private router: Router,
    private route: ActivatedRoute
  ) {
    // effect() must run in injection context (constructor); triggers refreshQuote when tracked signals change
    effect(() => {
      this.tokenType();
      this.orderType();
      this.orderMode();
      this.amountType();
      this.amount();
      this.currentPrice();
      this.refreshQuote();
    });
  }

  ngOnInit() {
    this.route.queryParams.subscribe((params) => {
      if (params['token']) {
        const raw = String(params['token']);
        const normalized =
          raw.toLowerCase() === 'alpha' ? 'Atlas'
          : raw.toLowerCase() === 'beta' ? 'Didon'
          : raw;
        if (normalized === 'Atlas' || normalized === 'Didon') {
          this.tokenType.set(normalized);
        }
      }
      if (params['action']) {
        this.orderType.set(params['action'] as OrderType);
      }
    });

    const saved = localStorage.getItem('walletAddress');
    if (saved && saved.startsWith('0x')) {
      this.userAddress.set(saved);
    }

    // Defer API calls to next tick to avoid ExpressionChangedAfterItHasBeenCheckedError
    setTimeout(() => {
      this.authApi.me().subscribe({
        next: (u) => {
          const wallet = (u as any)?.walletAddress as string | undefined;
          if (wallet && wallet.startsWith('0x') && wallet.length === 42) {
            this.userAddress.set(wallet);
            localStorage.setItem('walletAddress', wallet);
            this.api.getPortfolio(wallet as any).subscribe({
              next: (p) => {
                const cash = p.cashBalanceTnd ? this.from1e8(p.cashBalanceTnd) : 0;
                const cred = p.creditDebtTnd ? this.from1e8(p.creditDebtTnd) : 0;
                this.creditTracking.syncWithCreditDebt(wallet, cred);
                this.cashBalance.set(cash);
                this.creditDebt.set(cred);
                this.setTokenBalancesFromPositions(p.positions);
                this.refreshP2PReservations(wallet);
              },
              error: () => {},
            });
            this.api.getInvestorProfile(wallet as any).subscribe({
              next: (prof) => {
                this.feeLevel.set(Math.min(Math.max(prof.feeLevel ?? 0, 0), 4));
              },
              error: () => {},
            });
          }
        },
        error: () => {},
      });

      this.api.listFunds().subscribe({
        next: (res) => {
          const atlas = res.funds.find((f) => f.name.toLowerCase().includes('atlas')) ?? res.funds[0] ?? null;
          const didon = res.funds.find((f) => f.name.toLowerCase().includes('didon')) ?? res.funds[1] ?? null;
          this.fundAlpha.set(atlas ?? null);
          this.fundBeta.set(didon ?? null);
          this.refreshVni();
          this.refreshQuote();
        },
        error: (e) => {
          this.quoteError.set(this.formatErr(e));
        },
      });
    }, 0);
  }

  toggleOrderMode() {
    this.orderMode.set(this.orderMode() === 'piscine' ? 'p2p' : 'piscine');
  }

  selectValidityPeriod(period: ValidityPeriod): void {
    this.validityPeriod.set(period);
  }

  switchAmountType() {
    this.amountType.set(this.amountType() === 'tnd' ? 'tokens' : 'tnd');
    if (this.amount() > 0) {
      if (this.amountType() === 'tnd') {
        this.amount.set(this.tokenAmount() * this.currentPrice());
      } else {
        this.amount.set(this.tndAmount() / this.currentPrice());
      }
    }
  }

  onAmountChange() {
    // handled by effect() too, but keep explicit hook for clarity
    this.refreshQuote();
  }

  onConfirm() {
    if (!this.canConfirm()) return;
    this.isConfirmOpen.set(true);
  }

  async onConfirmModal() {
    if (!this.canConfirm()) return;

    if (this.orderMode() === 'piscine') {
      const token = this.getSelectedFund()?.token;
      if (!token) return;

      this.quoteError.set(null);
      this.submitMessage.set(null);
      this.isSubmitting.set(true);
      const user = this.userAddress();

      if (this.orderType() === 'buy') {
        const tndIn = this.to1e8(this.amountType() === 'tnd' ? this.amount() : this.amount() * this.currentPrice());
        const fromCredit = this.priorityCredit() ? this.fromCreditWallet() : 0;
        this.api.buy({ token, user: user as any, tndIn }).subscribe({
          next: (res) => {
            this.submitMessage.set(res.message ?? 'Ordre soumis');
            this.isConfirmOpen.set(false);
            if (fromCredit > 0) this.creditTracking.addCreditUsed(user, fromCredit);
            // Refresh cash balance immediately and multiple times to ensure update
            this.refreshCashBalance();
            setTimeout(() => {
              this.refreshCashBalance();
              this.navbarClient?.refreshWalletBalance();
            }, 1000);
            setTimeout(() => {
              this.refreshCashBalance();
              this.navbarClient?.refreshWalletBalance();
            }, 3000); // Wait 3 seconds for transaction confirmation
            setTimeout(() => {
              this.router.navigate(['/acceuil-client']).then(() => {
                // Refresh again after navigation to ensure navbar is updated
                setTimeout(() => {
                  this.refreshCashBalance();
                  this.navbarClient?.refreshWalletBalance();
                }, 500);
              });
            }, 500);
          },
          error: (e) => this.quoteError.set(this.formatErr(e)),
          complete: () => this.isSubmitting.set(false),
        });
      } else {
        const tokenAmount = this.to1e8(
          this.amountType() === 'tokens' ? this.amount() : this.amount() / Math.max(this.currentPrice(), 1e-9)
        );
        this.api.sell({ token, user: user as any, tokenAmount }).subscribe({
          next: (res) => {
            this.submitMessage.set(res.message ?? 'Ordre soumis');
            this.isConfirmOpen.set(false);
            // Refresh cash balance immediately and multiple times to ensure update
            this.refreshCashBalance();
            setTimeout(() => {
              this.refreshCashBalance();
              this.navbarClient?.refreshWalletBalance();
            }, 1000);
            setTimeout(() => {
              this.refreshCashBalance();
              this.navbarClient?.refreshWalletBalance();
            }, 3000); // Wait 3 seconds for transaction confirmation
            setTimeout(() => {
              this.router.navigate(['/acceuil-client']).then(() => {
                // Refresh again after navigation to ensure navbar is updated
                setTimeout(() => {
                  this.refreshCashBalance();
                  this.navbarClient?.refreshWalletBalance();
                }, 500);
              });
            }, 500);
          },
          error: (e) => this.quoteError.set(this.formatErr(e)),
          complete: () => this.isSubmitting.set(false),
        });
      }
      return;
    }

    // P2P mode: Utiliser le wallet WaaS géré par la plateforme (pas de MetaMask nécessaire)
    const fund = this.getSelectedFund();
    const token = fund?.token;
    if (!token) return;

    this.quoteError.set(null);
    this.submitMessage.set(null);

    const side = this.orderType(); // buy/sell
    const nonce = String(Date.now()); // ok for MVP
    const periodSec = VALIDITY_SECONDS[this.validityPeriod()];
    const deadline = String(Math.floor(Date.now() / 1000) + periodSec);

    const tokenAmount1e8 =
      side === 'buy'
        ? this.to1e8(this.amountType() === 'tokens' ? this.amount() : this.amount() / Math.max(this.currentPrice(), 1e-9))
        : this.to1e8(this.amountType() === 'tokens' ? this.amount() : this.amount() / Math.max(this.currentPrice(), 1e-9));

    const pricePerToken1e8 = this.to1e8(this.currentPrice());

    this.isSubmitting.set(true);
    
    // Utiliser le nouveau système d'order book (wallet WaaS automatique)
    // Le backend récupère automatiquement l'utilisateur connecté via JWT
    this.api
      .submitOrder({
        maker: '0x0000000000000000000000000000000000000000' as any, // Ignoré par le backend, remplacé par userId du JWT
        side,
        token: token as any,
        tokenAmount: tokenAmount1e8,
        pricePerToken: pricePerToken1e8,
        nonce,
        deadline,
        // signature optionnelle - pas nécessaire avec WaaS
      })
      .subscribe({
        next: (res) => {
          if (res.status === 'SETTLED' && res.matchedOrder) {
            this.submitMessage.set(`Ordre matché et réglé! Transaction: ${res.matchedOrder.settlementTxHash || 'en cours'}`);
          } else if (res.status === 'MATCHED') {
            this.submitMessage.set('Ordre matché, settlement en cours...');
          } else {
            this.submitMessage.set(`Ordre soumis (ID: ${res.orderId}). En attente de matching.`);
          }
          if (res.poolSpreadWarning) {
            this.submitMessage.update((m) => (m ? `${m} ${res.poolSpreadWarning}` : res.poolSpreadWarning!));
          }
          // Rafraîchir le solde après transaction
          if (res.status === 'SETTLED' || res.status === 'MATCHED') {
            this.cashBalance.set(this.cashAfter());
            if (side === 'buy' && this.priorityCredit()) {
              const user = this.userAddress();
              const fromCredit = this.fromCreditWallet();
              if (fromCredit > 0 && user) this.creditTracking.addCreditUsed(user, fromCredit);
            }
            setTimeout(() => {
              this.navbarClient?.refreshWalletBalance();
            }, 1000);
          }
          this.isConfirmOpen.set(false);
          setTimeout(() => this.router.navigate(['/acceuil-client']), 2000);
        },
        error: (e) => {
          this.quoteError.set(this.formatErr(e));
          this.isSubmitting.set(false);
        },
        complete: () => this.isSubmitting.set(false),
      });
  }

  onCancel() {
    this.router.navigate(['/acceuil-client']);
  }

  /** Alimente le Cash Wallet (10 000 TND) pour pouvoir effectuer les transactions. */
  onSeedCash() {
    const user = this.userAddress();
    if (!user || !user.startsWith('0x') || user.length !== 42) return;
    this.isSeeding.set(true);
    this.quoteError.set(null);
    this.api.seedCash(user, 10_000).subscribe({
      next: () => {
        this.refreshCashBalance();
        this.navbarClient?.refreshWalletBalance();
      },
      error: (e) => this.quoteError.set(this.formatErr(e)),
      complete: () => this.isSeeding.set(false),
    });
  }

  Math = Math; // Expose Math to template

  private getSelectedFund(): Fund | null {
    return this.tokenType() === 'Atlas' ? this.fundAlpha() : this.fundBeta();
  }


  private refreshVni() {
    const a = this.fundAlpha();
    const b = this.fundBeta();
    if (a?.token) {
      this.api.getVni(a.token).subscribe({
        next: (v) => this.alphaPrice.set(this.from1e8(v.vni)),
        error: () => {},
      });
    }
    if (b?.token) {
      this.api.getVni(b.token).subscribe({
        next: (v) => this.betaPrice.set(this.from1e8(v.vni)),
        error: () => {},
      });
    }
  }

  private refreshQuote() {
    if (this.orderMode() !== 'piscine') return;
    const f = this.getSelectedFund();
    if (!f) return;
    if (this.amount() <= 0) {
      this.lastQuoteBuy.set(null);
      this.lastQuoteSell.set(null);
      return;
    }

    const user = this.userAddress();
    this.isQuoting.set(true);
    this.quoteError.set(null);

    if (this.orderType() === 'buy') {
      const tndIn = this.to1e8(this.amountType() === 'tnd' ? this.amount() : this.amount() * this.currentPrice());
      this.api.quoteBuy({ token: f.token, user: user as any, tndIn }).subscribe({
        next: (q) => (this.lastQuoteBuy.set(q), this.lastQuoteSell.set(null)),
        error: (e) => this.quoteError.set(this.formatErr(e)),
        complete: () => this.isQuoting.set(false),
      });
    } else {
      const tokenAmount = this.to1e8(
        this.amountType() === 'tokens' ? this.amount() : this.amount() / Math.max(this.currentPrice(), 1e-9)
      );
      this.api.quoteSell({ token: f.token, user: user as any, tokenAmount }).subscribe({
        next: (q) => (this.lastQuoteSell.set(q), this.lastQuoteBuy.set(null)),
        error: (e) => this.quoteError.set(this.formatErr(e)),
        complete: () => this.isQuoting.set(false),
      });
    }
  }

  private from1e8(v: string): number {
    return Number(v) / 1e8;
  }

  private to1e8(n: number): string {
    // avoid floating rounding issues in UI: keep 2 decimals for TND and 4 for token-ish
    const scaled = Math.round(n * 1e8);
    return String(scaled);
  }

  /**
   * Refresh cash balance from blockchain after transaction.
   */
  private setTokenBalancesFromPositions(positions: { token?: string; balanceTokens?: string; lockedTokens1e8?: string }[]) {
    const pick = (name: string) =>
      positions.find((p: any) => (p.name ?? '').toLowerCase().includes(name) || (p.symbol ?? '').toLowerCase().includes(name));
    const atlas = pick('atlas') ?? positions[0];
    const didon = pick('didon') ?? positions[1];
    const toNum = (s: string | undefined) => (s ? this.from1e8(s) : 0);
    const toNumLocked = (s: string | undefined) => (s ? this.from1e8(s) : 0);
    this.tokenBalanceAtlas.set(atlas ? toNum(atlas.balanceTokens) - toNumLocked(atlas.lockedTokens1e8) : 0);
    this.tokenBalanceDidon.set(didon ? toNum(didon.balanceTokens) - toNumLocked(didon.lockedTokens1e8) : 0);
  }

  private refreshP2PReservations(wallet: string) {
    this.api.getP2PReservations(wallet).subscribe({
      next: (r) => {
        this.p2pReservedCash.set(r.reservedCashTnd1e8 ? this.from1e8(r.reservedCashTnd1e8) : 0);
        const tokens: Record<string, number> = {};
        for (const [tk, amt] of Object.entries(r.reservedTokens1e8 ?? {})) {
          tokens[tk.toLowerCase()] = this.from1e8(amt);
        }
        this.p2pReservedTokens.set(tokens);
      },
      error: () => {
        this.p2pReservedCash.set(0);
        this.p2pReservedTokens.set({});
      },
    });
  }

  private refreshCashBalance() {
    const user = this.userAddress();
    console.log('[PasserOrdrePage] refreshCashBalance() called for user:', user);
    if (!user || !user.startsWith('0x') || user.length !== 42) {
      console.warn('[PasserOrdrePage] Invalid user address:', user);
      return;
    }
    
    this.api.getPortfolio(user as any).subscribe({
      next: (p) => {
        const newBalance = p.cashBalanceTnd ? this.from1e8(p.cashBalanceTnd) : 0;
        const newCredit = p.creditDebtTnd ? this.from1e8(p.creditDebtTnd) : 0;
        this.creditTracking.syncWithCreditDebt(user, newCredit);
        this.cashBalance.set(newBalance);
        this.creditDebt.set(newCredit);
        this.setTokenBalancesFromPositions(p.positions);
        this.refreshP2PReservations(user);
      },
      error: (err) => {
        console.error('[PasserOrdrePage] Error refreshing cash balance:', err);
      },
    });
  }

  private formatErr(e: any): string {
    return e?.error?.message ?? e?.message ?? 'Erreur API';
  }
}
