import { Injectable } from '@angular/core';

const STORAGE_KEY = 'fancapital_creditUsed';
const REPAYMENT_KEY = 'fancapital_repaymentFromCash';

/**
 * Suivi du montant "utilisé depuis le Credit Wallet" pour l'option Priorité Crédit.
 * Permet d'afficher une répartition cohérente : Credit décroît en premier, puis Cash.
 * Le remboursement est imputé au Cash Wallet (pas au Credit).
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

  /** Montant remboursé depuis le Cash Wallet (pour afficher le remboursement imputé au Cash, pas au Credit). */
  getRepaymentFromCash(wallet: string): number {
    if (!wallet?.startsWith('0x')) return 0;
    try {
      const stored = localStorage.getItem(`${REPAYMENT_KEY}_${wallet.toLowerCase()}`);
      return stored ? parseFloat(stored) || 0 : 0;
    } catch {
      return 0;
    }
  }

  /** Enregistre un remboursement imputé au Cash Wallet (pas au Credit). */
  addRepaymentFromCash(wallet: string, amount: number): void {
    if (!wallet?.startsWith('0x') || amount <= 0) return;
    const key = `${REPAYMENT_KEY}_${wallet.toLowerCase()}`;
    const current = this.getRepaymentFromCash(wallet);
    localStorage.setItem(key, String(current + amount));
  }

  /** Réinitialise creditUsed et repaymentFromCash quand creditDebt=0 (avance remboursée). */
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
    const key = wallet.toLowerCase();
    localStorage.removeItem(`${STORAGE_KEY}_${key}`);
    localStorage.removeItem(`${REPAYMENT_KEY}_${key}`);
  }

  /**
   * Montant affiché "Credit Wallet" = portion du solde attribuée au crédit (Priorité Crédit).
   * Le remboursement (repaymentFromCash) est imputé au Cash : on l'ajoute pour ne pas réduire le Credit.
   * = min(cashBalance, creditDebt - creditUsed + repaymentFromCash)
   */
  getCreditDisplay(cashBalance: number, creditDebt: number, wallet: string): number {
    const used = this.getCreditUsed(wallet);
    const repaidFromCash = this.getRepaymentFromCash(wallet);
    const creditAvailable = Math.max(0, creditDebt - used + repaidFromCash);
    return Math.min(cashBalance, creditAvailable);
  }

  /**
   * Montant affiché "Cash Wallet" = le reste (dépôts).
   */
  getCashDisplay(cashBalance: number, creditDebt: number, wallet: string): number {
    return Math.max(0, cashBalance - this.getCreditDisplay(cashBalance, creditDebt, wallet));
  }
}
