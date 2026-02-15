import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import { BackButton } from '../../shared/back-button/back-button';
import type { FiscalDashboardResponse } from '../../../backoffice/models/fiscal.models';

@Component({
  selector: 'app-fiscal-dashboard-page',
  imports: [CommonModule, FormsModule, BackButton],
  templateUrl: './fiscal-dashboard-page.html',
  styleUrl: './fiscal-dashboard-page.css',
})
export class FiscalDashboardPage implements OnInit {
  data = signal<FiscalDashboardResponse | null>(null);
  loading = signal<boolean>(false);
  error = signal<string>('');

  withdrawAmount = signal<string>('0');
  withdrawResult = signal<string>('');

  constructor(private api: BackofficeApiService) {}

  ngOnInit() {
    this.refresh();
  }

  refresh() {
    this.loading.set(true);
    this.error.set('');
    this.withdrawResult.set('');
    this.api.getFiscalSummary().subscribe({
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

  onWithdraw() {
    this.error.set('');
    this.withdrawResult.set('');
    const amount = this.withdrawAmount().trim();
    if (!/^\d{1,78}$/.test(amount)) {
      this.error.set('Montant invalide');
      return;
    }
    this.api.withdrawToFisc({ amount }).subscribe({
      next: (res) => {
        this.withdrawResult.set(`Tx: ${res.txHash ?? ''} (${res.status})`);
        this.refresh();
      },
      error: (err: unknown) => {
        const msg =
          err instanceof HttpErrorResponse ? (err.error?.message ?? err.message) : 'Erreur transfert';
        this.error.set(String(msg));
      },
    });
  }
}

