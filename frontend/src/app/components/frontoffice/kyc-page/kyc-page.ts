import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-kyc-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './kyc-page.html',
  styleUrl: './kyc-page.css',
})
export class KycPage {}

