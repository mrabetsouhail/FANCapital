import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

type ClientType = 'particulier' | 'entreprise';

@Component({
  selector: 'app-signin-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './signin-page.html',
  styleUrl: './signin-page.css',
})
export class SigninPage {
  clientType = signal<ClientType>('particulier');
  logoPath: string = '/fancapital_logo.jpg';

  // Formulaire Particulier
  particulierForm = {
    nom: '',
    prenom: '',
    email: '',
    password: '',
    confirmPassword: '',
    cin: '',
    telephone: ''
  };

  // Formulaire Entreprise
  entrepriseForm = {
    denominationSociale: '',
    matriculeFiscal: '',
    nomGerant: '',
    prenomGerant: '',
    emailProfessionnel: '',
    telephone: ''
  };

  passwordStrength = signal<string>('');
  passwordStrengthClass = signal<string>('');

  selectClientType(type: ClientType) {
    this.clientType.set(type);
  }

  checkPasswordStrength(password: string) {
    if (!password) {
      this.passwordStrength.set('');
      this.passwordStrengthClass.set('');
      return;
    }

    let strength = 0;
    if (password.length >= 8) strength++;
    if (/[a-z]/.test(password)) strength++;
    if (/[A-Z]/.test(password)) strength++;
    if (/[0-9]/.test(password)) strength++;
    if (/[^a-zA-Z0-9]/.test(password)) strength++;

    switch (strength) {
      case 0:
      case 1:
        this.passwordStrength.set('Très faible');
        this.passwordStrengthClass.set('text-red-500');
        break;
      case 2:
        this.passwordStrength.set('Faible');
        this.passwordStrengthClass.set('text-orange-500');
        break;
      case 3:
        this.passwordStrength.set('Moyen');
        this.passwordStrengthClass.set('text-yellow-500');
        break;
      case 4:
        this.passwordStrength.set('Fort');
        this.passwordStrengthClass.set('text-green-500');
        break;
      case 5:
        this.passwordStrength.set('Très fort');
        this.passwordStrengthClass.set('text-green-600');
        break;
    }
  }

  validateCIN(cin: string): boolean {
    return /^\d{8}$/.test(cin);
  }

  validateNIF(nif: string): boolean {
    return /^\d{8}$/.test(nif);
  }

  validateEmail(email: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }

  isParticulierFormValid(): boolean {
    const f = this.particulierForm;
    return !!(
      f.nom.trim() &&
      f.prenom.trim() &&
      this.validateEmail(f.email) &&
      f.password.length >= 8 &&
      f.password === f.confirmPassword &&
      this.validateCIN(f.cin) &&
      f.telephone.trim()
    );
  }

  isEntrepriseFormValid(): boolean {
    const f = this.entrepriseForm;
    return !!(
      f.denominationSociale.trim() &&
      this.validateNIF(f.matriculeFiscal) &&
      f.nomGerant.trim() &&
      f.prenomGerant.trim() &&
      this.validateEmail(f.emailProfessionnel) &&
      f.telephone.trim()
    );
  }

  onSubmitParticulier() {
    if (!this.isParticulierFormValid()) return;
    // TODO: Implement signup logic for particulier
    console.log('Particulier Form:', this.particulierForm);
  }

  onSubmitEntreprise() {
    if (!this.isEntrepriseFormValid()) return;
    // TODO: Implement signup logic for entreprise
    console.log('Entreprise Form:', this.entrepriseForm);
  }
}
