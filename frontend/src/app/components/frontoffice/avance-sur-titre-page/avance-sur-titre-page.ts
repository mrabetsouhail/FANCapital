import { Component, signal, computed } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';

interface RepaymentSchedule {
  id: number;
  date: Date;
  amount: number;
  type: 'coupon' | 'echeance';
  status: 'pending' | 'completed';
}

@Component({
  selector: 'app-avance-sur-titre-page',
  imports: [CommonModule, FormsModule, NavbarClient, DatePipe],
  templateUrl: './avance-sur-titre-page.html',
  styleUrl: './avance-sur-titre-page.css',
})
export class AvanceSurTitrePage {
  // Available tokens for collateral
  availableTokens = signal<{ type: 'Alpha' | 'Beta'; amount: number; price: number }[]>([
    { type: 'Alpha', amount: 150, price: 125.50 },
    { type: 'Beta', amount: 75, price: 85.25 },
  ]);

  // Selected tokens for collateral
  selectedTokenType = signal<'Alpha' | 'Beta'>('Alpha');
  selectedTokenAmount = signal<number>(0);
  
  // Loan simulation
  loanAmount = signal<number>(0);
  maxLoanAmount = computed(() => {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    if (!token) return 0;
    return (this.selectedTokenAmount() * token.price) * 0.7; // 70% LTV max
  });

  // LTV calculation
  currentLTV = computed(() => {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    if (!token || this.selectedTokenAmount() === 0) return 0;
    const collateralValue = this.selectedTokenAmount() * token.price;
    if (collateralValue === 0) return 0;
    return (this.loanAmount() / collateralValue) * 100;
  });

  // Liquidation threshold
  liquidationThreshold = 90;
  liquidationPrice = computed(() => {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    if (!token || this.selectedTokenAmount() === 0) return 0;
    return (this.loanAmount() / (this.selectedTokenAmount() * (this.liquidationThreshold / 100)));
  });

  // Risk visualization data points
  riskDataPoints = computed(() => {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    if (!token || this.selectedTokenAmount() === 0 || this.loanAmount() === 0) return [];
    
    const currentPrice = token.price;
    const liquidationPrice = this.liquidationPrice();
    const points = [];
    
    // Generate points from current price down to liquidation price
    for (let i = 0; i <= 20; i++) {
      const price = currentPrice - ((currentPrice - liquidationPrice) / 20) * i;
      const ltv = (this.loanAmount() / (this.selectedTokenAmount() * price)) * 100;
      points.push({ price, ltv: Math.min(ltv, 100) });
    }
    
    return points;
  });

  // Repayment schedule
  repaymentSchedule = signal<RepaymentSchedule[]>([
    { id: 1, date: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), amount: 500, type: 'coupon', status: 'pending' },
    { id: 2, date: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000), amount: 500, type: 'coupon', status: 'pending' },
    { id: 3, date: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000), amount: 2000, type: 'echeance', status: 'pending' },
    { id: 4, date: new Date(Date.now() + 60 * 24 * 60 * 60 * 1000), amount: 2000, type: 'echeance', status: 'pending' },
  ]);

  // Interest rate
  interestRate = 5; // 5% annual

  constructor() {
    // Initialize with default values
    this.selectedTokenAmount.set(50);
    this.updateLoanAmount();
  }

  onTokenTypeChange() {
    this.selectedTokenAmount.set(0);
    this.loanAmount.set(0);
  }

  onTokenAmountChange() {
    this.updateLoanAmount();
  }

  onLoanAmountChange() {
    // Ensure loan amount doesn't exceed max
    if (this.loanAmount() > this.maxLoanAmount()) {
      this.loanAmount.set(this.maxLoanAmount());
    }
  }

  updateLoanAmount() {
    // Auto-update loan amount based on selected tokens (70% of collateral value)
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    if (token && this.selectedTokenAmount() > 0) {
      const maxLoan = (this.selectedTokenAmount() * token.price) * 0.7;
      this.loanAmount.set(Math.min(this.loanAmount(), maxLoan));
    }
  }

  getTokenLabel(type: 'Alpha' | 'Beta'): string {
    return type === 'Alpha' ? 'Token Alpha' : 'Token Beta';
  }

  getRepaymentTypeLabel(type: 'coupon' | 'echeance'): string {
    return type === 'coupon' ? 'Coupon' : 'Échéance';
  }

  getMaxPrice(): number {
    const token = this.availableTokens().find(t => t.type === this.selectedTokenType());
    return token ? token.price : 0;
  }

  getMinPrice(): number {
    return this.liquidationPrice();
  }

  getChartHeight(): number {
    return 200;
  }

  getChartWidth(): number {
    return 600;
  }

  getTotalRepayment(): number {
    return this.repaymentSchedule().reduce((total, item) => total + item.amount, 0);
  }

  getSelectedToken() {
    return this.availableTokens().find(t => t.type === this.selectedTokenType());
  }

  getSelectedTokenPrice(): number {
    const token = this.getSelectedToken();
    return token ? token.price : 0;
  }

  getSelectedTokenAmount(): number {
    const token = this.getSelectedToken();
    return token ? token.amount : 0;
  }

  getCollateralValue(): number {
    return this.selectedTokenAmount() * this.getSelectedTokenPrice();
  }
}
