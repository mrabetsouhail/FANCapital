import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { importProvidersFrom } from '@angular/core';

import { routes } from './app.routes';
import { mockBlockchainInterceptor } from './blockchain/mock/mock-blockchain.interceptor';
import { authTokenInterceptor } from './auth/auth-token.interceptor';
import { authErrorInterceptor } from './auth/auth-error.interceptor';
import { SessionService } from './auth/services/session.service';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader, provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { LanguageService } from './i18n/language.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAppInitializer(() => inject(SessionService).initFromStorage()),
    provideAppInitializer(() => inject(LanguageService).init()),
    provideHttpClient(withInterceptors([authTokenInterceptor, mockBlockchainInterceptor, authErrorInterceptor])),
    importProvidersFrom(
      TranslateModule.forRoot({
        loader: {
          provide: TranslateLoader,
          useClass: TranslateHttpLoader,
        },
      })
    ),
    ...provideTranslateHttpLoader({ prefix: '/assets/i18n/', suffix: '.json' }),
    provideRouter(routes)
  ]
};
