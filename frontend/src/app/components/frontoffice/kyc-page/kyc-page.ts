import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BackButton } from '../../shared/back-button/back-button';

@Component({
  selector: 'app-kyc-page',
  imports: [CommonModule, BackButton],
  templateUrl: './kyc-page.html',
  styleUrl: './kyc-page.css',
})
export class KycPage {}

