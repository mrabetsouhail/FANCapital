import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import { NavbarClient } from '../../frontoffice/navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import type { AuditLogRow, AuditRegistryRow } from '../../../backoffice/models/audit.models';
import type { TxRow, TxKind } from '../../../blockchain/models/tx-history.models';

@Component({
  selector: 'app-audit-registry-page',
  imports: [CommonModule, FormsModule, NavbarClient, BackButton, DatePipe],
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

  /** Utilisateur sélectionné (clic sur une ligne). */
  selectedUser = signal<AuditRegistryRow | null>(null);
  userTxHistory = signal<TxRow[]>([]);
  txHistoryLoading = signal<boolean>(false);
  txHistoryError = signal<string>('');

  /** Historique filtré : achat et vente uniquement. */
  filteredTxHistory = computed(() =>
    this.userTxHistory().filter((t) => t.kind === 'BUY' || t.kind === 'SELL')
  );

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

  constructor(
    private backofficeApi: BackofficeApiService,
    private authApi: AuthApiService,
    private blockchainApi: BlockchainApiService,
  ) {}

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

  /** Retourne true si la valeur 1e8 est > 0 (pour afficher les tokens bloqués). */
  hasLocked(v: string | undefined): boolean {
    return (v ? Number(v) : 0) > 0;
  }

  /** Clic sur une ligne : charge l'historique des transactions (achat/vente) de l'utilisateur. */
  selectUser(r: AuditRegistryRow) {
    const wallet = r.walletAddress?.trim();
    if (!wallet || !wallet.startsWith('0x') || wallet.length !== 42) {
      this.selectedUser.set(null);
      this.userTxHistory.set([]);
      return;
    }
    this.selectedUser.set(r);
    this.txHistoryLoading.set(true);
    this.txHistoryError.set('');
    this.blockchainApi.getTxHistory(wallet, 200).subscribe({
      next: (res) => {
        this.userTxHistory.set(res.items ?? []);
        this.txHistoryLoading.set(false);
      },
      error: (e: any) => {
        this.txHistoryError.set(e?.error?.message ?? e?.message ?? 'Erreur chargement historique');
        this.userTxHistory.set([]);
        this.txHistoryLoading.set(false);
      },
    });
  }

  closeUserHistory() {
    this.selectedUser.set(null);
    this.userTxHistory.set([]);
    this.txHistoryError.set('');
  }

  getTxKindLabel(kind: TxKind): string {
    if (kind === 'BUY') return 'Achat';
    if (kind === 'SELL') return 'Vente';
    return kind;
  }

  from1e8ToNumber(v: string): number {
    return Number(v) / 1e8;
  }
}

