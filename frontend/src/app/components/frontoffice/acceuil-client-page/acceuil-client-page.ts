import { Component, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { NavbarClient } from '../navbar-client/navbar-client';

interface Transaction {
  id: number;
  type: 'achat' | 'vente' | 'depot' | 'retrait';
  token?: 'Alpha' | 'Beta';
  amount: number;
  price?: number;
  date: Date;
}

@Component({
  selector: 'app-acceuil-client-page',
  imports: [CommonModule, NavbarClient],
  templateUrl: './acceuil-client-page.html',
  styleUrl: './acceuil-client-page.css',
})
export class AcceuilClientPage {
  // Wallets Data
  tokensAlpha = signal<number>(150);
  tokensBeta = signal<number>(75);
  cashAmount = signal<number>(5000.00);
  creditAmount = signal<number>(10000.00);
  
  // Profile Data
  memberLevel = signal<string>('Gold');

  // Token Alpha Data
  alphaPrice = signal<number>(125.50);
  alphaChange = signal<number>(3.5);
  alphaOwned = signal<number>(150);
  alphaSparkline = signal<number[]>([120, 122, 121, 123, 125, 124, 125.5]);

  // Token Beta Data
  betaPrice = signal<number>(85.25);
  betaChange = signal<number>(-1.2);
  betaOwned = signal<number>(75);
  betaSparkline = signal<number[]>([86, 85.5, 85, 85.5, 85.2, 85.3, 85.25]);

  // Recent Transactions
  recentTransactions = signal<Transaction[]>([
    { id: 1, type: 'achat', token: 'Alpha', amount: 10, price: 124.50, date: new Date(Date.now() - 2 * 60 * 60 * 1000) },
    { id: 2, type: 'vente', token: 'Beta', amount: 5, price: 86.00, date: new Date(Date.now() - 5 * 60 * 60 * 1000) },
    { id: 3, type: 'depot', amount: 2000, date: new Date(Date.now() - 24 * 60 * 60 * 1000) },
    { id: 4, type: 'achat', token: 'Alpha', amount: 15, price: 123.00, date: new Date(Date.now() - 48 * 60 * 60 * 1000) },
  ]);

  constructor(private router: Router) {}

  onDeposit() {
   
  }

  onWithdraw() {
   ;
  }

  onProfile() {
   
  }

  onBuyAlpha() {
    this.router.navigate(['/passer-ordre'], { queryParams: { token: 'Alpha', action: 'buy' } });
  }

  onSellAlpha() {
    this.router.navigate(['/passer-ordre'], { queryParams: { token: 'Alpha', action: 'sell' } });
  }

  onBuyBeta() {
    this.router.navigate(['/passer-ordre'], { queryParams: { token: 'Beta', action: 'buy' } });
  }

  onSellBeta() {
    this.router.navigate(['/passer-ordre'], { queryParams: { token: 'Beta', action: 'sell' } });
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

  getTransactionIcon(type: string): string {
    if (type === 'achat') return 'M12 4v16m8-8H4';
    if (type === 'vente') return 'M20 12H4';
    if (type === 'depot') return 'M12 4v16m8-8H4';
    return 'M20 12H4';
  }
}
