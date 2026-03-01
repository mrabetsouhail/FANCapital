import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { NavbarClient } from '../../frontoffice/navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import type { SubscriptionsMonitorResponse } from '../../../backoffice/models/subscription.models';

type TabId = 'monitor' | 'expiring';

@Component({
  selector: 'app-subscriptions-backoffice-page',
  imports: [CommonModule, NavbarClient, BackButton, DatePipe],
  templateUrl: './subscriptions-backoffice-page.html',
  styleUrls: ['./subscriptions-backoffice-page.css', '../backoffice-theme.css'],
})
export class SubscriptionsBackofficePage implements OnInit {
  activeTab = signal<TabId>('monitor');
  loading = signal<boolean>(false);
  error = signal<string>('');

  monitorData = signal<SubscriptionsMonitorResponse | null>(null);
  expiringSubscriptions = signal<{ subscriptions: Array<{ userId: string; email: string; fullName: string; expiresAt: string; daysRemaining: number; duration: string }>; totalCount: number }>({ subscriptions: [], totalCount: 0 });

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
    this.api.getSubscriptionsMonitor().subscribe({
      next: (r) => this.monitorData.set(r),
      error: (e) => this.handleError(e),
      complete: () => this.loading.set(false),
    });
    this.api.getExpiringSubscriptions().subscribe({
      next: (r) => this.expiringSubscriptions.set(r),
      error: (e) => this.handleError(e),
    });
  }

  private handleError(err: unknown) {
    const msg =
      err instanceof HttpErrorResponse
        ? (err.error?.message ?? err.statusText ?? err.message)
        : 'Erreur chargement';
    this.error.set(String(msg));
    this.loading.set(false);
  }

  formatAddress(addr: string): string {
    if (!addr || addr === '—' || addr.length < 14) return addr;
    return addr.slice(0, 6) + '…' + addr.slice(-4);
  }

  formatDays(d: number | null): string {
    if (d == null) return '—';
    if (d < 0) return 'Expiré (' + (-d) + 'j)';
    if (d === 0) return "Aujourd'hui";
    if (d === 1) return '1 jour';
    return d + ' jours';
  }
}
