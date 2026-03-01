import { Routes } from '@angular/router';
import { AuthentificationPage } from './components/authentification-page/authentification-page';
import { SigninPage } from './components/signin-page/signin-page';
import { AcceuilClientPage } from './components/frontoffice/acceuil-client-page/acceuil-client-page';
import { PasserOrdrePage } from './components/frontoffice/passer-ordre-page/passer-ordre-page';
import { TransactionPage } from './components/frontoffice/transaction-page/transaction-page';
import { ProfilePage } from './components/frontoffice/profile-page/profile-page';
import { NotificationsPage } from './components/frontoffice/notifications-page/notifications-page';
import { CreditPage } from './components/frontoffice/credit-page/credit-page';
import { AvanceSurTitrePage } from './components/frontoffice/avance-sur-titre-page/avance-sur-titre-page';
import { KycPage } from './components/frontoffice/kyc-page/kyc-page';
import { PortfolioPage } from './components/frontoffice/portfolio-page/portfolio-page';
import { FiscalDashboardPage } from './components/backoffice/fiscal-dashboard-page/fiscal-dashboard-page';
import { KycDashboardPage } from './components/backoffice/kyc-dashboard-page/kyc-dashboard-page';
import { AuditRegistryPage } from './components/backoffice/audit-registry-page/audit-registry-page';
import { FeeWalletPage } from './components/backoffice/fee-wallet-page/fee-wallet-page';
import { MultiSigPage } from './components/backoffice/multisig-page/multisig-page';
import { OrderBookBackofficePage } from './components/backoffice/order-book-backoffice-page/order-book-backoffice-page';
import { SubscriptionsBackofficePage } from './components/backoffice/subscriptions-backoffice-page/subscriptions-backoffice-page';
import { EscrowBackofficePage } from './components/backoffice/escrow-backoffice-page/escrow-backoffice-page';
import { CompartmentsBackofficePage } from './components/backoffice/compartments-backoffice-page/compartments-backoffice-page';
import { authGuard } from './auth/auth.guard';
import { clientOnlyGuard } from './auth/client-only.guard';
import { backofficeGuard } from './backoffice/backoffice.guard';
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
    canActivate: [authGuard],
  },
  {
    path: 'portefeuille',
    component: PortfolioPage,
    canActivate: [authGuard],
  },
  {
    path: 'passer-ordre',
    component: PasserOrdrePage,
    canActivate: [authGuard, clientOnlyGuard],
  },
  {
    path: 'transaction',
    component: TransactionPage,
    canActivate: [authGuard],
  },
  {
    path: 'profil',
    component: ProfilePage,
    canActivate: [authGuard],
  },
  {
    path: 'notifications',
    component: NotificationsPage,
    canActivate: [authGuard],
  },
  {
    path: 'credit',
    component: CreditPage,
    canActivate: [authGuard],
  },
  {
    path: 'avance-sur-titre',
    component: AvanceSurTitrePage,
    canActivate: [authGuard],
  },
  {
    path: 'kyc',
    component: KycPage,
    canActivate: [authGuard],
  },
  {
    path: 'backoffice/fiscal',
    component: FiscalDashboardPage,
    canActivate: [backofficeGuard],
  },
  {
    path: 'backoffice/kyc',
    component: KycDashboardPage,
    canActivate: [backofficeGuard],
  },
  {
    path: 'backoffice/audit',
    component: AuditRegistryPage,
    canActivate: [backofficeGuard],
  },
  {
    path: 'backoffice/fees',
    component: FeeWalletPage,
    canActivate: [backofficeGuard],
  },
  {
    path: 'backoffice/multisig',
    component: MultiSigPage,
    canActivate: [backofficeGuard],
  },
  {
    path: 'backoffice/orderbook',
    component: OrderBookBackofficePage,
    canActivate: [backofficeGuard],
  },
  {
    path: 'backoffice/subscriptions',
    component: SubscriptionsBackofficePage,
    canActivate: [backofficeGuard],
  },
  {
    path: 'backoffice/escrow',
    component: EscrowBackofficePage,
    canActivate: [backofficeGuard],
  },
  {
    path: 'backoffice/compartments',
    component: CompartmentsBackofficePage,
    canActivate: [backofficeGuard],
  },
];
