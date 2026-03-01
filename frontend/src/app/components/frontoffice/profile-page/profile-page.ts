import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import type { UserResponse } from '../../../auth/models/auth.models';
import type { InvestorProfileResponse } from '../../../blockchain/models/investor.models';
import type { TxHistoryResponse, TxKind } from '../../../blockchain/models/tx-history.models';

interface Transaction {
  id: number;
  date: Date;
  type: 'achat' | 'vente' | 'depot' | 'retrait';
  tokenType?: 'Atlas' | 'Didon';
  amount: number;
  status: 'completed' | 'pending' | 'failed';
}

type MemberLevel = 'Silver' | 'Gold' | 'Platinum' | 'Diamond';

@Component({
  selector: 'app-profile-page',
  imports: [CommonModule, FormsModule, RouterModule, NavbarClient, BackButton, DatePipe],
  templateUrl: './profile-page.html',
  styleUrls: ['./profile-page.css', '../../../../styles/theme.css'],
})
export class ProfilePage implements OnInit {
  // User Profile (server-truth)
  user = signal<UserResponse | null>(null);
  investor = signal<InvestorProfileResponse | null>(null);
  loading = signal<boolean>(false);
  error = signal<string>('');

  firstName = signal<string>(''); // particulier prenom
  lastName = signal<string>(''); // particulier nom
  companyName = signal<string>(''); // entreprise denomination
  memberLevel = signal<MemberLevel>('Gold');
  email = signal<string>('');
  wallet = signal<string>('');

  // On-chain history (LiquidityPool Bought/Sold + CashToken mint/burn)
  historyLoading = signal<boolean>(false);
  historyError = signal<string>('');
  
  // Progress Tracker - Volume = somme des achats uniquement (dépôts exclus)
  currentVolume = computed(() => {
    return this.allTransactions()
      .filter(t => t.type === 'achat')
      .reduce((sum, t) => sum + t.amount, 0);
  });

  /** Tier dérivé du volume d'achats cumulés (seuils: 7k→Gold, 20k→Platinum, 70k→Diamond). */
  volumeBasedMemberLevel = computed((): MemberLevel => {
    const v = this.currentVolume();
    if (v >= 70000) return 'Diamond';
    if (v >= 20000) return 'Platinum';
    if (v >= 7000) return 'Gold';
    return 'Silver';
  });

  /** Niveau affiché = max(backend, volume) plafonné par le score SCI. Le badge doit refléter le tier autorisé par le score. */
  displayedMemberLevel = computed(() => {
    const backend = this.memberLevel();
    const volumeBased = this.volumeBasedMemberLevel();
    const maxFromVolume = this.higherLevel(backend, volumeBased);
    const score = this.investor()?.score;
    if (score == null || score < 0) return maxFromVolume;
    const maxFromScore = this.scoreToMemberLevel(score);
    return this.lowerLevel(maxFromVolume, maxFromScore);
  });

  nextLevelVolume = computed(() => {
    const level = this.displayedMemberLevel();
    if (level === 'Silver') return 7000; // Gold
    if (level === 'Gold') return 20000; // Platinum
    if (level === 'Platinum') return 70000; // Diamond
    return 0;
  });

  progressPercentage = computed(() => {
    if (this.nextLevelVolume() === 0) return 100;
    return Math.min((this.currentVolume() / this.nextLevelVolume()) * 100, 100);
  });

  nextLevel = computed(() => {
    const level = this.displayedMemberLevel();
    if (level === 'Silver') return 'Gold';
    if (level === 'Gold') return 'Platinum';
    if (level === 'Platinum') return 'Diamond';
    return null;
  });

  remainingVolume = computed(() => {
    if (this.nextLevelVolume() === 0) return 0;
    return Math.max(this.nextLevelVolume() - this.currentVolume(), 0);
  });

  // Transaction History
  allTransactions = signal<Transaction[]>([]);

  // Filters
  filterType = signal<string>('all');
  filterStatus = signal<string>('all');
  filterTokenType = signal<string>('all');
  searchDate = signal<string>('');

  // Filtered transactions
  filteredTransactions = computed(() => {
    let transactions = this.allTransactions();
    
    if (this.filterType() !== 'all') {
      transactions = transactions.filter(t => t.type === this.filterType());
    }
    
    if (this.filterStatus() !== 'all') {
      transactions = transactions.filter(t => t.status === this.filterStatus());
    }
    
    if (this.filterTokenType() !== 'all' && this.filterTokenType() !== 'none') {
      transactions = transactions.filter(t => t.tokenType === this.filterTokenType());
    }
    
    if (this.searchDate()) {
      const searchDateObj = new Date(this.searchDate());
      transactions = transactions.filter(t => {
        const tDate = new Date(t.date);
        return tDate.toDateString() === searchDateObj.toDateString();
      });
    }
    
    return transactions;
  });

  // Security (UI placeholder)
  twoFactorEnabled = signal<boolean>(false);
  currentPassword = signal<string>('');
  newPassword = signal<string>('');
  confirmPassword = signal<string>('');

  // Premium activation (versement via Cash Wallet)
  cashBalanceTnd = signal<number>(0);
  premiumActivationLoading = signal<boolean>(false);
  premiumActivationError = signal<string>('');
  /** Durée choisie: trimestriel, semestriel, annuel */
  subscriptionDuration = signal<'trimestriel' | 'semestriel' | 'annuel'>('annuel');
  /** Tarifs selon tier (TND), -1 = non dispo */
  premiumPrices = signal<{ trimestriel: number; semestriel: number; annuel: number } | null>(null);

  constructor(private authApi: AuthApiService, private blockchainApi: BlockchainApiService) {}

  ngOnInit(): void {
    this.loading.set(true);
    this.error.set('');
    this.authApi.me().subscribe({
      next: (u) => {
        this.user.set(u);
        const displayEmail = u.emailProfessionnel ?? u.email ?? '';
        this.email.set(displayEmail);

        if (u.type === 'ENTREPRISE') {
          this.companyName.set(u.denominationSociale ?? '');
          this.firstName.set(u.prenomGerant ?? '');
          this.lastName.set(u.nomGerant ?? '');
        } else {
          this.firstName.set(u.prenom ?? '');
          this.lastName.set(u.nom ?? '');
        }

        const w = (u.walletAddress as any as string) ?? localStorage.getItem('walletAddress') ?? '';
        this.wallet.set(w);
        if (w && w.startsWith('0x') && w.length === 42) {
          this.loadHistory(w);
          this.loadCashBalance(w);
          this.loadPremiumPrices();
          this.blockchainApi.getInvestorProfile(w).subscribe({
            next: (p) => {
              this.investor.set(p);
              this.memberLevel.set(this.feeLevelToMemberLevel(p.feeLevel));
              this.loading.set(false);
            },
            error: () => {
              // Don't block profile rendering if chain read fails
              this.loading.set(false);
            },
          });
        } else {
          this.loading.set(false);
        }
      },
      error: (e: any) => {
        this.loading.set(false);
        this.error.set(e?.error?.message ?? e?.message ?? 'Erreur chargement profil');
      },
    });
  }

  private loadHistory(userWallet: string) {
    this.historyLoading.set(true);
    this.historyError.set('');
    this.blockchainApi.getTxHistory(userWallet, 200).subscribe({
      next: (res: TxHistoryResponse) => {
        const items = (res.items ?? []).map((x, idx) =>
          this.mapTx(x.kind, x.fundSymbol, x.amountTnd1e8, x.timestampSec, idx)
        );
        this.allTransactions.set(items);
        this.historyLoading.set(false);
      },
      error: (e: any) => {
        this.historyLoading.set(false);
        this.historyError.set(e?.error?.message ?? e?.message ?? 'Erreur chargement historique');
      },
    });
  }

  private mapTx(kind: TxKind, fundSymbol: string, amountTnd1e8: string, timestampSec: number, idx: number): Transaction {
    const date = new Date((timestampSec || Math.floor(Date.now() / 1000)) * 1000);
    const amount = this.from1e8(amountTnd1e8);
    if (kind === 'BUY') return { id: idx + 1, date, type: 'achat', tokenType: this.symbolToTokenType(fundSymbol), amount, status: 'completed' };
    if (kind === 'SELL') return { id: idx + 1, date, type: 'vente', tokenType: this.symbolToTokenType(fundSymbol), amount, status: 'completed' };
    if (kind === 'DEPOSIT') return { id: idx + 1, date, type: 'depot', amount, status: 'completed' };
    return { id: idx + 1, date, type: 'retrait', amount, status: 'completed' };
  }

  private symbolToTokenType(symbol: string | null | undefined): 'Atlas' | 'Didon' {
    const s = (symbol ?? '').toLowerCase();
    if (s.includes('didon')) return 'Didon';
    return 'Atlas';
  }

  private from1e8(v: string): number {
    const n = Number(v);
    if (!Number.isFinite(n)) return 0;
    return n / 1e8;
  }

  kycLabel(): string {
    const p = this.investor();
    if (!p) return this.user()?.kycLevel ? `KYC niveau ${this.user()!.kycLevel}` : 'KYC: inconnu';
    if (!p.whitelisted || p.kycLevel === 0) return 'Non vérifié';
    if (p.kycLevel === 1) return 'Green (N1)';
    if (p.kycLevel === 2) return 'White (N2)';
    return `Niveau ${p.kycLevel}`;
  }

  kycBadgeClass(): string {
    const p = this.investor();
    if (!p || !p.whitelisted || p.kycLevel === 0) return 'bg-white/10 text-white/80 border-white/10';
    if (p.kycLevel === 1) return 'bg-amber-500/20 text-amber-300 border-amber-500/30';
    return 'bg-green-500/20 text-green-300 border-green-500/30';
  }

  investorTierLabel(): string {
    const p = this.investor();
    if (!p) return '-';
    if (p.tier === 0) return 'Bronze';
    if (p.tier === 1) return 'Silver/Gold';
    if (p.tier === 2) return 'Platinum/Diamond';
    return `Tier ${p.tier}`;
  }

  private feeLevelToMemberLevel(feeLevel: number): MemberLevel {
    // InvestorRegistry / SCI v4.5 : 0=BRONZE, 1=SILVER, 2=GOLD, 3=PLATINUM, 4=DIAMOND
    if (feeLevel === 4) return 'Diamond';
    if (feeLevel === 3) return 'Platinum';
    if (feeLevel === 2) return 'Gold';
    if (feeLevel === 1) return 'Silver';
    return 'Silver'; // Bronze → Silver pour l'affichage membre
  }

  private higherLevel(a: MemberLevel, b: MemberLevel): MemberLevel {
    const order: Record<MemberLevel, number> = { Silver: 1, Gold: 2, Platinum: 3, Diamond: 4 };
    return order[a] >= order[b] ? a : b;
  }

  private lowerLevel(a: MemberLevel, b: MemberLevel): MemberLevel {
    const order: Record<MemberLevel, number> = { Silver: 1, Gold: 2, Platinum: 3, Diamond: 4 };
    return order[a] <= order[b] ? a : b;
  }

  /** SCI v4.5 : BRONZE 0-15, SILVER 16-35, GOLD 36-55, PLATINUM 56-84, DIAMOND 85+ */
  private scoreToMemberLevel(score: number): MemberLevel {
    if (score >= 85) return 'Diamond';
    if (score >= 56) return 'Platinum';
    if (score >= 36) return 'Gold';
    if (score >= 16) return 'Silver';
    return 'Silver';
  }

  getMemberLevelColor(): string {
    const level = this.displayedMemberLevel();
    if (level === 'Silver') return '#c0c0c0';
    if (level === 'Gold') return '#ffd700';
    if (level === 'Platinum') return '#e5e4e2';
    return '#b9f2ff'; // Diamond
  }

  getTransactionTypeLabel(type: string): string {
    const labels: { [key: string]: string } = {
      'achat': 'Achat',
      'vente': 'Vente',
      'depot': 'Dépôt',
      'retrait': 'Retrait'
    };
    return labels[type] || type;
  }

  getStatusLabel(status: string): string {
    const labels: { [key: string]: string } = {
      'completed': 'Complété',
      'pending': 'En attente',
      'failed': 'Échoué'
    };
    return labels[status] || status;
  }

  getStatusColor(status: string): string {
    if (status === 'completed') return 'text-green-400 bg-green-500/20';
    if (status === 'pending') return 'text-amber-400 bg-amber-500/20';
    return 'text-red-400 bg-red-500/20';
  }

  toggle2FA() {
    this.twoFactorEnabled.set(!this.twoFactorEnabled());
  }

  onChangePassword() {
    if (this.newPassword() !== this.confirmPassword()) {
      console.log('Les mots de passe ne correspondent pas');
      return;
    }
    if (this.newPassword().length < 8) {
      console.log('Le mot de passe doit contenir au moins 8 caractères');
      return;
    }
    // TODO: Implement password change logic
    console.log('Password change requested');
    this.currentPassword.set('');
    this.newPassword.set('');
    this.confirmPassword.set('');
  }

  canChangePassword(): boolean {
    return !!(
      this.currentPassword().trim() &&
      this.newPassword().trim() &&
      this.newPassword() === this.confirmPassword() &&
      this.newPassword().length >= 8
    );
  }

  getTotalFilteredAmount(): number {
    return this.filteredTransactions().reduce((total, transaction) => total + transaction.amount, 0);
  }

  private loadCashBalance(wallet: string): void {
    this.blockchainApi.getPortfolio(wallet).subscribe({
      next: (p) => {
        const cash = p.cashBalanceTnd ? this.from1e8(p.cashBalanceTnd) : 0;
        this.cashBalanceTnd.set(cash);
      },
      error: () => this.cashBalanceTnd.set(0),
    });
  }

  private loadPremiumPrices(): void {
    this.authApi.getPremiumPrices().subscribe({
      next: (p) => this.premiumPrices.set(p),
      error: () => this.premiumPrices.set(null),
    });
  }

  /** Montant TND de l'abonnement sélectionné (-1 si non dispo). */
  selectedSubscriptionAmount(): number {
    const prices = this.premiumPrices();
    if (!prices) return -1;
    const d = this.subscriptionDuration();
    return prices[d] ?? -1;
  }

  canActivatePremium(): boolean {
    const amount = this.selectedSubscriptionAmount();
    return amount > 0 && this.cashBalanceTnd() >= amount && !!this.wallet();
  }

  /** Rafraîchit le profil et l'historique (score/tier mis à jour après achat/vente). */
  refreshProfile(): void {
    const w = this.wallet();
    if (!w || !w.startsWith('0x')) return;
    this.loadHistory(w);
    this.loadCashBalance(w);
    this.blockchainApi.getInvestorProfile(w).subscribe({
      next: (p) => {
        this.investor.set(p);
        this.memberLevel.set(this.feeLevelToMemberLevel(p.feeLevel));
      },
      error: () => {},
    });
  }

  onActivatePremium(): void {
    const w = this.wallet();
    if (!w || !w.startsWith('0x')) return;
    this.premiumActivationLoading.set(true);
    this.premiumActivationError.set('');
    this.authApi.activatePremium(this.subscriptionDuration()).subscribe({
      next: () => {
        this.premiumActivationLoading.set(false);
        this.premiumActivationError.set('');
        this.loadCashBalance(w);
        this.loadHistory(w); // Rafraîchir l'historique (le retrait apparaît)
        this.blockchainApi.getInvestorProfile(w).subscribe({
          next: (p) => this.investor.set(p),
        });
      },
      error: (e: any) => {
        this.premiumActivationLoading.set(false);
        this.premiumActivationError.set(e?.error?.message ?? e?.message ?? 'Erreur lors de l\'activation');
      },
    });
  }
}
