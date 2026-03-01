import { Component, signal, computed, OnInit, effect, ViewChild } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import { NotificationApiService } from '../../../auth/services/notification-api.service';
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
  status: 'pending' | 'completed' | 'overdue';
}

const FEE_TO_TIER: Tier[] = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND'];

@Component({
  selector: 'app-avance-sur-titre-page',
  imports: [CommonModule, FormsModule, NavbarClient, BackButton, DatePipe],
  templateUrl: './avance-sur-titre-page.html',
  styleUrls: ['./avance-sur-titre-page.css', '../../../../styles/theme.css'],
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

  /** Calendrier de remboursement calculé (Spec v4.7, CREDIT_LOMBARD).
   * Modèle A: Coupons mensuels + échéance principal. Modèle B (PGP): uniquement échéance (clôture à maturité).
   * Utilise l'avance active si présente (dates réelles), sinon simulation. */
  repaymentSchedule = computed<RepaymentSchedule[]>(() => {
    const loan = this.activeLoan();
    const principal = loan ? loan.principalTnd : this.loanAmount();
    const durationDays = loan ? loan.durationDays : this.tierDurationDays();
    const isPgp = loan?.model === 'B' || this.creditModel() === 'B';
    if (principal <= 0 || durationDays <= 0) return [];
    const now = Date.now();
    const startMs = loan ? loan.startAt * 1000 : now;
    const start = new Date(startMs);
    const MS_PER_DAY = 24 * 60 * 60 * 1000;
    const items: RepaymentSchedule[] = [];
    let id = 0;
    if (!isPgp) {
      const rate = this.interestRate() / 100;
      const totalInterest = principal * rate * (durationDays / 365);
      const nbMonths = Math.max(1, Math.floor(durationDays / 30));
      const couponAmount = totalInterest / nbMonths;
      for (let i = 1; i <= nbMonths; i++) {
        const dueDate = new Date(start.getTime() + i * 30 * MS_PER_DAY);
        if (dueDate.getTime() <= start.getTime() + durationDays * MS_PER_DAY) {
          const status: RepaymentSchedule['status'] = dueDate.getTime() < now ? 'completed' : 'pending';
          items.push({ id: ++id, date: dueDate, amount: Math.round(couponAmount * 100) / 100, type: 'coupon', status });
        }
      }
    }
    const maturityDate = new Date(start.getTime() + durationDays * MS_PER_DAY);
    let echeanceStatus: RepaymentSchedule['status'] = 'pending';
    if (loan) {
      if (principal <= 0) echeanceStatus = 'completed';
      else if (maturityDate.getTime() < now) echeanceStatus = 'overdue';
    }
    items.push({ id: ++id, date: maturityDate, amount: Math.round(principal * 100) / 100, type: 'echeance', status: echeanceStatus });
    return items;
  });

  /** Date d'échéance (dernier élément du calendrier). */
  maturityDate = computed(() => {
    const s = this.repaymentSchedule();
    const echeance = s.find(i => i.type === 'echeance');
    return echeance ? echeance.date : null;
  });

  /** Jours restants jusqu'à l'échéance (négatif si dépassée). */
  daysUntilMaturity = computed(() => {
    const m = this.maturityDate();
    if (!m) return null;
    const diff = m.getTime() - Date.now();
    return Math.floor(diff / (24 * 60 * 60 * 1000));
  });

  /** Prochaine échéance à payer (premier item en attente). */
  nextDuePayment = computed(() => {
    return this.repaymentSchedule().find(i => i.status === 'pending' || i.status === 'overdue') ?? null;
  });

  /** Montant intérêts total (pour détail du remboursement). */
  totalInterestAmount = computed(() => {
    const principal = this.activeLoan()?.principalTnd ?? this.loanAmount();
    const durationDays = this.activeLoan()?.durationDays ?? this.tierDurationDays();
    const rate = this.interestRate() / 100;
    if (principal <= 0 || durationDays <= 0) return 0;
    return Math.round(principal * rate * (durationDays / 365) * 100) / 100;
  });

  /** Montant principal (pour détail du remboursement). */
  totalPrincipalAmount = computed(() => {
    const loan = this.activeLoan();
    const principal = loan ? loan.principalTnd : this.loanAmount();
    return Math.round(principal * 100) / 100;
  });

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

  /** PGP (Modèle B) : mettre à true quand l'implémentation backend est prête. */
  pgpFullyImplemented = signal<boolean>(false);

  tierLoading = signal<boolean>(true);
  tokensLoading = signal<boolean>(true);
  requestInProgress = signal<boolean>(false);
  requestError = signal<string | null>(null);
  requestSuccess = signal<{ txHash: string } | null>(null);
  userWallet = signal<string>('');
  activeLoan = signal<{ principalTnd: number; startAt: number; durationDays: number; model?: 'A' | 'B' } | null>(null);

  /** Déclenche une animation discrète sur le Prix de liquidation quand le montant change. */
  liquidationPriceJustUpdated = signal<boolean>(false);
  private liquidationPulseTimeout: ReturnType<typeof setTimeout> | null = null;

  /** Points d'arrêt visuels pour le slider (montants ronds). */
  sliderStep = computed(() => {
    const max = this.maxCreditAmount();
    if (max <= 0) return 10;
    if (max <= 500) return 50;
    if (max <= 2000) return 100;
    if (max <= 10000) return 500;
    return 1000;
  });

  sliderTicks = computed(() => {
    const max = this.maxCreditAmount();
    if (max <= 0) return [];
    const ticks: { value: number; percent: number; label: string }[] = [{ value: 0, percent: 0, label: '0' }];
    for (const p of [0.25, 0.5, 0.75]) {
      const v = max * p;
      const label = v >= 1000 ? (v / 1000).toFixed(1) + 'k' : Math.round(v).toString();
      ticks.push({ value: v, percent: p * 100, label });
    }
    ticks.push({ value: max, percent: 100, label: max >= 1000 ? (max / 1000).toFixed(1) + 'k' : Math.round(max).toString() });
    return ticks;
  });

  /** Classe CSS pour l'affichage du tier (gradient privilège pour Platinum/Diamond). */
  tierDisplayClass = computed(() => {
    const t = this.userTier();
    if (t === 'PLATINUM' || t === 'DIAMOND') {
      return 'tier-premium border-amber-500/30 bg-gradient-to-r from-amber-500/10 to-blue-500/10';
    }
    return 'border border-white/10 bg-white/5';
  });

  @ViewChild(NavbarClient) navbarClient?: NavbarClient;

  private marginAlertSent = false;

  constructor(
    private authApi: AuthApiService,
    private blockchainApi: BlockchainApiService,
    private notificationApi: NotificationApiService
  ) {
    this.selectedTokenAmount.set(0);
    effect(() => {
      const ltv = this.ltvVariablePercent();
      if (ltv >= 75 && !this.marginAlertSent && this.loanAmount() > 0 && this.selectedTokenAmount() > 0) {
        this.marginAlertSent = true;
        this.notificationApi.reportMarginAlert(ltv).subscribe({ error: () => {} });
      }
    });
  }

  ngOnInit(): void {
    this.authApi.me().subscribe({
      next: (u) => {
        const w = (u as any)?.walletAddress ?? localStorage.getItem('walletAddress') ?? '';
        this.userWallet.set(w ?? '');
        if (w && w.startsWith('0x')) this.loadActiveLoan(w);
      },
    });
    this.loadUserTier();
    this.loadTokensData();
  }

  private loadActiveLoan(wallet: string): void {
    this.blockchainApi.getActiveAdvance(wallet).subscribe({
      next: (loan) => {
        if (loan) {
          this.activeLoan.set({
            principalTnd: Number(loan.principalTnd) / 1e8,
            startAt: loan.startAt,
            durationDays: loan.durationDays,
            model: loan.model,
          });
          // Rafraîchir la navbar pour afficher le montant dans Credit Wallet (pas Cash)
          this.navbarClient?.refreshWalletBalance();
        } else {
          this.activeLoan.set(null);
        }
      },
      error: () => this.activeLoan.set(null),
    });
  }

  /** Rafraîchir l'avance active (après activation). Met à jour la navbar Credit/Cash. */
  refreshActiveLoan(): void {
    const w = this.userWallet();
    if (w && w.startsWith('0x')) {
      this.loadActiveLoan(w);
      this.navbarClient?.refreshWalletBalance();
    }
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
            const atlasBal = atlas ? this.from1e8(atlas.balanceTokens) : 0;
            const atlasLocked = atlas?.lockedTokens1e8 ? this.from1e8(atlas.lockedTokens1e8) : 0;
            const didonBal = didon ? this.from1e8(didon.balanceTokens) : 0;
            const didonLocked = didon?.lockedTokens1e8 ? this.from1e8(didon.lockedTokens1e8) : 0;
            const tokens: { type: 'Atlas' | 'Didon'; amount: number; price: number }[] = [
              { type: 'Atlas', amount: Math.max(0, atlasBal - atlasLocked), price: atlas ? this.from1e8(atlas.vni) : 0 },
              { type: 'Didon', amount: Math.max(0, didonBal - didonLocked), price: didon ? this.from1e8(didon.vni) : 0 },
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
    this.triggerLiquidationPricePulse();
  }

  onLoanAmountChange() {
    if (this.loanAmount() > this.maxCreditAmount()) {
      this.loanAmount.set(this.maxCreditAmount());
    }
    this.triggerLiquidationPricePulse();
  }

  private triggerLiquidationPricePulse(): void {
    if (this.liquidationPulseTimeout) clearTimeout(this.liquidationPulseTimeout);
    this.liquidationPriceJustUpdated.set(true);
    this.liquidationPulseTimeout = setTimeout(() => {
      this.liquidationPriceJustUpdated.set(false);
      this.liquidationPulseTimeout = null;
    }, 600);
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

  getStatusLabel(status: RepaymentSchedule['status']): string {
    if (status === 'completed') return 'Complété';
    if (status === 'overdue') return 'En retard';
    return 'En attente';
  }

  /** Indique si cet item est la prochaine échéance à payer. */
  isNextDue(item: RepaymentSchedule): boolean {
    const next = this.nextDuePayment();
    return next !== null && item.id === next.id;
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
    if (this.creditModel() === 'A' && this.userTier() === 'BRONZE') {
      this.requestError.set('Le modèle A nécessite un tier Silver ou supérieur.');
      return;
    }
            if (this.creditModel() === 'B') {
      if (!this.pgpFullyImplemented()) {
        this.requestError.set('Le modèle PGP (B) sera bientôt disponible.');
        return;
      }
      if (this.userTier() === 'BRONZE' || this.userTier() === 'SILVER' || this.userTier() === 'GOLD') {
        this.requestError.set('Le modèle PGP (B) nécessite un tier Platinum ou Diamond.');
        return;
      }
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
        model: this.creditModel(),
      })
      .subscribe({
        next: (res) => {
          this.requestInProgress.set(false);
          if (res.txHash) {
            this.requestSuccess.set({ txHash: res.txHash });
            // Rafraîchir le calendrier après activation (sous 1-2 min)
            setTimeout(() => this.refreshActiveLoan(), 90_000);
          }
        },
        error: (err) => {
          this.requestInProgress.set(false);
          this.requestError.set(err?.error?.message ?? err?.message ?? 'Erreur lors de la demande.');
        },
      });
  }
}
