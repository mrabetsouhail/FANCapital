import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import type { KycUserRow, KycLevel } from '../../../backoffice/models/kyc.models';

@Component({
  selector: 'app-kyc-dashboard-page',
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './kyc-dashboard-page.html',
  styleUrl: './kyc-dashboard-page.css',
})
export class KycDashboardPage implements OnInit {
  rows = signal<KycUserRow[]>([]);
  loading = signal<boolean>(false);
  error = signal<string>('');
  actionMsg = signal<string>('');

  q = signal<string>('');

  constructor(private api: BackofficeApiService) {}

  ngOnInit() {
    this.refresh();
  }

  refresh() {
    this.loading.set(true);
    this.error.set('');
    this.actionMsg.set('');
    this.api.listKycUsers(this.q()).subscribe({
      next: (res) => {
        this.rows.set(res ?? []);
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

  badge(level: number): { label: string; cls: string } {
    if (level >= 2) return { label: 'KYC2 (White)', cls: 'bg-green-100 text-green-700 border-green-200' };
    if (level === 1) return { label: 'KYC1 (Green)', cls: 'bg-yellow-100 text-yellow-800 border-yellow-200' };
    return { label: 'KYC0 (None)', cls: 'bg-gray-100 text-gray-700 border-gray-200' };
  }

  canSet(row: KycUserRow, target: KycLevel): boolean {
    return Number(row.kycLevel ?? 0) !== target;
  }

  setLevel(row: KycUserRow, target: KycLevel) {
    this.error.set('');
    this.actionMsg.set('');
    this.loading.set(true);
    this.api.setKycLevel({ userId: row.id, level: target }).subscribe({
      next: () => {
        this.actionMsg.set(`KYC mis à jour: ${row.email} → niveau ${target}`);
        this.refresh();
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const msg =
          err instanceof HttpErrorResponse ? (err.error?.message ?? err.message) : 'Erreur validation KYC';
        this.error.set(String(msg));
      },
    });
  }
}

