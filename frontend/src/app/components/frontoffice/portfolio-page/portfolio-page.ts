import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import type { PortfolioResponse, PortfolioPosition } from '../../../blockchain/models/portfolio.models';

@Component({
  selector: 'app-portfolio-page',
  imports: [CommonModule, RouterModule, NavbarClient, BackButton],
  templateUrl: './portfolio-page.html',
  styleUrl: './portfolio-page.css',
})
export class PortfolioPage implements OnInit {
  private readonly api = inject(BlockchainApiService);
  private readonly authApi = inject(AuthApiService);

  userAddress = signal<string>('0x0000000000000000000000000000000000000000');
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  portfolio = signal<PortfolioResponse | null>(null);

  positions = computed<PortfolioPosition[]>(() => this.portfolio()?.positions ?? []);

  ngOnInit() {
    const saved = localStorage.getItem('walletAddress');
    if (saved && saved.startsWith('0x')) this.userAddress.set(saved);

    // If user is authenticated and backend knows the linked/provisioned wallet,
    // use it automatically (no manual copy/paste).
    this.authApi.me().subscribe({
      next: (u) => {
        const addr = (u as any)?.walletAddress as string | undefined;
        if (addr && addr.startsWith('0x') && addr.length === 42) {
          this.userAddress.set(addr);
          localStorage.setItem('walletAddress', addr);
          this.refresh();
        }
      },
      error: () => {},
    });

    this.refresh();
  }

  saveAddress() {
    const user = (this.userAddress() ?? '').trim();
    localStorage.setItem('walletAddress', user);
    this.refresh();
  }

  async connectWallet() {
    const w = (globalThis as any).window;
    const eth = w?.ethereum;
    if (!eth?.request) {
      this.error.set("Wallet non détecté. Installe MetaMask, puis réessaie.");
      return;
    }
    try {
      const accounts: string[] = await eth.request({ method: 'eth_requestAccounts', params: [] });
      const addr = accounts?.[0];
      if (!addr || !addr.startsWith('0x') || addr.length !== 42) {
        this.error.set('Adresse wallet invalide.');
        return;
      }
      this.userAddress.set(addr);
      localStorage.setItem('walletAddress', addr);
      this.refresh();
    } catch (e: any) {
      this.error.set(e?.message ?? 'Connexion wallet annulée.');
    }
  }

  refresh() {
    const user = this.userAddress();
    if (!user || !user.startsWith('0x') || user.length !== 42 || user.includes('...') || user.toLowerCase().includes('votre_adresse')) {
      this.error.set(
        "Adresse wallet invalide. Elle doit être une vraie adresse `0x` de 42 caractères (sans `...`). Clique sur « Connecter wallet » ou colle ton adresse complète."
      );
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.api.getPortfolio(user as any).subscribe({
      next: (p) => this.portfolio.set(p),
      error: (e) => this.error.set(e?.error?.message ?? e?.message ?? 'Erreur API'),
      complete: () => this.loading.set(false),
    });
  }

  from1e8(v: string): number {
    return Number(v) / 1e8;
  }

  /** Retourne true si les tokens bloqués (1e8) sont > 0. */
  hasLocked(v: string | undefined): boolean {
    return (v ? Number(v) : 0) > 0;
  }

  cleanSymbol(symbol: string | null | undefined): string {
    return (symbol ?? '').replaceAll('$', '');
  }
}

