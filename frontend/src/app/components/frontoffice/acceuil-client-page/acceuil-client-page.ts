import { Component, OnInit, signal, ViewChild } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import type { Fund } from '../../../blockchain/models/fund.models';

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
  imports: [CommonModule, NavbarClient, RouterModule],
  templateUrl: './acceuil-client-page.html',
  styleUrl: './acceuil-client-page.css',
})
export class AcceuilClientPage implements OnInit {
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

  // Funds mapping: legacy Alpha/Beta UI -> Atlas/Didon (blockchain)
  fundAlpha = signal<Fund | null>(null); // Atlas
  fundBeta = signal<Fund | null>(null);  // Didon

  // Recent Transactions
  recentTransactions = signal<Transaction[]>([
    { id: 1, type: 'achat', token: 'Alpha', amount: 10, price: 124.50, date: new Date(Date.now() - 2 * 60 * 60 * 1000) },
    { id: 2, type: 'vente', token: 'Beta', amount: 5, price: 86.00, date: new Date(Date.now() - 5 * 60 * 60 * 1000) },
    { id: 3, type: 'depot', amount: 2000, date: new Date(Date.now() - 24 * 60 * 60 * 1000) },
    { id: 4, type: 'achat', token: 'Alpha', amount: 15, price: 123.00, date: new Date(Date.now() - 48 * 60 * 60 * 1000) },
  ]);

  @ViewChild(NavbarClient) navbarClient?: NavbarClient;

  constructor(
    private router: Router, 
    private api: BlockchainApiService,
    private authApi: AuthApiService
  ) {}

  ngOnInit() {
    // Refresh wallet balance when navigating to this page (e.g., after transaction)
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        // Small delay to ensure navbar is rendered
        setTimeout(() => {
          this.navbarClient?.refreshWalletBalance();
        }, 300);
      });

    // Load funds then fetch VNIs (Atlas/Didon)
    this.api.listFunds().subscribe({
      next: (res) => {
        const atlas = res.funds.find((f) => f.name.toLowerCase().includes('atlas')) ?? res.funds[0] ?? null;
        const didon = res.funds.find((f) => f.name.toLowerCase().includes('didon')) ?? res.funds[1] ?? null;
        this.fundAlpha.set(atlas ?? null);
        this.fundBeta.set(didon ?? null);
        this.refreshVni();
        
        // Also refresh wallet balance on initial load
        setTimeout(() => {
          this.navbarClient?.refreshWalletBalance();
        }, 500);
      },
      error: () => {
        // keep UI usable even if API not available
      },
    });
  }

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

  private refreshVni() {
    const atlas = this.fundAlpha();
    const didon = this.fundBeta();

    if (atlas?.token) {
      const prev = this.alphaPrice();
      this.api.getVni(atlas.token).subscribe({
        next: (v) => {
          const price = Number(v.vni) / 1e8;
          this.alphaPrice.set(price);
          this.alphaChange.set(prev > 0 ? Number((((price - prev) / prev) * 100).toFixed(2)) : 0);
          this.alphaSparkline.set([...this.alphaSparkline().slice(-6), price]);
        },
        error: () => {},
      });
    }

    if (didon?.token) {
      const prev = this.betaPrice();
      this.api.getVni(didon.token).subscribe({
        next: (v) => {
          const price = Number(v.vni) / 1e8;
          this.betaPrice.set(price);
          this.betaChange.set(prev > 0 ? Number((((price - prev) / prev) * 100).toFixed(2)) : 0);
          this.betaSparkline.set([...this.betaSparkline().slice(-6), price]);
        },
        error: () => {},
      });
    }
  }
}
