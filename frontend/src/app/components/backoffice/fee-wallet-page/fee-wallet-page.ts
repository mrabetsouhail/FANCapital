import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import type { FeeWalletDashboard } from '../../../backoffice/models/fee-wallet.models';

@Component({
  selector: 'app-fee-wallet-page',
  imports: [CommonModule],
  templateUrl: './fee-wallet-page.html',
  styleUrl: './fee-wallet-page.css',
})
export class FeeWalletPage implements OnInit {
  data = signal<FeeWalletDashboard | null>(null);
  loading = signal<boolean>(false);
  error = signal<string>('');

  constructor(private api: BackofficeApiService) {}

  ngOnInit() {
    this.refresh();
  }

  refresh() {
    this.loading.set(true);
    this.error.set('');
    this.api.getFeeWallet().subscribe({
      next: (res) => {
        this.data.set(res);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const msg =
          err instanceof HttpErrorResponse ? (err.error?.message ?? err.message) : 'Erreur chargement';
        this.error.set(String(msg));
      },
    });
  }

  from1e8(v: string): number {
    return Number(v) / 1e8;
  }

  // Computed totals for display
  totalFees = computed(() => {
    const d = this.data();
    if (!d) return 0;
    return d.feesByFund.reduce((sum, f) => sum + this.from1e8(f.totalFeesTnd), 0);
  });

  totalFeesBase = computed(() => {
    const d = this.data();
    if (!d) return 0;
    return d.feesByFund.reduce((sum, f) => sum + this.from1e8(f.feesBaseTnd), 0);
  });

  totalVat = computed(() => {
    const d = this.data();
    if (!d) return 0;
    return d.feesByFund.reduce((sum, f) => sum + this.from1e8(f.vatTnd), 0);
  });

  totalTransactions = computed(() => {
    const d = this.data();
    if (!d) return 0;
    return d.feesByFund.reduce((sum, f) => sum + f.transactionCount, 0);
  });
}
