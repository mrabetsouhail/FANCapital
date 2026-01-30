import { Component, signal, computed, OnInit, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import type { Fund } from '../../../blockchain/models/fund.models';
import type { QuoteBuyResponse, QuoteSellResponse } from '../../../blockchain/models/pricing.models';

type OrderMode = 'piscine' | 'p2p';
type OrderType = 'buy' | 'sell';
type TokenType = 'Alpha' | 'Beta';
type AmountType = 'tnd' | 'tokens';

@Component({
  selector: 'app-passer-ordre-page',
  imports: [CommonModule, FormsModule, NavbarClient],
  templateUrl: './passer-ordre-page.html',
  styleUrl: './passer-ordre-page.css',
})
export class PasserOrdrePage implements OnInit {
  private readonly api = inject(BlockchainApiService);

  tokenType = signal<TokenType>('Alpha');
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

  // Wallet context (temporary; backend can also infer from session later)
  userAddress = signal<string>('0x0000000000000000000000000000000000000000');
  
  // Fallback fee rate used only for P2P mode in this UI (until we wire P2P quotes)
  feeRate = 0.005;
  
  cashBalance = signal<number>(5000.00);
  
  currentPrice = computed(() => {
    return this.tokenType() === 'Alpha' ? this.alphaPrice() : this.betaPrice();
  });
  
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

  constructor(
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
   
    this.route.queryParams.subscribe(params => {
      if (params['token']) {
        this.tokenType.set(params['token'] as TokenType);
      }
      if (params['action']) {
        this.orderType.set(params['action'] as OrderType);
      }
    });

    const saved = localStorage.getItem('walletAddress');
    if (saved && saved.startsWith('0x')) {
      this.userAddress.set(saved);
    }

    // Load funds + initial VNIs
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

    // Auto-refresh quote when key inputs change
    effect(() => {
      // read signals to track
      this.tokenType();
      this.orderType();
      this.orderMode();
      this.amountType();
      this.amount();
      this.currentPrice();

      // trigger (best-effort)
      this.refreshQuote();
    });
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

    if (this.orderMode() === 'piscine') {
      const token = this.getSelectedFund()?.token;
      if (!token) return;

      this.quoteError.set(null);
      const user = this.userAddress();

      if (this.orderType() === 'buy') {
        const tndIn = this.to1e8(this.amountType() === 'tnd' ? this.amount() : this.amount() * this.currentPrice());
        this.api.buy({ token, user: user as any, tndIn }).subscribe({
          next: (res) => console.log('BUY:', res),
          error: (e) => (this.quoteError.set(this.formatErr(e)), console.error(e)),
        });
      } else {
        const tokenAmount = this.to1e8(
          this.amountType() === 'tokens' ? this.amount() : this.amount() / Math.max(this.currentPrice(), 1e-9)
        );
        this.api.sell({ token, user: user as any, tokenAmount }).subscribe({
          next: (res) => console.log('SELL:', res),
          error: (e) => (this.quoteError.set(this.formatErr(e)), console.error(e)),
        });
      }
      return;
    }

    // P2P mode: still local-only UI (backend matching engine not wired here yet)
    console.log('Order confirmed (P2P UI only):', {
      token: this.tokenType(),
      type: this.orderType(),
      mode: this.orderMode(),
      amount: this.amount(),
      amountType: this.amountType(),
      totalCost: this.totalCost(),
      fees: this.fees(),
    });
  }

  onCancel() {
    this.router.navigate(['/acceuil-client']);
  }

  Math = Math; // Expose Math to template

  private getSelectedFund(): Fund | null {
    return this.tokenType() === 'Alpha' ? this.fundAlpha() : this.fundBeta();
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

  private formatErr(e: any): string {
    return e?.error?.message ?? e?.message ?? 'Erreur API';
  }
}
