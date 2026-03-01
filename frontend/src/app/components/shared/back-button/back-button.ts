import { Component, input } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';

/**
 * Bouton de retour réutilisable.
 * - Si historique disponible : Location.back()
 * - Sinon (accès direct) : navigation vers fallbackRoute
 */
@Component({
  selector: 'app-back-button',
  standalone: true,
  template: `
    <button
      type="button"
      (click)="goBack()"
      class="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-white/10 bg-white/10 hover:bg-white/15 text-white font-medium text-sm transition"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
      </svg>
      {{ label() }}
    </button>
  `,
})
export class BackButton {
  /** Texte du bouton */
  label = input<string>('Retour');

  /** Route de repli si accès direct (ex: /acceuil-client, /backoffice/audit) */
  fallbackRoute = input<string>('/acceuil-client');

  constructor(
    private location: Location,
    private router: Router,
  ) {}

  goBack() {
    const fallback = this.fallbackRoute();
    const hasHistory = typeof window !== 'undefined' && window.history.length > 1;

    if (!hasHistory) {
      this.router.navigateByUrl(fallback);
    } else {
      this.location.back();
    }
  }
}
