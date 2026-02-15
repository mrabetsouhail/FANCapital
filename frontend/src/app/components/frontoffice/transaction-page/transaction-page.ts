import { Component, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';

type TransactionType = 'depot' | 'retrait';
type DepositMethod = 'virement' | 'carte';
type TransactionStatus = 'pending' | 'verified' | 'completed';

interface BankAccount {
  id: number;
  label: string;
  iban: string;
  bankName: string;
}

@Component({
  selector: 'app-transaction-page',
  imports: [CommonModule, FormsModule, NavbarClient, BackButton],
  templateUrl: './transaction-page.html',
  styleUrl: './transaction-page.css',
})
export class TransactionPage implements OnInit {
  transactionType = signal<TransactionType>('depot');
  depositMethod = signal<DepositMethod>('virement');
  amount = signal<number>(0);
  selectedBankAccount = signal<number | null>(null);
  transactionStatus = signal<TransactionStatus>('pending');
  
  // FAN-Capital IBAN (example)
  fanCapitalIBAN = 'TN59 01 010 1234567890123456789';
  
  // User's registered bank accounts
  bankAccounts = signal<BankAccount[]>([
    { id: 1, label: 'Compte Principal', iban: 'TN59 01 010 9876543210987654321', bankName: 'Banque de Tunisie' },
    { id: 2, label: 'Compte Épargne', iban: 'TN59 02 020 1111111111111111111', bankName: 'STB' },
  ]);

  // Progress percentage based on status
  progressPercentage = computed(() => {
    switch (this.transactionStatus()) {
      case 'pending':
        return 33;
      case 'verified':
        return 66;
      case 'completed':
        return 100;
      default:
        return 0;
    }
  });

  // Status label in French
  statusLabel = computed(() => {
    switch (this.transactionStatus()) {
      case 'pending':
        return 'En attente';
      case 'verified':
        return 'Vérifié';
      case 'completed':
        return 'Complété';
      default:
        return '';
    }
  });

  canSubmit = computed(() => {
    if (this.transactionType() === 'depot') {
      return this.amount() > 0;
    } else {
      return this.amount() > 0 && this.selectedBankAccount() !== null;
    }
  });

  constructor(
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['type']) {
        this.transactionType.set(params['type'] as TransactionType);
      }
    });
  }

  switchTransactionType(type: TransactionType) {
    this.transactionType.set(type);
    this.amount.set(0);
    this.selectedBankAccount.set(null);
    this.transactionStatus.set('pending');
  }

  selectDepositMethod(method: DepositMethod) {
    this.depositMethod.set(method);
  }

  selectBankAccount(accountId: number) {
    this.selectedBankAccount.set(accountId);
  }

  copyIBAN() {
    navigator.clipboard.writeText(this.fanCapitalIBAN).then(() => {
      // Could show a toast notification here
      console.log('IBAN copied to clipboard');
    });
  }

  onSubmit() {
    if (!this.canSubmit()) return;

    // Simulate transaction processing
    this.transactionStatus.set('pending');
    
    // Simulate status progression (in real app, this would be from backend)
    setTimeout(() => {
      this.transactionStatus.set('verified');
      setTimeout(() => {
        this.transactionStatus.set('completed');
      }, 2000);
    }, 2000);
  }

  onCancel() {
    this.router.navigate(['/acceuil-client']);
  }
}
