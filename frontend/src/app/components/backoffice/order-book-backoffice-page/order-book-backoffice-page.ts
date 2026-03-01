import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { NavbarClient } from '../../frontoffice/navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import type { OrderRow } from '../../../backoffice/models/orderbook.models';

type TabId = 'pending' | 'matched' | 'fallback';

@Component({
  selector: 'app-order-book-backoffice-page',
  imports: [CommonModule, NavbarClient, BackButton, DatePipe],
  templateUrl: './order-book-backoffice-page.html',
  styleUrls: ['./order-book-backoffice-page.css', '../backoffice-theme.css'],
})
export class OrderBookBackofficePage implements OnInit {
  activeTab = signal<TabId>('pending');
  loading = signal<boolean>(false);
  error = signal<string>('');

  pendingOrders = signal<OrderRow[]>([]);
  matchedOrders = signal<OrderRow[]>([]);
  fallbackOrders = signal<OrderRow[]>([]);

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
      if (done >= 3) this.loading.set(false);
    };
    this.api.listPendingOrders().subscribe({
      next: (r) => this.pendingOrders.set(r.orders),
      error: (e) => { this.handleError(e); checkDone(); },
      complete: checkDone,
    });
    this.api.listMatchedOrders().subscribe({
      next: (r) => this.matchedOrders.set(r.orders),
      error: (e) => { this.handleError(e); checkDone(); },
      complete: checkDone,
    });
    this.api.listFallbackAudit().subscribe({
      next: (r) => this.fallbackOrders.set(r.orders),
      error: (e) => { this.handleError(e); checkDone(); },
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

  formatTtl(sec: number | null): string {
    if (sec == null) return '—';
    if (sec < 60) return sec + 's';
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    if (m < 60) return m + 'm ' + s + 's';
    const h = Math.floor(m / 60);
    const rm = m % 60;
    return h + 'h ' + rm + 'm';
  }

  formatAddress(addr: string): string {
    if (!addr || addr.length < 14) return addr;
    return addr.slice(0, 6) + '…' + addr.slice(-4);
  }
}
