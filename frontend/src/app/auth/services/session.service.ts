import { Injectable } from '@angular/core';
import { Router } from '@angular/router';

type JwtPayload = {
  exp?: number; // seconds
  email?: string;
  sub?: string;
  type?: string;
};

@Injectable({ providedIn: 'root' })
export class SessionService {
  private logoutTimer: number | null = null;

  constructor(private router: Router) {}

  initFromStorage() {
    // Called once at app bootstrap
    this.scheduleAutoLogout();
  }

  getToken(): string | null {
    return localStorage.getItem('authToken');
  }

  isAuthenticated(): boolean {
    const t = this.getToken();
    if (!t) return false;
    const expMs = this.getTokenExpiryMs(t);
    return expMs == null ? true : expMs > Date.now();
  }

  setSession(token: string, email?: string | null, walletAddress?: string | null) {
    localStorage.setItem('authToken', token);
    if (email) localStorage.setItem('userEmail', email);
    if (walletAddress && walletAddress.startsWith('0x') && walletAddress.length === 42) {
      localStorage.setItem('walletAddress', walletAddress);
    }
    this.scheduleAutoLogout();
  }

  clearSession(redirectToLogin: boolean = true) {
    localStorage.removeItem('authToken');
    localStorage.removeItem('userEmail');
    // keep walletAddress? For security, clear it too.
    localStorage.removeItem('walletAddress');
    this.clearTimer();
    if (redirectToLogin) this.router.navigate(['']);
  }

  scheduleAutoLogout() {
    this.clearTimer();
    const t = this.getToken();
    if (!t) return;
    const expMs = this.getTokenExpiryMs(t);
    if (expMs == null) return;
    const delay = expMs - Date.now();
    if (delay <= 0) {
      this.clearSession(true);
      return;
    }
    // Add a small buffer (1s) to ensure backend also considers it expired.
    this.logoutTimer = window.setTimeout(() => this.clearSession(true), delay + 1000);
  }

  private clearTimer() {
    if (this.logoutTimer != null) {
      window.clearTimeout(this.logoutTimer);
      this.logoutTimer = null;
    }
  }

  private getTokenExpiryMs(token: string): number | null {
    const payload = this.decodeJwtPayload(token);
    if (!payload?.exp) return null;
    return payload.exp * 1000;
  }

  private decodeJwtPayload(token: string): JwtPayload | null {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const b64url = parts[1];
      const b64 = b64url.replace(/-/g, '+').replace(/_/g, '/');
      const padded = b64.padEnd(Math.ceil(b64.length / 4) * 4, '=');
      const json = atob(padded);
      return JSON.parse(json);
    } catch {
      return null;
    }
  }
}

