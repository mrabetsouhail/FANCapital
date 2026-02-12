import { Component, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import type { SciScoreResult } from '../../../blockchain/models/investor.models';

const TIER_NAMES: Record<number, string> = {
  0: 'BRONZE',
  1: 'SILVER',
  2: 'GOLD',
  3: 'PLATINUM',
  4: 'DIAMOND',
};

const TIER_THRESHOLDS = [16, 36, 56, 85]; // Silver, Gold, Platinum, Diamond

@Component({
  selector: 'app-sci-nudge-banner',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './sci-nudge-banner.html',
  styleUrl: './sci-nudge-banner.css',
})
export class SciNudgeBanner {
  /** Résultat SCI (null = pas encore chargé ou pas de nudge). */
  sci = input<SciScoreResult | null>(null);

  dismissed = signal(false);

  /** Mod AST 4.2: Nudge si (a) KYC1 cap OU (b) ±2 pts d'un seuil. */
  get shouldShow(): boolean {
    if (this.dismissed()) return false;
    const s = this.sci();
    if (!s) return false;
    if (s.kycTierCap === 0 && s.tierFromScore >= 1) return true;
    return this.isNearThreshold();
  }

  /** Alerte seuil: ±2 points d'un changement de Tier (ex: 34/35) - Mod AST 4.2 */
  isNearThreshold(): boolean {
    return this.nearThresholdInfo() !== null;
  }

  /** Seuil et tier cible quand à ±2 pts (ex: 34 → Gold/36) */
  nearThresholdInfo(): { threshold: number; tierName: string } | null {
    const s = this.sci();
    if (!s || s.kycTierCap === 0) return null;
    const score = s.score;
    for (let i = 0; i < TIER_THRESHOLDS.length; i++) {
      const t = TIER_THRESHOLDS[i];
      if (Math.abs(score - t) <= 2 && score < t) {
        return { threshold: t, tierName: TIER_NAMES[i + 1] ?? '' };
      }
    }
    return null;
  }

  get nextTierName(): string {
    const s = this.sci();
    if (!s || s.tierFromScore <= 0) return '';
    return TIER_NAMES[s.tierFromScore] ?? '';
  }

  get threshold(): number {
    const s = this.sci();
    if (!s || s.tierFromScore <= 0) return 0;
    return TIER_THRESHOLDS[s.tierFromScore - 1] ?? 0;
  }

  dismiss() {
    this.dismissed.set(true);
  }
}
