import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthApiService } from '../auth/services/auth-api.service';
import { SessionService } from '../auth/services/session.service';

export const backofficeGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const authApi = inject(AuthApiService);
  const router = inject(Router);

  if (!session.isAuthenticated()) {
    router.navigate(['']);
    return false;
  }

  // Best-effort admin check from /me (server-truth).
  return new Promise<boolean>((resolve) => {
    authApi.me().subscribe({
      next: (u) => {
        if ((u as any)?.isBackofficeAdmin) {
          resolve(true);
          return;
        }
        router.navigate(['/acceuil-client']);
        resolve(false);
      },
      error: () => {
        router.navigate(['']);
        resolve(false);
      },
    });
  });
};

