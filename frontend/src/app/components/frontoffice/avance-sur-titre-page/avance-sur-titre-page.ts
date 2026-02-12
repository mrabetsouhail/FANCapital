import { Component, signal, computed, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import type { PortfolioPosition } from '../../../blockchain/models/portfolio.models';

/** Spécifications Financières v4.7 - Sans frais de dossier */
const LTV_MAX_BPS = 70;         // 70% max à l'émission
const MARGIN_CALL_BPS = 75;     // Appel de marge à 75%
const LIQUIDATION_BPS = 85;     // Liquidation forcée à 85%

type CreditModel = 'A' | 'B';  // A = Taux fixe dégressif, B = PGP
type Tier = 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'DIAMOND';

const TIER_RATES: Record<Tier, number | null> = {
  BRONZE: null,
  SILVER: 5.0,
  GOLD: 4.5,
  PLATINUM: 3.5,
  DIAMOND: 3.0,
};

/** Durée max par tier (jours) - Spec v4.7 : SILVER 3m, GOLD 4m, PLATINUM 5m, DIAMOND 12m */
const TIER_DURATION_DAYS: Record<Tier, number> = {
  BRONZE: 0,
  SILVER: 90,
  GOLD: 120,
  PLATINUM: 150,
  DIAMOND: 365,
};

interface RepaymentSchedule {
  id: number;
  date: Date;
  amount: number;
  type: 'coupon' | 'echeance';
  status: 'pending' | 'completed';
}

const FEE_TO_TIER: Tier[] = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND'];

@Component({
  selector: 'app-avance-sur-titre-page',
  imports: [CommonModule, FormsModule, NavbarClient, DatePipe],
  templateUrl: './avance-sur-titre-page.html',
  styleUrl: './avance-sur-titre-page.css',
})
export class AvanceSurTitrePage implements OnInit {
  // Modèle d'avance: A (Taux fixe) ou B (PGP)
  creditModel = signal<CreditModel>('A');
  // Tier utilisateur (Score SCI) - automatiquement récupéré du profil investisseur
  userTier = signal<Tier>('BRONZE');

  // Tokens disponibles pour collatéral (chargés depuis le portfolio + VNI réels)
  availableTokens = signal<{ type: 'Atlas' | 'Didon'; amount: number; price: number }[]>([
    { type: 'Atlas', amount: 0, price: 0 },
    { type: 'Didon', amount: 0, price: 0 },
  ]);

  // Selected tokens for collateral
  selectedTokenType = signal<'Atlas' | 'Didon'>('Atlas');
  selectedTokenAmount = signal<number>(0);
  
  // Loan simulation - LTV 70% max (CREDIT_LOMBARD v4.51 / Mod AST 2.1)
  loanAmount = signal<number>(0);
  /** Avance max (70% LTV) avant retenue. */
  maxLoanAmount = computed(() => {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    if (!token) return 0;
    return (this.selectedTokenAmount() * token.price) * (LTV_MAX_BPS / 100);
  });

  /** Montant crédité au Credit Wallet (= avance max, sans frais de dossier v4.7). */
  maxCreditAmount = computed(() => this.maxLoanAmount());

  // LTV calculation
  currentLTV = computed(() => {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    if (!token || this.selectedTokenAmount() === 0) return 0;
    const collateralValue = this.selectedTokenAmount() * token.price;
    if (collateralValue === 0) return 0;
    return (this.loanAmount() / collateralValue) * 100;
  });

  // Liquidation à 85% LTV (CREDIT_LOMBARD v4.51)
  liquidationPrice = computed(() => {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    if (!token || this.selectedTokenAmount() === 0) return 0;
    return (this.loanAmount() / (this.selectedTokenAmount() * (LIQUIDATION_BPS / 100)));
  });

  // Risk visualization - zones: Safe <75%, Warning 75-85%, Danger >85%
  riskDataPoints = computed(() => {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    if (!token || this.selectedTokenAmount() === 0 || this.loanAmount() === 0) return [];
    
    const currentPrice = token.price;
    const liqPrice = this.liquidationPrice();
    const points = [];
    
    for (let i = 0; i <= 20; i++) {
      const price = currentPrice - ((currentPrice - liqPrice) / 20) * i;
      const ltv = (this.loanAmount() / (this.selectedTokenAmount() * price)) * 100;
      points.push({ price, ltv: Math.min(ltv, 100) });
    }
    
    return points;
  });

  // Repayment schedule
  repaymentSchedule = signal<RepaymentSchedule[]>([
    { id: 1, date: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), amount: 500, type: 'coupon', status: 'pending' },
    { id: 2, date: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000), amount: 500, type: 'coupon', status: 'pending' },
    { id: 3, date: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000), amount: 2000, type: 'echeance', status: 'pending' },
    { id: 4, date: new Date(Date.now() + 60 * 24 * 60 * 60 * 1000), amount: 2000, type: 'echeance', status: 'pending' },
  ]);

  // Taux annuel selon tier (Modèle A) - CREDIT_LOMBARD v4.51
  interestRate = computed(() => {
    const rate = TIER_RATES[this.userTier()];
    return rate ?? 0;
  });

  // PGP disponible uniquement PLATINUM et DIAMOND
  isPgpAvailable = computed(() => {
    const t = this.userTier();
    return t === 'PLATINUM' || t === 'DIAMOND';
  });

  MARGIN_CALL_BPS = MARGIN_CALL_BPS;
  LIQUIDATION_BPS = LIQUIDATION_BPS;
  LTV_MAX_BPS = LTV_MAX_BPS;

  tierLoading = signal<boolean>(true);
  tokensLoading = signal<boolean>(true);
  requestInProgress = signal<boolean>(false);
  requestError = signal<string | null>(null);
  requestSuccess = signal<{ txHash: string } | null>(null);
  userWallet = signal<string>('');

  constructor(
    private authApi: AuthApiService,
    private blockchainApi: BlockchainApiService
  ) {
    this.selectedTokenAmount.set(0);
  }

  ngOnInit(): void {
    this.authApi.me().subscribe({
      next: (u) => {
        const w = (u as any)?.walletAddress ?? localStorage.getItem('walletAddress') ?? '';
        this.userWallet.set(w ?? '');
      },
    });
    this.loadUserTier();
    this.loadTokensData();
  }

  private loadTokensData(): void {
    this.tokensLoading.set(true);
    this.authApi.me().subscribe({
      next: (u) => {
        const w = (u as any)?.walletAddress ?? localStorage.getItem('walletAddress') ?? '';
        if (!w || !w.startsWith('0x') || w.length !== 42) {
          this.tokensLoading.set(false);
          return;
        }
        this.blockchainApi.getPortfolio(w).subscribe({
          next: (p) => {
            const atlas = this.pickPosition(p.positions, 'atlas', 0);
            const didon = this.pickPosition(p.positions, 'didon', 1);
            const tokens: { type: 'Atlas' | 'Didon'; amount: number; price: number }[] = [
              { type: 'Atlas', amount: atlas ? this.from1e8(atlas.balanceTokens) : 0, price: atlas ? this.from1e8(atlas.vni) : 0 },
              { type: 'Didon', amount: didon ? this.from1e8(didon.balanceTokens) : 0, price: didon ? this.from1e8(didon.vni) : 0 },
            ];
            this.availableTokens.set(tokens);
            const maxForSelected = tokens.find((t) => t.type === this.selectedTokenType());
            if (maxForSelected && this.selectedTokenAmount() > maxForSelected.amount) {
              this.selectedTokenAmount.set(Math.floor(maxForSelected.amount));
            }
            this.updateLoanAmount();
            this.tokensLoading.set(false);
          },
          error: () => this.tokensLoading.set(false),
        });
      },
      error: () => this.tokensLoading.set(false),
    });
  }

  private pickPosition(positions: PortfolioPosition[], nameHint: string, idHint: number): PortfolioPosition | null {
    const byName = positions.find((x) => (x.name ?? '').toLowerCase().includes(nameHint))
      ?? positions.find((x) => (x.symbol ?? '').toLowerCase().includes(nameHint));
    if (byName) return byName;
    return positions.find((x) => x.fundId === idHint) ?? null;
  }

  private from1e8(v: string): number {
    return Number(v) / 1e8;
  }

  private loadUserTier(): void {
    this.tierLoading.set(true);
    this.authApi.me().subscribe({
      next: (u) => {
        const w = (u as any)?.walletAddress ?? localStorage.getItem('walletAddress') ?? '';
        if (!w || !w.startsWith('0x') || w.length !== 42) {
          this.tierLoading.set(false);
          return;
        }
        this.blockchainApi.getInvestorProfile(w).subscribe({
          next: (p) => {
            const tier = FEE_TO_TIER[Math.min(p.feeLevel, 4)] ?? 'BRONZE';
            this.userTier.set(tier);
            this.tierLoading.set(false);
          },
          error: () => {
            this.tierLoading.set(false);
          },
        });
      },
      error: () => this.tierLoading.set(false),
    });
  }

  onTokenTypeChange() {
    this.selectedTokenAmount.set(0);
    this.loanAmount.set(0);
  }

  onTokenAmountChange() {
    const n = Math.max(0, Math.floor(Number(this.selectedTokenAmount())));
    this.selectedTokenAmount.set(n);
    this.updateLoanAmount();
  }

  onLoanAmountChange() {
    if (this.loanAmount() > this.maxCreditAmount()) {
      this.loanAmount.set(this.maxCreditAmount());
    }
  }

  updateLoanAmount() {
    const maxCredit = this.maxCreditAmount();
    if (maxCredit > 0) {
      this.loanAmount.set(Math.min(this.loanAmount(), maxCredit));
    }
  }

  tierRangeLabel(): string {
    const t = this.userTier();
    if (t === 'BRONZE') return '0-15';
    if (t === 'SILVER') return '16-35';
    if (t === 'GOLD') return '36-55';
    if (t === 'PLATINUM') return '56-84';
    if (t === 'DIAMOND') return '85+';
    return '';
  }

  getTokenLabel(type: 'Atlas' | 'Didon'): string {
    return type;
  }

  getRepaymentTypeLabel(type: 'coupon' | 'echeance'): string {
    return type === 'coupon' ? 'Coupon' : 'Échéance';
  }

  getMaxPrice(): number {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    return token ? token.price : 0;
  }

  getMinPrice(): number {
    return this.liquidationPrice();
  }

  getChartHeight(): number {
    return 200;
  }

  getChartWidth(): number {
    return 600;
  }

  getTotalRepayment(): number {
    return this.repaymentSchedule().reduce((total, item) => total + item.amount, 0);
  }

  getSelectedToken() {
    return this.availableTokens().find(t => t.type === this.selectedTokenType());
  }

  getSelectedTokenPrice(): number {
    const token = this.getSelectedToken();
    return token ? token.price : 0;
  }

  getSelectedTokenAmount(): number {
    const token = this.getSelectedToken();
    return token ? token.amount : 0;
  }

  getCollateralValue(): number {
    return this.selectedTokenAmount() * this.getSelectedTokenPrice();
  }

  /** LTV variable = Dette / Valeur collatéral (Mod AST 4.1) */
  ltvVariablePercent(): number {
    const coll = this.getCollateralValue();
    if (coll <= 0) return 0;
    return (this.loanAmount() / coll) * 100;
  }

  /** Durée typique du tier (jours) */
  tierDurationDays(): number {
    return TIER_DURATION_DAYS[this.userTier()] ?? 90;
  }

  /** Simulation jours restants pour barre progression (Mod AST 4.1) */
  daysElapsedSim(): number {
    if (this.loanAmount() <= 0) return 0;
    return Math.floor(this.tierDurationDays() * 0.5);
  }

  daysRemainingSim(): number {
    return Math.max(0, this.tierDurationDays() - this.daysElapsedSim());
  }

  /** Demande d'avance — appelle l'API et soumet la tx on-chain (WaaS). */
  demanderAvance(): void {
    const wallet = this.userWallet();
    if (!wallet || !wallet.startsWith('0x')) {
      this.requestError.set('Wallet non connecté. Connectez-vous pour demander une avance.');
      return;
    }
    const amount = this.selectedTokenAmount();
    if (amount <= 0) {
      this.requestError.set('Sélectionnez un nombre de tokens > 0.');
      return;
    }
    if (this.userTier() === 'BRONZE') {
      this.requestError.set('Le modèle A nécessite un tier Silver ou supérieur.');
      return;
    }
    if (this.creditModel() === 'B') {
      this.requestError.set('Le modèle B (PGP) n\'est pas encore implémenté.');
      return;
    }
    this.requestError.set(null);
    this.requestSuccess.set(null);
    this.requestInProgress.set(true);
    this.blockchainApi
      .requestAdvance({
        user: wallet,
        token: this.selectedTokenType(),
        collateralAmount: amount,
        durationDays: this.tierDurationDays(),
      })
      .subscribe({
        next: (res) => {
          this.requestInProgress.set(false);
          if (res.txHash) {
            this.requestSuccess.set({ txHash: res.txHash });
          }
        },
        error: (err) => {
          this.requestInProgress.set(false);
          this.requestError.set(err?.error?.message ?? err?.message ?? 'Erreur lors de la demande.');
        },
      });
  }
}
