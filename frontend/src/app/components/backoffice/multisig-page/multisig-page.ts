import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import { NavbarClient } from '../../frontoffice/navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import type { MultiSigInfo, MultiSigTransaction, SubmitTransactionRequest } from '../../../backoffice/models/multisig.models';
import type { TxResponse } from '../../../backoffice/models/fiscal.models';

@Component({
  selector: 'app-multisig-page',
  imports: [CommonModule, FormsModule, NavbarClient, BackButton],
  templateUrl: './multisig-page.html',
  styleUrls: ['./multisig-page.css', '../backoffice-theme.css'],
})
export class MultiSigPage implements OnInit {
  info = signal<MultiSigInfo | null>(null);
  transactions = signal<MultiSigTransaction[]>([]);
  loading = signal<boolean>(false);
  error = signal<string>('');
  submitting = signal<boolean>(false);

  // Form for submitting new transaction
  showSubmitForm = signal<boolean>(false);
  submitTo = signal<string>('');
  submitValue = signal<string>('0');
  submitData = signal<string>('0x');

  constructor(private api: BackofficeApiService) {}

  ngOnInit() {
    this.refresh();
  }

  refresh() {
    this.loading.set(true);
    this.error.set('');
    this.api.getMultiSigInfo().subscribe({
      next: (info) => {
        this.info.set(info);
        this.loadTransactions();
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const msg = err instanceof HttpErrorResponse ? (err.error?.message ?? err.message) : 'Erreur chargement';
        this.error.set(String(msg));
      },
    });
  }

  loadTransactions() {
    this.api.listMultiSigTransactions().subscribe({
      next: (res) => {
        this.transactions.set(res.transactions);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const msg = err instanceof HttpErrorResponse ? (err.error?.message ?? err.message) : 'Erreur chargement';
        this.error.set(String(msg));
      },
    });
  }

  toggleSubmitForm() {
    this.showSubmitForm.set(!this.showSubmitForm());
  }

  onSubmitTransaction() {
    const to = this.submitTo().trim();
    const value = this.submitValue().trim();
    const data = this.submitData().trim() || '0x';

    if (!to || !to.match(/^0x[a-fA-F0-9]{40}$/)) {
      this.error.set('Adresse destination invalide (doit être 0x suivi de 40 caractères hex)');
      return;
    }

    if (!value || isNaN(Number(value)) || Number(value) < 0) {
      this.error.set('Montant invalide (doit être un nombre >= 0 en wei)');
      return;
    }

    this.submitting.set(true);
    this.error.set('');

    const req: SubmitTransactionRequest = { to, value, data };
    this.api.submitMultiSigTransaction(req).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.showSubmitForm.set(false);
        this.submitTo.set('');
        this.submitValue.set('0');
        this.submitData.set('0x');
        setTimeout(() => this.refresh(), 2000);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const msg = err instanceof HttpErrorResponse ? (err.error?.message ?? err.message) : 'Erreur soumission';
        this.error.set(String(msg));
      },
    });
  }

  onConfirm(txId: string) {
    this.submitting.set(true);
    this.error.set('');
    this.api.confirmMultiSigTransaction(txId).subscribe({
      next: () => {
        this.submitting.set(false);
        setTimeout(() => this.refresh(), 1000);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const msg = err instanceof HttpErrorResponse ? (err.error?.message ?? err.message) : 'Erreur confirmation';
        this.error.set(String(msg));
      },
    });
  }

  onExecute(txId: string) {
    if (!confirm('Exécuter cette transaction ? Cette action est irréversible.')) return;
    this.submitting.set(true);
    this.error.set('');
    this.api.executeMultiSigTransaction(txId).subscribe({
      next: () => {
        this.submitting.set(false);
        setTimeout(() => this.refresh(), 1000);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const msg = err instanceof HttpErrorResponse ? (err.error?.message ?? err.message) : 'Erreur exécution';
        this.error.set(String(msg));
      },
    });
  }

  fromWei(wei: string): number {
    const n = Number(wei);
    if (!Number.isFinite(n)) return 0;
    return n / 1e18;
  }

  formatAddress(addr: string): string {
    if (!addr || addr.length < 10) return addr;
    return `${addr.substring(0, 6)}...${addr.substring(addr.length - 4)}`;
  }

  pendingTransactions = computed(() => {
    return this.transactions().filter((t) => !t.executed);
  });

  executedTransactions = computed(() => {
    return this.transactions().filter((t) => t.executed);
  });
}
