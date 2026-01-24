import { Component, signal, computed } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';

interface Transaction {
  id: number;
  date: Date;
  type: 'achat' | 'vente' | 'depot' | 'retrait';
  tokenType?: 'Alpha' | 'Beta';
  amount: number;
  status: 'completed' | 'pending' | 'failed';
}

type MemberLevel = 'Silver' | 'Gold' | 'Platinum' | 'Diamond';

@Component({
  selector: 'app-profile-page',
  imports: [CommonModule, FormsModule, NavbarClient, DatePipe],
  templateUrl: './profile-page.html',
  styleUrl: './profile-page.css',
})
export class ProfilePage {
  // User Profile
  firstName = signal<string>('Ahmed');
  lastName = signal<string>('Ben Ali');
  memberLevel = signal<MemberLevel>('Gold');
  email = signal<string>('ahmed.benali@example.com');
  
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
  allTransactions = signal<Transaction[]>([
    { id: 1, date: new Date(Date.now() - 2 * 60 * 60 * 1000), type: 'achat', tokenType: 'Alpha', amount: 1250, status: 'completed' },
    { id: 2, date: new Date(Date.now() - 5 * 60 * 60 * 1000), type: 'vente', tokenType: 'Beta', amount: 850, status: 'completed' },
    { id: 3, date: new Date(Date.now() - 24 * 60 * 60 * 1000), type: 'depot', amount: 5000, status: 'completed' },
    { id: 4, date: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000), type: 'achat', tokenType: 'Alpha', amount: 2000, status: 'completed' },
    { id: 5, date: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000), type: 'retrait', amount: 3000, status: 'completed' },
    { id: 6, date: new Date(Date.now() - 4 * 24 * 60 * 60 * 1000), type: 'achat', tokenType: 'Beta', amount: 1500, status: 'pending' },
    { id: 7, date: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000), type: 'vente', tokenType: 'Alpha', amount: 1000, status: 'failed' },
  ]);

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

  // Security
  twoFactorEnabled = signal<boolean>(false);
  currentPassword = signal<string>('');
  newPassword = signal<string>('');
  confirmPassword = signal<string>('');

  constructor() {}

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
}
