import { InjectionToken } from '@angular/core';

export const AUTH_API_BASE_URL = new InjectionToken<string>('AUTH_API_BASE_URL', {
  factory: () => '/api/auth',
});

