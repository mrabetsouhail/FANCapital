import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { mockBlockchainInterceptor } from './blockchain/mock/mock-blockchain.interceptor';
import { authTokenInterceptor } from './auth/auth-token.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([authTokenInterceptor, mockBlockchainInterceptor])),
    provideRouter(routes)
  ]
};
