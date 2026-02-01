import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { AUTH_API_BASE_URL } from '../auth-api.tokens';
import type {
  AuthResponse,
  LoginRequest,
  RegisterEntrepriseRequest,
  RegisterParticulierRequest,
  UserResponse,
  WalletChallengeResponse,
  WalletLoginChallengeRequest,
  WalletLoginRequest,
  WalletConfirmRequest,
  WalletConfirmResponse,
} from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(AUTH_API_BASE_URL);

  login(req: LoginRequest) {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, req);
  }

  registerParticulier(req: RegisterParticulierRequest) {
    return this.http.post<AuthResponse>(`${this.baseUrl}/register/particulier`, req);
  }

  registerEntreprise(req: RegisterEntrepriseRequest) {
    return this.http.post<AuthResponse>(`${this.baseUrl}/register/entreprise`, req);
  }

  me() {
    return this.http.get<UserResponse>(`${this.baseUrl}/me`);
  }

  walletChallenge() {
    return this.http.post<WalletChallengeResponse>(`${this.baseUrl}/wallet/challenge`, {});
  }

  walletConfirm(req: WalletConfirmRequest) {
    return this.http.post<WalletConfirmResponse>(`${this.baseUrl}/wallet/confirm`, req);
  }

  walletLoginChallenge(req: WalletLoginChallengeRequest) {
    return this.http.post<WalletChallengeResponse>(`${this.baseUrl}/wallet/login/challenge`, req);
  }

  walletLogin(req: WalletLoginRequest) {
    return this.http.post<AuthResponse>(`${this.baseUrl}/wallet/login`, req);
  }
}

