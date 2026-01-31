import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SessionService } from './services/session.service';

export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(SessionService).getToken();
  if (!token) return next(req);

  // Only attach to our API calls (avoid leaking token to other origins).
  if (!req.url.startsWith('/api/')) return next(req);

  return next(
    req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    })
  );
};

