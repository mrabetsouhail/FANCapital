import { Component, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';

type OrderMode = 'piscine' | 'p2p';
type OrderType = 'buy' | 'sell';
type TokenType = 'Alpha' | 'Beta';
type AmountType = 'tnd' | 'tokens';

@Component({
  selector: 'app-passer-ordre-page',
  imports: [CommonModule, FormsModule, NavbarClient],
  templateUrl: './passer-ordre-page.html',
  styleUrl: './passer-ordre-page.css',
})
export class PasserOrdrePage implements OnInit {
  tokenType = signal<TokenType>('Alpha');
  orderType = signal<OrderType>('buy');
  
  orderMode = signal<OrderMode>('piscine');
  
  amountType = signal<AmountType>('tnd');
  amount = signal<number>(0);
  
  alphaPrice = signal<number>(125.50);
  betaPrice = signal<number>(85.25);
  
  feeRate = 0.005;
  
  cashBalance = signal<number>(5000.00);
  
  currentPrice = computed(() => {
    return this.tokenType() === 'Alpha' ? this.alphaPrice() : this.betaPrice();
  });
  
  tokenAmount = computed(() => {
    if (this.amountType() === 'tokens') {
      return this.amount();
    }
    const totalCost = this.orderType() === 'buy' 
      ? this.amount() * (1 + this.feeRate)
      : this.amount();
    return this.amount() / this.currentPrice();
  });
  
  tndAmount = computed(() => {
    if (this.amountType() === 'tnd') {
      return this.amount();
    }
    return this.tokenAmount() * this.currentPrice();
  });
  
  fees = computed(() => {
    return this.tndAmount() * this.feeRate;
  });
  
  totalCost = computed(() => {
    if (this.orderType() === 'buy') {
      return this.tndAmount() + this.fees();
    }
    return this.tndAmount() - this.fees();
  });
  
  cashAfter = computed(() => {
    if (this.orderType() === 'buy') {
      return this.cashBalance() - this.totalCost();
    }
    return this.cashBalance() + this.totalCost();
  });
  
  canConfirm = computed(() => {
    if (this.amount() <= 0) return false;
    if (this.orderType() === 'buy') {
      return this.totalCost() <= this.cashBalance();
    }
    return true; 
  });

  constructor(
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
   
    this.route.queryParams.subscribe(params => {
      if (params['token']) {
        this.tokenType.set(params['token'] as TokenType);
      }
      if (params['action']) {
        this.orderType.set(params['action'] as OrderType);
      }
    });
  }

  toggleOrderMode() {
    this.orderMode.set(this.orderMode() === 'piscine' ? 'p2p' : 'piscine');
  }

  switchAmountType() {
    this.amountType.set(this.amountType() === 'tnd' ? 'tokens' : 'tnd');
    if (this.amount() > 0) {
      if (this.amountType() === 'tnd') {
        this.amount.set(this.tokenAmount() * this.currentPrice());
      } else {
        this.amount.set(this.tndAmount() / this.currentPrice());
      }
    }
  }

  onAmountChange() {
  }

  onConfirm() {
    if (!this.canConfirm()) return;
    
    console.log('Order confirmed:', {
      token: this.tokenType(),
      type: this.orderType(),
      mode: this.orderMode(),
      amount: this.amount(),
      amountType: this.amountType(),
      totalCost: this.totalCost(),
      fees: this.fees()
    });
    
    
  }

  onCancel() {
    this.router.navigate(['/acceuil-client']);
  }

  Math = Math; // Expose Math to template
}
