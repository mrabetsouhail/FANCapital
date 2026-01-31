import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthApiService } from '../../auth/services/auth-api.service';
import { SessionService } from '../../auth/services/session.service';

type ClientType = 'particulier' | 'entreprise';
type PostSignupRoute = '/kyc' | '/acceuil-client';

@Component({
  selector: 'app-signin-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './signin-page.html',
  styleUrl: './signin-page.css',
})
export class SigninPage {
  clientType = signal<ClientType>('particulier');
  logoPath: string = '/fancapital_logo.jpg';
  errorMessage = signal<string>('');

  // Formulaire Particulier
  particulierForm = {
    nom: '',
    prenom: '',
    email: '',
    password: '',
    confirmPassword: '',
    cin: '',
    passportNumber: '',
    resident: true,
    telephone: ''
  };

  // Formulaire Entreprise
  entrepriseForm = {
    denominationSociale: '',
    matriculeFiscal: '',
    nomGerant: '',
    prenomGerant: '',
    emailProfessionnel: '',
    password: '',
    confirmPassword: '',
    telephone: ''
  };

  passwordStrength = signal<string>('');
  passwordStrengthClass = signal<string>('');
  showKycPrompt = signal<boolean>(false);
  showWalletPrompt = signal<boolean>(false);
  walletError = signal<string>('');
  isWalletLinking = signal<boolean>(false);
  nextRouteAfterSignup = signal<PostSignupRoute>('/acceuil-client');

  constructor(private authApi: AuthApiService, private router: Router, private session: SessionService) {}

  selectClientType(type: ClientType) {
    this.errorMessage.set('');
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

  validatePassport(passport: string): boolean {
    // MVP: 6-20 alphanum (passport formats vary by country)
    return /^[A-Za-z0-9]{6,20}$/.test(passport);
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
      (f.resident ? this.validateCIN(f.cin) : this.validatePassport(f.passportNumber)) &&
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
      f.password.length >= 8 &&
      f.password === f.confirmPassword &&
      f.telephone.trim()
    );
  }

  onSubmitParticulier() {
    if (!this.isParticulierFormValid()) return;
    this.errorMessage.set('');
    const f = this.particulierForm;
    const req = {
      nom: f.nom.trim(),
      prenom: f.prenom.trim(),
      email: f.email.trim(),
      password: f.password,
      confirmPassword: f.confirmPassword,
      cin: f.cin.trim(),
      passportNumber: f.passportNumber.trim(),
      resident: !!f.resident,
      telephone: f.telephone.trim(),
    };
    this.authApi.registerParticulier(req).subscribe({
      next: (res) => {
        this.session.setSession(res.token, res.user.email, (res.user as any)?.walletAddress ?? null);
        this.showKycPrompt.set(true);
      },
      error: (err: unknown) => {
        const msg =
          err instanceof HttpErrorResponse
            ? (err.error?.message ?? err.message)
            : 'Erreur inscription';
        this.errorMessage.set(String(msg));
      },
    });
  }

  onSubmitEntreprise() {
    if (!this.isEntrepriseFormValid()) return;
    this.errorMessage.set('');
    const f = this.entrepriseForm;
    const req = {
      denominationSociale: f.denominationSociale.trim(),
      matriculeFiscal: f.matriculeFiscal.trim(),
      nomGerant: f.nomGerant.trim(),
      prenomGerant: f.prenomGerant.trim(),
      emailProfessionnel: f.emailProfessionnel.trim(),
      password: f.password,
      confirmPassword: f.confirmPassword,
      telephone: f.telephone.trim(),
    };
    this.authApi.registerEntreprise(req).subscribe({
      next: (res) => {
        this.session.setSession(res.token, res.user.email, (res.user as any)?.walletAddress ?? null);
        this.showKycPrompt.set(true);
      },
      error: (err: unknown) => {
        const msg =
          err instanceof HttpErrorResponse
            ? (err.error?.message ?? err.message)
            : 'Erreur inscription';
        this.errorMessage.set(String(msg));
      },
    });
  }

  onKycNow() {
    this.showKycPrompt.set(false);
    this.nextRouteAfterSignup.set('/kyc');
    this.showWalletPrompt.set(true);
  }

  onKycLater() {
    this.showKycPrompt.set(false);
    this.nextRouteAfterSignup.set('/acceuil-client');
    this.showWalletPrompt.set(true);
  }

  onWalletLater() {
    this.showWalletPrompt.set(false);
    this.router.navigate([this.nextRouteAfterSignup()]);
  }

  async onConnectWallet() {
    this.walletError.set('');
    this.isWalletLinking.set(true);

    try {
      // 1) Ask backend for message to sign (authenticated via authTokenInterceptor)
      const challenge = await new Promise<{ message: string }>((resolve, reject) => {
        this.authApi.walletChallenge().subscribe({ next: resolve, error: reject });
      });

      const w = (globalThis as any).window;
      const eth = w?.ethereum;
      if (!eth?.request) {
        throw new Error("Wallet non détecté. Installe MetaMask puis réessaie.");
      }

      // 2) Request wallet address
      const accounts: string[] = await eth.request({ method: 'eth_requestAccounts', params: [] });
      const addr = accounts?.[0];
      if (!addr || !addr.startsWith('0x') || addr.length !== 42) {
        throw new Error('Adresse wallet invalide.');
      }

      // 3) personal_sign the challenge message
      const signature: string = await eth.request({ method: 'personal_sign', params: [challenge.message, addr] });
      if (!signature || !signature.startsWith('0x')) throw new Error('Signature invalide.');

      // 4) Send signature to backend for verification + storage
      const confirm = await new Promise<{ walletAddress: string }>((resolve, reject) => {
        this.authApi.walletConfirm({ signature }).subscribe({ next: resolve, error: reject });
      });

      localStorage.setItem('walletAddress', confirm.walletAddress);
      this.session.scheduleAutoLogout();

      this.showWalletPrompt.set(false);
      this.router.navigate([this.nextRouteAfterSignup()]);
    } catch (err: any) {
      const msg =
        err instanceof HttpErrorResponse
          ? (err.error?.message ?? err.message)
          : (err?.message ?? 'Erreur connexion wallet');
      this.walletError.set(String(msg));
    } finally {
      this.isWalletLinking.set(false);
    }
  }
}
