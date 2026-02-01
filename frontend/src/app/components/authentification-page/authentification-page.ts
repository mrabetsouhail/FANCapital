import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthApiService } from '../../auth/services/auth-api.service';
import { SessionService } from '../../auth/services/session.service';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService, type AppLang } from '../../i18n/language.service';

@Component({
  selector: 'app-authentification-page',
  imports: [FormsModule, RouterLink, TranslateModule],
  templateUrl: './authentification-page.html',
  styleUrl: './authentification-page.css',
})
export class AuthentificationPage {
  email: string = '';
  password: string = '';
  logoPath: string = '/fancapital_logo.jpg';
  errorMessage: string = '';

  lang: AppLang = 'fr';
  
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
