import { Component, signal, computed, OnInit, effect, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';
import { ConfirmOrderModal, type ConfirmOrderSummary } from '../confirm-order-modal/confirm-order-modal';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import type { Fund } from '../../../blockchain/models/fund.models';
import type { QuoteBuyResponse, QuoteSellResponse } from '../../../blockchain/models/pricing.models';

type OrderMode = 'piscine' | 'p2p';
type OrderType = 'buy' | 'sell';
type TokenType = 'Atlas' | 'Didon';
type AmountType = 'tnd' | 'tokens';

@Component({
  selector: 'app-passer-ordre-page',
  imports: [CommonModule, FormsModule, NavbarClient, ConfirmOrderModal],
  templateUrl: './passer-ordre-page.html',
  styleUrl: './passer-ordre-page.css',
})
export class PasserOrdrePage implements OnInit {
  private readonly api = inject(BlockchainApiService);
  private readonly authApi = inject(AuthApiService);
  @ViewChild(NavbarClient) navbarClient?: NavbarClient;

  tokenType = signal<TokenType>('Atlas');
  orderType = signal<OrderType>('buy');
  
  orderMode = signal<OrderMode>('piscine');
  
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
  
  // Fallback fee rate used only for P2P mode in this UI (until we wire P2P quotes)
  feeRate = 0.005;
  
  cashBalance = signal<number>(0);
  
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
    return this.tndAmount() * this.feeRate;
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
  
  cashAfter = computed(() => {
    if (this.orderType() === 'buy') {
      return this.cashBalance() - this.totalCost();
    }
    return this.cashBalance() + this.totalCost();
  });
  
  canConfirm = computed(() => {
    if (this.amount() <= 0) return false;
    if (this.orderType() === 'buy') {
      return this.totalCost() <= this.cashBalance();
    }
    return true; 
  });

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
                this.cashBalance.set(p.cashBalanceTnd ? this.from1e8(p.cashBalanceTnd) : 0);
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
        this.api.buy({ token, user: user as any, tndIn }).subscribe({
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
    const deadline = String(Math.floor(Date.now() / 1000) + 3600); // +1 heure par défaut

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
          // Rafraîchir le solde après transaction
          if (res.status === 'SETTLED' || res.status === 'MATCHED') {
            this.cashBalance.set(this.cashAfter());
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
        console.log('[PasserOrdrePage] Cash balance updated to:', newBalance, 'TND (raw:', p.cashBalanceTnd, ')');
        this.cashBalance.set(newBalance);
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
