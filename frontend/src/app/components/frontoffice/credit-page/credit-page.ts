import { Component, signal, computed, OnInit, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, catchError, of } from 'rxjs';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import { CreditWalletTrackingService } from '../../../auth/services/credit-wallet-tracking.service';
import type { PortfolioResponse, PortfolioPosition } from '../../../blockchain/models/portfolio.models';
import type { ActiveLoanInfo } from '../../../blockchain/models/pricing.models';

const PRICE_SCALE = 1e8;

/** Spec v4.7: taux annuel par tier (0=BRONZE, 1=SILVER 5%, 2=GOLD 4.5%, 3=PLATINUM 3.5%, 4=DIAMOND 3%) */
const TIER_RATES: Record<number, number> = { 0: 0, 1: 5.0, 2: 4.5, 3: 3.5, 4: 3.0 };
/** Spec v4.7: durée max par tier (jours) - fallback si avance non chargée */
const TIER_DURATION_DAYS: Record<number, number> = { 0: 0, 1: 90, 2: 120, 3: 150, 4: 365 };
const LIQUIDATION_BPS = 85; // 85% liquidation (spec)
const LIQUIDATION_THRESHOLD = LIQUIDATION_BPS / 100;

export interface CollateralToken {
  id: number;
  type: 'Alpha' | 'Beta';
  amount: number;
  lockedAmount: number;
  price: number;
  lockedValue: number;
}

@Component({
  selector: 'app-credit-page',
  imports: [CommonModule, FormsModule, RouterModule, NavbarClient, BackButton],
  templateUrl: './credit-page.html',
  styleUrls: ['./credit-page.css', '../../../../styles/theme.css'],
})
export class CreditPage implements OnInit {
  private readonly api = inject(BlockchainApiService);
  private readonly authApi = inject(AuthApiService);
  private readonly creditTracking = inject(CreditWalletTrackingService);

  @ViewChild(NavbarClient) navbarClient?: NavbarClient;

  userAddress = signal<string>('');
  loading = signal<boolean>(true);
  error = signal<string>('');

  portfolio = signal<PortfolioResponse | null>(null);
  collateralTokens = signal<CollateralToken[]>([]);
  totalCreditUsed = signal<number>(0);
  activeAdvance = signal<ActiveLoanInfo | null>(null);
  feeLevel = signal<number>(1); // 0=BRONZE, 1=SILVER, ...
  repaymentAmount = signal<number>(0);
  repaySubmitting = signal<boolean>(false);
  repayMessage = signal<string>('');
  repayError = signal<boolean>(false);

  liquidationThreshold = LIQUIDATION_BPS;

  totalCollateralValue = computed(() => {
    return this.collateralTokens().reduce((sum, t) => sum + t.lockedValue, 0);
  });

  totalCollateral = computed(() => {
    return this.collateralTokens().reduce((sum, t) => sum + t.lockedAmount, 0);
  });

  ltvRatio = computed(() => {
    const coll = this.totalCollateralValue();
    if (coll <= 0) return this.totalCreditUsed() > 0 ? 999 : 0;
    return (this.totalCreditUsed() / coll) * 100;
  });

  ltvHealth = computed(() => {
    const ltv = this.ltvRatio();
    if (ltv < 50) return { status: 'safe', color: '#22c55e', label: 'Sécurisé' };
    if (ltv < 75) return { status: 'warning', color: '#f59e0b', label: 'Attention' };
    if (ltv < 90) return { status: 'danger', color: '#ef4444', label: 'Risque' };
    return { status: 'critical', color: '#dc2626', label: 'Critique' };
  });

  marginBeforeLiquidation = computed(() => {
    return this.liquidationThreshold - this.ltvRatio();
  });

  hasActiveAdvance = computed(() => this.totalCreditUsed() > 0);

  /** Principal restant (TND). */
  principalTnd = computed(() => this.totalCreditUsed());

  /** Taux annuel selon tier (Spec v4.7). */
  interestRatePercent = computed(() => TIER_RATES[this.feeLevel()] ?? 0);

  /** Intérêts restants à rembourser (fixes dès le début, prélevés en priorité). */
  interestAmountTnd = computed(() => {
    const adv = this.activeAdvance();
    if (adv?.totalInterestTnd != null && adv?.interestPaidTnd != null) {
      const total = Number(adv.totalInterestTnd) / PRICE_SCALE;
      const paid = Number(adv.interestPaidTnd) / PRICE_SCALE;
      return Math.round(Math.max(0, total - paid) * 100) / 100;
    }
    const principal = this.principalTnd();
    const rate = this.interestRatePercent() / 100;
    if (principal <= 0 || rate <= 0) return 0;
    const durationDays = adv?.durationDays ?? TIER_DURATION_DAYS[this.feeLevel()] ?? 90;
    if (durationDays <= 0) return 0;
    return Math.round(principal * rate * (durationDays / 365) * 100) / 100;
  });

  /** Intérêts totaux à l'échéance (informatif). */
  interestAtMaturityTnd = computed(() => {
    const adv = this.activeAdvance();
    if (adv?.totalInterestTnd != null) {
      return Number(adv.totalInterestTnd) / PRICE_SCALE;
    }
    const principal = this.principalTnd();
    const rate = this.interestRatePercent() / 100;
    if (principal <= 0 || rate <= 0) return 0;
    const durationDays = adv?.durationDays ?? TIER_DURATION_DAYS[this.feeLevel()] ?? 90;
    if (durationDays <= 0) return 0;
    return Math.round(principal * rate * (durationDays / 365) * 100) / 100;
  });

  /** Total à rembourser (principal + intérêts). */
  totalToRepayTnd = computed(() => {
    const p = this.principalTnd();
    const i = this.interestAmountTnd();
    return Math.round((p + i) * 100) / 100;
  });

  /** Principal restant après un remboursement (part capital ≈ montant × principal/total). */
  principalAfterRepayment = computed(() => {
    const amt = this.repaymentAmount();
    const total = this.totalToRepayTnd();
    const principal = this.principalTnd();
    if (amt <= 0 || total <= 0) return principal;
    const principalPart = Math.min(amt * (principal / total), principal);
    return Math.round(Math.max(0, principal - principalPart) * 100) / 100;
  });

  cashBalanceTnd = computed(() => {
    const p = this.portfolio();
    if (!p?.cashBalanceTnd) return 0;
    return Number(p.cashBalanceTnd) / PRICE_SCALE;
  });

  constructor() {}

  ngOnInit(): void {
    const saved = localStorage.getItem('walletAddress');
    if (saved?.startsWith('0x')) this.userAddress.set(saved);

    this.authApi.me().subscribe({
      next: (u) => {
        const addr = (u as any)?.walletAddress as string | undefined;
        if (addr?.startsWith('0x') && addr.length === 42) {
          this.userAddress.set(addr);
          localStorage.setItem('walletAddress', addr);
          this.refresh();
        } else if (this.userAddress()) {
          this.refresh();
        } else {
          this.loading.set(false);
          this.error.set('Connectez votre wallet pour afficher votre crédit.');
        }
      },
      error: () => {
        this.loading.set(false);
        if (this.userAddress()) this.refresh();
      },
    });
  }

  refresh() {
    const user = this.userAddress();
    if (!user?.startsWith('0x')) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      portfolio: this.api.getPortfolio(user),
      advance: this.api.getActiveAdvance(user),
      profile: this.api.getInvestorProfile(user).pipe(catchError(() => of(null))),
      sci: this.api.getSciScore(user).pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ portfolio: p, advance, profile, sci }) => {
        this.portfolio.set(p);
        this.totalCreditUsed.set(Number(p.creditDebtTnd ?? 0) / PRICE_SCALE);
        this.buildCollateralTokens(p.positions ?? []);
        this.activeAdvance.set(advance ?? null);
        const creditUsed = Number(p.creditDebtTnd ?? 0) / PRICE_SCALE;
        const flProfile = (profile as any)?.feeLevel;
        const flSci = (sci as any)?.effectiveTier;
        let fl = typeof flProfile === 'number' ? flProfile : typeof flSci === 'number' ? flSci : 1;
        if (creditUsed > 0 && fl < 1) fl = 1;
        this.feeLevel.set(Math.min(4, Math.max(0, fl)));
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message ?? err.statusText ?? 'Erreur chargement portfolio');
      },
    });
  }

  private buildCollateralTokens(positions: PortfolioPosition[]): void {
    const tokens: CollateralToken[] = [];
    let id = 1;
    for (const p of positions) {
      const locked = Number(p.lockedTokens1e8 ?? 0) / PRICE_SCALE;
      if (locked <= 0) continue;
      const balance = Number(p.balanceTokens ?? 0) / PRICE_SCALE;
      const price = Number(p.vni ?? 0) / PRICE_SCALE;
      const lockedValue = locked * price;
      const type = p.name?.toLowerCase().includes('atlas')
        ? ('Alpha' as const)
        : ('Beta' as const);
      tokens.push({
        id: id++,
        type,
        amount: balance,
        lockedAmount: locked,
        price,
        lockedValue,
      });
    }
    this.collateralTokens.set(tokens);
  }

  getLTVPercentage(): number {
    return Math.min(this.ltvRatio(), 100);
  }

  getLTVArc(): { dasharray: number; dashoffset: number } {
    const percentage = this.getLTVPercentage();
    const radius = 80;
    const circumference = 2 * Math.PI * radius;
    const offset = circumference - (percentage / 100) * circumference;
    return { dasharray: circumference, dashoffset: offset };
  }

  onRepay() {
    const amount = this.repaymentAmount();
    if (amount <= 0 || amount > this.totalToRepayTnd()) return;

    this.repaySubmitting.set(true);
    this.repayMessage.set('');
    this.repayError.set(false);
    this.api.repayAdvance(this.userAddress(), amount).subscribe({
      next: (res) => {
        this.repayError.set(false);
        this.repayMessage.set(res.message ?? res.txHash ?? 'Remboursement enregistré.');
        this.repaymentAmount.set(0);
        // Imputer le remboursement au Cash Wallet (pas au Credit)
        this.creditTracking.addRepaymentFromCash(this.userAddress(), amount);
        this.refresh();
        this.navbarClient?.refreshWalletBalance();
        this.repaySubmitting.set(false);
      },
      error: (err) => {
        this.repayError.set(true);
        const msg = err.error?.message ?? err.error?.error ?? err.statusText ?? 'Erreur remboursement';
        this.repayMessage.set(typeof msg === 'string' ? msg : JSON.stringify(msg));
        this.repaySubmitting.set(false);
      },
    });
  }

  getTokenTypeLabel(type: 'Alpha' | 'Beta'): string {
    return type === 'Alpha' ? 'CPEF Atlas' : 'CPEF Didon';
  }

  progressPercent(token: CollateralToken): number {
    if (token.amount <= 0) return 0;
    return Math.min(100, (token.lockedAmount / token.amount) * 100);
  }
}
