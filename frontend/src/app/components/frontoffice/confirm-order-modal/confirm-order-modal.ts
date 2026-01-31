import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

export type ConfirmOrderMode = 'piscine' | 'p2p';
export type ConfirmOrderType = 'buy' | 'sell';
export type ConfirmAmountType = 'tnd' | 'tokens';

export interface ConfirmOrderSummary {
  tokenLabel: string;
  tokenSymbol: string;
  orderType: ConfirmOrderType;
  orderMode: ConfirmOrderMode;
  amountType: ConfirmAmountType;
  amount: number;
  price: number; // 1 token price in TND
  fees: number; // in TND
  totalCost: number; // in TND (debit/credit shown in UI)
  cashAfter: number; // in TND
}

@Component({
  selector: 'app-confirm-order-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './confirm-order-modal.html',
  styleUrl: './confirm-order-modal.css',
})
export class ConfirmOrderModal {
  @Input({ required: true }) open!: boolean;
  @Input({ required: true }) summary!: ConfirmOrderSummary | null;
  @Input() isSubmitting: boolean = false;
  @Input() error: string | null = null;

  @Output() close = new EventEmitter<void>();
  @Output() confirm = new EventEmitter<void>();

  onBackdropClick(e: MouseEvent) {
    // close only when clicking the backdrop (not the card)
    if (e.target === e.currentTarget) this.close.emit();
  }
}

