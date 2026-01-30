import type { Address } from '../../blockchain/models/fund.models';

export type UserType = 'PARTICULIER' | 'ENTREPRISE';

export interface UserResponse {
  id: string;
  type: UserType;
  email: string;

  // Particulier
  nom?: string;
  prenom?: string;
  cin?: string;
  passportNumber?: string;
  resident?: boolean;
  telephone?: string;

  // Entreprise
  denominationSociale?: string;
  matriculeFiscal?: string;
  nomGerant?: string;
  prenomGerant?: string;
  emailProfessionnel?: string;
}

export interface AuthResponse {
  token: string;
  user: UserResponse;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterParticulierRequest {
  nom: string;
  prenom: string;
  email: string;
  password: string;
  confirmPassword: string;
  cin: string;
  passportNumber: string;
  resident: boolean;
  telephone: string;
}

export interface RegisterEntrepriseRequest {
  denominationSociale: string;
  matriculeFiscal: string;
  nomGerant: string;
  prenomGerant: string;
  emailProfessionnel: string;
  password: string;
  confirmPassword: string;
  telephone: string;
}

