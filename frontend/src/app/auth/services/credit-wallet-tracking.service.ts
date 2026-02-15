import { Injectable } from '@angular/core';

const STORAGE_KEY = 'fancapital_creditUsed';

/**
 * Suivi du montant "utilisé depuis le Credit Wallet" pour l'option Priorité Crédit.
 * Permet d'afficher une répartition cohérente : Credit décroît en premier, puis Cash.
 * Stockage localStorage (clé par wallet).
 */
@Injectable({ providedIn: 'root' })
export class CreditWalletTrackingService {
  getCreditUsed(wallet: string): number {
    if (!wallet?.startsWith('0x')) return 0;
    try {
      const stored = localStorage.getItem(`${STORAGE_KEY}_${wallet.toLowerCase()}`);
      return stored ? parseFloat(stored) || 0 : 0;
    } catch {
      return 0;
    }
  }

  addCreditUsed(wallet: string, amount: number): void {
    if (!wallet?.startsWith('0x') || amount <= 0) return;
    const key = `${STORAGE_KEY}_${wallet.toLowerCase()}`;
    const current = this.getCreditUsed(wallet);
    localStorage.setItem(key, String(current + amount));
  }

  /** Réinitialise creditUsed quand creditDebt=0 (avance remboursée). */
  syncWithCreditDebt(wallet: string, creditDebt: number): void {
    if (!wallet?.startsWith('0x')) return;
    const used = this.getCreditUsed(wallet);
    if (creditDebt <= 0 || used > creditDebt) {
      this.clear(wallet);
      return;
    }
    // Garde used tel quel si cohérent
  }

  clear(wallet: string): void {
    if (!wallet?.startsWith('0x')) return;
    localStorage.removeItem(`${STORAGE_KEY}_${wallet.toLowerCase()}`);
  }

  /**
   * Montant affiché "Credit Wallet" = portion du solde attribuée au crédit (Priorité Crédit).
   * = min(cashBalance, creditDebt - creditUsed)
   */
  getCreditDisplay(cashBalance: number, creditDebt: number, wallet: string): number {
    const used = this.getCreditUsed(wallet);
    const creditAvailable = Math.max(0, creditDebt - used);
    return Math.min(cashBalance, creditAvailable);
  }

  /**
   * Montant affiché "Cash Wallet" = le reste (dépôts).
   */
  getCashDisplay(cashBalance: number, creditDebt: number, wallet: string): number {
    return Math.max(0, cashBalance - this.getCreditDisplay(cashBalance, creditDebt, wallet));
  }
}
