import { Routes } from '@angular/router';
import { AuthentificationPage } from './components/authentification-page/authentification-page';
import { SigninPage } from './components/signin-page/signin-page';
import { AcceuilClientPage } from './components/frontoffice/acceuil-client-page/acceuil-client-page';
import { PasserOrdrePage } from './components/frontoffice/passer-ordre-page/passer-ordre-page';
import { TransactionPage } from './components/frontoffice/transaction-page/transaction-page';
import { ProfilePage } from './components/frontoffice/profile-page/profile-page';
import { CreditPage } from './components/frontoffice/credit-page/credit-page';
import { AvanceSurTitrePage } from './components/frontoffice/avance-sur-titre-page/avance-sur-titre-page';
import { KycPage } from './components/frontoffice/kyc-page/kyc-page';
import { FiscalDashboardPage } from './components/backoffice/fiscal-dashboard-page/fiscal-dashboard-page';
export const routes: Routes = [
  {
    path: '',
    component: AuthentificationPage,
  },
  {
    path: 'signin',
    component: SigninPage,
  },                            
  {
    path: 'acceuil-client',
    component: AcceuilClientPage,
  },
  {
    path: 'passer-ordre',
    component: PasserOrdrePage,
  },
  {
    path: 'transaction',
    component: TransactionPage,
  },
  {
    path: 'profil',
    component: ProfilePage,
  },
  {
    path: 'credit',
    component: CreditPage,
  },
  {
    path: 'avance-sur-titre',
    component: AvanceSurTitrePage,
  },
  {
    path: 'kyc',
    component: KycPage,
  },
  {
    path: 'backoffice/fiscal',
    component: FiscalDashboardPage,
  },
];
