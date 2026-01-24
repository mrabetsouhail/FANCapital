import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';

interface CollateralToken {
  id: number;
  type: 'Alpha' | 'Beta';
  amount: number;
  lockedAmount: number;
  price: number;
  value: number;
}

@Component({
  selector: 'app-credit-page',
  imports: [CommonModule, FormsModule, NavbarClient],
  templateUrl: './credit-page.html',
  styleUrl: './credit-page.css',
})
export class CreditPage {
  // Credit data
  totalCreditUsed = signal<number>(5000.00);
  totalCreditLimit = signal<number>(10000.00);
  
  // Collateral tokens
  collateralTokens = signal<CollateralToken[]>([
    { id: 1, type: 'Alpha', amount: 150, lockedAmount: 40, price: 125.50, value: 5020.00 },
    { id: 2, type: 'Beta', amount: 75, lockedAmount: 20, price: 85.25, value: 1705.00 },
  ]);
  
  // LTV (Loan-to-Value) calculation
  totalCollateralValue = computed(() => {
    return this.collateralTokens().reduce((sum, token) => sum + token.value, 0);
  });
  
  ltvRatio = computed(() => {
    if (this.totalCollateralValue() === 0) return 0;
    return (this.totalCreditUsed() / this.totalCollateralValue()) * 100;
  });
  
  // LTV health indicator
  ltvHealth = computed(() => {
    const ltv = this.ltvRatio();
    if (ltv < 50) return { status: 'safe', color: '#22c55e', label: 'Sécurisé' };
    if (ltv < 75) return { status: 'warning', color: '#f59e0b', label: 'Attention' };
    if (ltv < 90) return { status: 'danger', color: '#ef4444', label: 'Risque' };
    return { status: 'critical', color: '#dc2626', label: 'Critique' };
  });
  
  // Liquidation threshold (typically 90%)
  liquidationThreshold = 90;
  marginBeforeLiquidation = computed(() => {
    return this.liquidationThreshold - this.ltvRatio();
  });
  
  // Repayment amount
  repaymentAmount = signal<number>(0);
  
  totalCollateral = computed(() => {
    return this.collateralTokens().reduce((sum, token) => sum + token.lockedAmount, 0);
  });

  constructor() {}

  getLTVPercentage(): number {
    return Math.min(this.ltvRatio(), 100);
  }

  getLTVArc(): { dasharray: number; dashoffset: number } {
    const percentage = this.getLTVPercentage();
    const radius = 80;
    const circumference = 2 * Math.PI * radius;
    const offset = circumference - (percentage / 100) * circumference;
    return { dasharray: circumference, dashoffset: offset };
  }

  onRepay() {
    if (this.repaymentAmount() <= 0 || this.repaymentAmount() > this.totalCreditUsed()) {
      return;
    }
    
    // TODO: Implement repayment logic
    console.log('Repayment:', this.repaymentAmount());
    
    // Simulate repayment
    const newCreditUsed = this.totalCreditUsed() - this.repaymentAmount();
    this.totalCreditUsed.set(Math.max(0, newCreditUsed));
    this.repaymentAmount.set(0);
  }

  getTokenTypeLabel(type: 'Alpha' | 'Beta'): string {
    return type === 'Alpha' ? 'Token Alpha' : 'Token Beta';
  }
}
