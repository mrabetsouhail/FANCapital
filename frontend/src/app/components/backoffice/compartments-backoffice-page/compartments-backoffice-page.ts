import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { BackofficeApiService } from '../../../backoffice/services/backoffice-api.service';
import { BackButton } from '../../shared/back-button/back-button';
import type { MatriceInfo } from '../../../backoffice/models/compartments.models';
import { COMPARTMENT_LABELS } from '../../../backoffice/models/compartments.models';

@Component({
  selector: 'app-compartments-backoffice-page',
  imports: [CommonModule, BackButton],
  templateUrl: './compartments-backoffice-page.html',
  styleUrl: './compartments-backoffice-page.css',
})
export class CompartmentsBackofficePage implements OnInit {
  data = signal<MatriceInfo | null>(null);
  loading = signal<boolean>(false);
  error = signal<string>('');

  constructor(private api: BackofficeApiService) {}

  ngOnInit() {
    this.refresh();
  }

  refresh() {
    this.loading.set(true);
    this.error.set('');
    this.api.getCompartments().subscribe({
      next: (res) => {
        this.data.set(res);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (err instanceof HttpErrorResponse) {
          if (err.status === 404) {
            this.error.set('Architecture Matrice non déployée. Exécutez le déploiement blockchain avec la section matrice.');
          } else {
            this.error.set(err.error?.message ?? err.message ?? 'Erreur chargement');
          }
        } else {
          this.error.set('Erreur chargement');
        }
      },
    });
  }

  shortAddr(addr: string | null): string {
    if (!addr) return '—';
    if (addr.length <= 14) return addr;
    return addr.slice(0, 8) + '…' + addr.slice(-6);
  }

  /** Compartiments pour le template (A, B, C, D avec adresse, label et solde) */
  compartments = computed(() => {
    const d = this.data();
    if (!d) return [];
    return [
      { key: 'A', addr: d.piscineA, balance: d.balancePiscineATnd ?? null, info: COMPARTMENT_LABELS['A'] },
      { key: 'B', addr: d.piscineB, balance: d.balancePiscineBTnd ?? null, info: COMPARTMENT_LABELS['B'] },
      { key: 'C', addr: d.piscineC, balance: d.balancePiscineCTnd ?? null, info: COMPARTMENT_LABELS['C'] },
      { key: 'D', addr: d.piscineD, balance: d.balancePiscineDTnd ?? null, info: COMPARTMENT_LABELS['D'] },
    ];
  });

  /** Convertit 1e8 en nombre affichable */
  from1e8(v: string | null | undefined): number {
    if (v == null || v === '') return 0;
    return Number(v) / 1e8;
  }

  /** Total TND de toutes les piscines */
  totalTnd = computed(() => {
    const d = this.data();
    if (!d) return 0;
    return this.from1e8(d.balancePiscineATnd) + this.from1e8(d.balancePiscineBTnd)
      + this.from1e8(d.balancePiscineCTnd) + this.from1e8(d.balancePiscineDTnd);
  });
}
