import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionService } from './services/session.service';

export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        // 401 => token expired/invalid, clear session
        // 403 => forbidden (e.g. backoffice), do NOT logout
        if (err.status === 401) {
          session.clearSession(false);
          router.navigate(['']);
        }
      }
      return throwError(() => err);
    })
  );
};

