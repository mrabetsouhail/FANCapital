import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { mockBlockchainInterceptor } from './blockchain/mock/mock-blockchain.interceptor';
import { authTokenInterceptor } from './auth/auth-token.interceptor';
import { authErrorInterceptor } from './auth/auth-error.interceptor';
import { SessionService } from './auth/services/session.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAppInitializer(() => inject(SessionService).initFromStorage()),
    provideHttpClient(withInterceptors([authTokenInterceptor, mockBlockchainInterceptor, authErrorInterceptor])),
    provideRouter(routes)
  ]
};
