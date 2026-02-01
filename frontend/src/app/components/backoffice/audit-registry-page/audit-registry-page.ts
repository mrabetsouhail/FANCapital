import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import { NavbarClient } from '../../frontoffice/navbar-client/navbar-client';
import type { AuditLogRow, AuditRegistryRow } from '../../../backoffice/models/audit.models';

@Component({
  selector: 'app-audit-registry-page',
  imports: [CommonModule, FormsModule, NavbarClient],
  templateUrl: './audit-registry-page.html',
  styleUrl: './audit-registry-page.css',
})
export class AuditRegistryPage implements OnInit {
  loading = signal<boolean>(false);
  error = signal<string>('');
  q = signal<string>('');

  rows = signal<AuditRegistryRow[]>([]);
  logs = signal<AuditLogRow[]>([]);

  canExport = signal<boolean>(false);

  filtered = computed(() => {
    const needle = this.q().trim().toLowerCase();
    const r = this.rows();
    if (!needle) return r;
    return r.filter((x) => {
      const hay = [
        x.userId,
        x.email,
        x.fullNameOrCompany ?? '',
        x.cinOrPassportOrFiscalId ?? '',
        x.walletAddress ?? '',
      ]
        .join(' ')
        .toLowerCase();
      return hay.includes(needle);
    });
  });

  constructor(private backofficeApi: BackofficeApiService, private authApi: AuthApiService) {}

  ngOnInit(): void {
    this.loading.set(true);
    this.error.set('');

    this.authApi.me().subscribe({
      next: (u: any) => {
        const role = String(u?.backofficeRole ?? 'NONE').toUpperCase();
        this.canExport.set(!!u?.isBackofficeAdmin || role === 'ADMIN' || role === 'COMPLIANCE');
      },
      error: () => {},
    });

    this.refresh();
  }

  refresh() {
    this.loading.set(true);
    this.error.set('');
    const q = this.q().trim();
    this.backofficeApi.getAuditRegistry(q || undefined).subscribe({
      next: (res) => {
        this.rows.set(res.rows ?? []);
        this.loading.set(false);
      },
      error: (e: any) => {
        this.loading.set(false);
        this.error.set(e?.error?.message ?? e?.message ?? 'Erreur chargement registre');
      },
    });
  }

  loadLogs() {
    this.backofficeApi.getAuditLogs(200).subscribe({
      next: (res) => this.logs.set(res.items ?? []),
      error: () => {},
    });
  }

  exportCsv() {
    this.backofficeApi.exportAuditCsv().subscribe({
      next: (resp) => this.download(resp.body as Blob, 'fancapital_audit_registry.csv'),
      error: (e: any) => this.error.set(e?.error?.message ?? e?.message ?? 'Export CSV refusé'),
    });
  }

  exportPdf() {
    this.backofficeApi.exportAuditPdf().subscribe({
      next: (resp) => this.download(resp.body as Blob, 'fancapital_audit_registry.pdf'),
      error: (e: any) => this.error.set(e?.error?.message ?? e?.message ?? 'Export PDF refusé'),
    });
  }

  private download(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  from1e8(v: string): string {
    const n = Number(v);
    if (!Number.isFinite(n)) return '0';
    return (n / 1e8).toFixed(4);
  }
}

