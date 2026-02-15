import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { NavbarClient } from '../../frontoffice/navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import type { LockedAssetRow, RepaymentTrackingRow } from '../../../backoffice/models/escrow.models';

type TabId = 'locked' | 'repayments';

@Component({
  selector: 'app-escrow-backoffice-page',
  imports: [CommonModule, NavbarClient, BackButton],
  templateUrl: './escrow-backoffice-page.html',
  styleUrl: './escrow-backoffice-page.css',
})
export class EscrowBackofficePage implements OnInit {
  activeTab = signal<TabId>('locked');
  loading = signal<boolean>(false);
  error = signal<string>('');

  lockedAssets = signal<LockedAssetRow[]>([]);
  repaymentTracking = signal<RepaymentTrackingRow[]>([]);

  constructor(private api: BackofficeApiService) {}

  ngOnInit() {
    this.refresh();
  }

  setTab(tab: TabId) {
    this.activeTab.set(tab);
  }

  refresh() {
    this.loading.set(true);
    this.error.set('');
    let done = 0;
    const checkDone = () => {
      done++;
      if (done >= 2) this.loading.set(false);
    };
    this.api.getLockedAssets().subscribe({
      next: (r) => this.lockedAssets.set(r.rows),
      error: (e) => {
        this.handleError(e);
        checkDone();
      },
      complete: checkDone,
    });
    this.api.getRepaymentTracking().subscribe({
      next: (r) => this.repaymentTracking.set(r.rows),
      error: (e) => {
        this.handleError(e);
        checkDone();
      },
      complete: checkDone,
    });
  }

  private handleError(err: unknown) {
    const msg =
      err instanceof HttpErrorResponse
        ? (err.error?.message ?? err.statusText ?? err.message)
        : 'Erreur chargement';
    this.error.set(String(msg));
  }

  from1e8(v: string): number {
    return Number(v) / 1e8;
  }

  formatAddress(addr: string): string {
    if (!addr || addr === '—' || addr.length < 14) return addr;
    return addr.slice(0, 6) + '…' + addr.slice(-4);
  }

  statusLabel(status: number): string {
    if (status === 0) return 'Actif';
    if (status === 1) return 'Remboursé';
    return 'Statut ' + status;
  }

  formatStartAt(ts: number): string {
    if (!ts) return '—';
    return new Date(ts * 1000).toLocaleString('fr-FR');
  }
}
