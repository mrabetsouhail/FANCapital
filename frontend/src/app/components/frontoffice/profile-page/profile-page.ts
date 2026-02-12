import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { NavbarClient } from '../navbar-client/navbar-client';
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
  imports: [CommonModule, FormsModule, RouterModule, NavbarClient, DatePipe],
  templateUrl: './profile-page.html',
  styleUrl: './profile-page.css',
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
  
  // Progress Tracker
  currentVolume = signal<number>(45000); // TND
  nextLevelVolume = computed(() => {
    const level = this.memberLevel();
    if (level === 'Silver') return 50000; // Gold
    if (level === 'Gold') return 100000; // Platinum
    if (level === 'Platinum') return 200000; // Diamond
    return 0; // Already at max level
  });
  
  progressPercentage = computed(() => {
    if (this.nextLevelVolume() === 0) return 100;
    return Math.min((this.currentVolume() / this.nextLevelVolume()) * 100, 100);
  });
  
  nextLevel = computed(() => {
    const level = this.memberLevel();
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
  /** Montant minimum Cash Wallet pour activer Premium (TND). 1 TND min = versement symbolique. */
  private readonly PREMIUM_MIN_TND = 1;

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
        const items = (res.items ?? []).map((x, idx) => this.mapTx(x.kind, x.fundSymbol, x.amountTnd1e8, x.timestampSec, idx));
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
    if (!p || !p.whitelisted || p.kycLevel === 0) return 'bg-gray-100 text-gray-700 border-gray-200';
    if (p.kycLevel === 1) return 'bg-yellow-100 text-yellow-800 border-yellow-200';
    return 'bg-green-100 text-green-700 border-green-200';
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
    // InvestorRegistry: 0=BRONZE, 1=SILVER, 2=GOLD, 3=DIAMOND, 4=PLATINUM
    if (feeLevel === 3) return 'Diamond';
    if (feeLevel === 4) return 'Platinum';
    if (feeLevel === 2) return 'Gold';
    return 'Silver';
  }

  getMemberLevelColor(): string {
    const level = this.memberLevel();
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
    if (status === 'completed') return 'text-green-600 bg-green-50';
    if (status === 'pending') return 'text-yellow-600 bg-yellow-50';
    return 'text-red-600 bg-red-50';
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

  canActivatePremium(): boolean {
    return this.cashBalanceTnd() >= this.PREMIUM_MIN_TND && !!this.wallet();
  }

  onActivatePremium(): void {
    const w = this.wallet();
    if (!w || !w.startsWith('0x')) return;
    this.premiumActivationLoading.set(true);
    this.premiumActivationError.set('');
    this.authApi.activatePremium().subscribe({
      next: () => {
        this.premiumActivationLoading.set(false);
        this.premiumActivationError.set('');
        this.loadCashBalance(w);
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
