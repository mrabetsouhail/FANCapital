import { Component, OnInit, signal, ViewChild } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import type { Fund } from '../../../blockchain/models/fund.models';
import type { TxRow, TxKind } from '../../../blockchain/models/tx-history.models';

interface Transaction {
  id: number;
  type: 'achat' | 'vente' | 'depot' | 'retrait';
  token?: 'Atlas' | 'Didon';
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
  // Wallets Data (chargés depuis portfolio on-chain)
  tokensAlpha = signal<number>(0);
  tokensBeta = signal<number>(0);
  cashAmount = signal<number>(0);
  creditAmount = signal<number>(0);

  // Profile Data
  memberLevel = signal<string>('Gold');
  isBackofficeAdmin = signal<boolean>(false);

  // Token Alpha (Atlas) - prix VNI on-chain, quantités portfolio
  alphaPrice = signal<number>(0);
  alphaChange = signal<number>(0);
  alphaOwned = signal<number>(0);
  alphaSparkline = signal<number[]>([]);

  // Token Beta (Didon)
  betaPrice = signal<number>(0);
  betaChange = signal<number>(0);
  betaOwned = signal<number>(0);
  betaSparkline = signal<number[]>([]);

  // Funds mapping: Alpha/Beta UI -> Atlas/Didon (blockchain)
  fundAlpha = signal<Fund | null>(null); // Atlas
  fundBeta = signal<Fund | null>(null);  // Didon

  // Historique réel (API getTxHistory)
  recentTransactions = signal<Transaction[]>([]);

  @ViewChild(NavbarClient) navbarClient?: NavbarClient;

  constructor(
    private router: Router, 
    private api: BlockchainApiService,
    private authApi: AuthApiService
  ) {}

  ngOnInit() {
    this.authApi.me().subscribe({
      next: (u) => {
        const role = String((u as any)?.backofficeRole ?? 'NONE').toUpperCase();
        this.isBackofficeAdmin.set(
          !!(u as any)?.isBackofficeAdmin ||
          role === 'ADMIN' ||
          role === 'COMPLIANCE' ||
          role === 'REGULATOR',
        );
        const wallet = (u as any)?.walletAddress ?? localStorage.getItem('walletAddress') ?? '';
        if (wallet && String(wallet).startsWith('0x') && String(wallet).length === 42) {
          this.loadPortfolio(wallet as string);
          this.loadTxHistory(wallet as string);
        }
      },
      error: () => {},
    });

    // Refresh wallet balance when navigating to this page (e.g., after transaction)
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        setTimeout(() => {
          this.navbarClient?.refreshWalletBalance();
          const wallet = localStorage.getItem('walletAddress');
          if (wallet && wallet.startsWith('0x')) {
            this.loadPortfolio(wallet);
            this.loadTxHistory(wallet);
          }
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

        const wallet = localStorage.getItem('walletAddress');
        if (wallet && wallet.startsWith('0x')) {
          this.loadPortfolio(wallet);
          this.loadTxHistory(wallet);
        }

        setTimeout(() => this.navbarClient?.refreshWalletBalance(), 500);
      },
      error: () => {},
    });
  }

  private loadPortfolio(wallet: string): void {
    this.api.getPortfolio(wallet).subscribe({
      next: (p) => {
        this.cashAmount.set(Number(p.cashBalanceTnd) / 1e8);
        // creditAmount = dette AST (avance créditée), pas creditLine (plafond)
        this.creditAmount.set(Number(p.creditDebtTnd ?? 0) / 1e8);

        for (const pos of p.positions) {
          const tokens = Number(pos.balanceTokens) / 1e8;
          const name = (pos.name ?? '').toLowerCase();
          const isAtlas = name.includes('atlas');
          const isDidon = name.includes('didon');
          if (isAtlas) {
            this.alphaOwned.set(tokens);
            this.tokensAlpha.set(tokens);
            const vni = Number(pos.vni) / 1e8;
            if (vni > 0) this.alphaPrice.set(vni);
          } else if (isDidon) {
            this.betaOwned.set(tokens);
            this.tokensBeta.set(tokens);
            const vni = Number(pos.vni) / 1e8;
            if (vni > 0) this.betaPrice.set(vni);
          }
        }
      },
      error: () => {},
    });
  }

  private loadTxHistory(wallet: string): void {
    this.api.getTxHistory(wallet, 50).subscribe({
      next: (r) => {
        const tx: Transaction[] = r.items.map((row, i) => {
          const type = this.mapTxKind(row.kind);
          const token = this.getTokenLabel(row);
          const date = new Date(row.timestampSec * 1000);
          let amount = 0;
          let price: number | undefined;
          if (row.kind === 'BUY' || row.kind === 'SELL') {
            amount = Number(row.amountToken1e8) / 1e8;
            const p = Number(row.priceClient1e8) / 1e8;
            price = p > 0 ? p : undefined;
          } else {
            amount = Number(row.amountTnd1e8) / 1e8;
          }
          return { id: i + 1, type, token, amount, price, date };
        });
        this.recentTransactions.set(tx);
      },
      error: () => {},
    });
  }

  private mapTxKind(kind: TxKind): 'achat' | 'vente' | 'depot' | 'retrait' {
    const map: Record<TxKind, 'achat' | 'vente' | 'depot' | 'retrait'> = {
      BUY: 'achat', SELL: 'vente', DEPOSIT: 'depot', WITHDRAW: 'retrait',
    };
    return map[kind] ?? 'depot';
  }

  private getTokenLabel(row: TxRow): 'Atlas' | 'Didon' | undefined {
    if (row.kind !== 'BUY' && row.kind !== 'SELL') return undefined;
    const name = (row.fundName ?? row.fundSymbol ?? '').toLowerCase();
    if (name.includes('atlas')) return 'Atlas';
    if (name.includes('didon')) return 'Didon';
    return undefined;
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

  /** Calcule la hauteur en % pour un point du sparkline (min=0%, max=100%). */
  getSparklineHeight(points: number[], point: number): number {
    if (!points.length) return 0;
    const min = Math.min(...points);
    const max = Math.max(...points);
    if (min === max) return 100;
    return ((point - min) / (max - min)) * 100;
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
