import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthApiService } from '../../auth/services/auth-api.service';
import { SessionService } from '../../auth/services/session.service';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService, type AppLang } from '../../i18n/language.service';
import { isWalletLoginEnabled } from '../../config/feature-flags';

@Component({
  selector: 'app-authentification-page',
  imports: [FormsModule, RouterLink, TranslateModule],
  templateUrl: './authentification-page.html',
  styleUrl: './authentification-page.css',
})
export class AuthentificationPage {
  email: string = '';
  password: string = '';
  // Use relative paths so it works even if deployed under a sub-path
  logoPath: string = 'assets/images/fancapital_logo.svg';
  private readonly fallbackLogoPath: string = 'assets/images/fancapital_logo.jpg';
  errorMessage: string = '';

  lang: AppLang = 'fr';
  showPassword: boolean = false;
  walletConnecting: boolean = false;
  walletAddressPreview: string = '';
  walletLoginEnabled: boolean = isWalletLoginEnabled();
  
  constructor(
    private router: Router,
    private authApi: AuthApiService,
    private session: SessionService,
    private language: LanguageService
  ) {
    this.lang = this.language.current;
  }

  setLang(l: string) {
    this.language.use(l as AppLang);
    this.lang = this.language.current;
  }

  onLogoError() {
    // if SVG is missing, gracefully fallback to JPG
    if (this.logoPath !== this.fallbackLogoPath) {
      this.logoPath = this.fallbackLogoPath;
    }
  }

  togglePassword() {
    this.showPassword = !this.showPassword;
  }

  async connectWallet() {
    this.errorMessage = '';
    this.walletAddressPreview = '';

    const eth = (window as any)?.ethereum;
    if (!eth?.request) {
      this.errorMessage = 'Wallet non détecté (installez MetaMask).';
      return;
    }

    this.walletConnecting = true;
    try {
      // 1) Request wallet address
      const accounts = (await eth.request({ method: 'eth_requestAccounts' })) as string[];
      const addr = (accounts?.[0] ?? '').trim();
      if (!addr) throw new Error('No account selected');
      this.walletAddressPreview = addr;

      // 2) Ask backend for a login challenge (only works if wallet is already linked to an account)
      const challenge = await new Promise<{ message: string }>((resolve, reject) => {
        this.authApi.walletLoginChallenge({ walletAddress: addr as any }).subscribe({
          next: resolve,
          error: reject,
        });
      });

      // 3) Sign and login
      const signature = (await eth.request({
        method: 'personal_sign',
        params: [challenge.message, addr],
      })) as string;

      const res = await new Promise<{ token: string; user: any }>((resolve, reject) => {
        this.authApi.walletLogin({ walletAddress: addr as any, signature }).subscribe({
          next: resolve,
          error: reject,
        });
      });

      this.session.setSession(res.token, res.user?.email ?? null, res.user?.walletAddress ?? addr);
      this.router.navigate(['/acceuil-client']);
    } catch (e: any) {
      const raw = e?.error?.message ?? e?.message ?? e ?? 'Erreur wallet';
      const msg = String(raw);
      // Improve UX for the expected case: wallet not linked yet
      if (/wallet not linked/i.test(msg) || /not linked/i.test(msg)) {
        this.errorMessage =
          "Wallet non lié à un compte. Connectez-vous d'abord avec email/mot de passe, puis liez votre wallet dans Profil.";
      } else {
        this.errorMessage = msg;
      }
    } finally {
      this.walletConnecting = false;
    }
  }
  
  
  onSubmit() {
    this.errorMessage = '';
    const email = this.email.trim();
    const password = this.password;
    this.authApi.login({ email, password }).subscribe({
      next: (res) => {
        this.session.setSession(res.token, res.user.email, (res.user as any)?.walletAddress ?? null);
        this.router.navigate(['/acceuil-client']);
      },
      error: (err: unknown) => {
        const msg =
          err instanceof HttpErrorResponse
            ? (err.error?.message ?? err.message)
            : 'Erreur de connexion';
        this.errorMessage = String(msg);
      },
    });
  }
}
