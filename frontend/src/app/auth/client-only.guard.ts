import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthApiService } from './services/auth-api.service';
import { SessionService } from './services/session.service';

/**
 * Garde qui bloque l'accès des utilisateurs backoffice (admin, compliance, regulator).
 * Utilisé pour la page "Passer ordre" : seuls les investisseurs peuvent acheter/vendre.
 */
export const clientOnlyGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const authApi = inject(AuthApiService);
  const router = inject(Router);

  if (!session.isAuthenticated()) {
    router.navigate(['']);
    return false;
  }

  return new Promise<boolean>((resolve) => {
    authApi.me().subscribe({
      next: (u) => {
        const role = String((u as any)?.backofficeRole ?? 'NONE').toUpperCase();
        const isBackoffice =
          (u as any)?.isBackofficeAdmin ||
          role === 'ADMIN' ||
          role === 'COMPLIANCE' ||
          role === 'REGULATOR';
        if (isBackoffice) {
          router.navigate(['/acceuil-client']);
          resolve(false);
          return;
        }
        resolve(true);
      },
      error: () => {
        router.navigate(['']);
        resolve(false);
      },
    });
  });
};
