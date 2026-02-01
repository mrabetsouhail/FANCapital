import { Component, signal, HostListener, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import type { PortfolioPosition } from '../../../blockchain/models/portfolio.models';
import { SessionService } from '../../../auth/services/session.service';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService, type AppLang } from '../../../i18n/language.service';

interface Notification {
  id: number;
  type: 'price' | 'security' | 'margin';
  title: string;
  message: string;
  timestamp: Date;
  read: boolean;
  priority: 'low' | 'medium' | 'high';
}

@Component({
  selector: 'app-navbar-client',
  imports: [CommonModule, RouterLink, TranslateModule],
  templateUrl: './navbar-client.html',
  styleUrl: './navbar-client.css',
})
export class NavbarClient implements OnInit {
  // Wallets Data
  tokensAlpha = signal<number>(0);
  tokensBeta = signal<number>(0);
  cashAmount = signal<number>(0);
  creditAmount = signal<number>(0);
  
  // Profile Data
  memberLevel = signal<string>('Gold'); // Silver, Gold, Platinum, etc.
  isBackofficeAdmin = signal<boolean>(false);
  backofficeRole = signal<string>('NONE');
  
  // Logo
  logoPath: string = 'assets/images/fancapital_logo.svg';
  private readonly fallbackLogoPath: string = 'assets/images/fancapital_logo.jpg';
  
  // Notifications
  hasNotifications = signal<boolean>(false);
  notifications = signal<Notification[]>([
    {
      id: 1,
      type: 'price',
      title: 'Token Alpha - Seuil atteint',
      message: 'Le Token Alpha a atteint 130.00 TND (votre seuil: 130.00 TND)',
      timestamp: new Date(Date.now() - 15 * 60 * 1000), // Il y a 15 minutes
      read: false,
      priority: 'high'
    },
    {
      id: 2,
      type: 'security',
      title: 'Nouvelle connexion détectée',
      message: 'Connexion depuis un nouvel appareil à Tunis, Tunisie',
      timestamp: new Date(Date.now() - 2 * 60 * 60 * 1000), // Il y a 2 heures
      read: false,
      priority: 'medium'
    },
    {
      id: 3,
      type: 'margin',
      title: 'Alerte de Marge - LTV Critique',
      message: 'Votre ratio LTV est à 82.5%. Risque de liquidation si le prix baisse de 8%',
      timestamp: new Date(Date.now() - 30 * 60 * 1000), // Il y a 30 minutes
      read: false,
      priority: 'high'
    },
    {
      id: 4,
      type: 'price',
      title: 'Token Beta - Hausse significative',
      message: 'Le Token Beta a augmenté de 5.2% en 24h (85.25 TND → 89.68 TND)',
      timestamp: new Date(Date.now() - 4 * 60 * 60 * 1000), // Il y a 4 heures
      read: true,
      priority: 'medium'
    },
    {
      id: 5,
      type: 'security',
      title: 'Retrait important effectué',
      message: 'Un retrait de 2,500.00 TND a été effectué vers votre compte bancaire',
      timestamp: new Date(Date.now() - 6 * 60 * 60 * 1000), // Il y a 6 heures
      read: true,
      priority: 'medium'
    },
    {
      id: 6,
      type: 'price',
      title: 'Token Alpha - Objectif de prix',
      message: 'Le Token Alpha approche votre objectif de vente à 140.00 TND (actuel: 135.50 TND)',
      timestamp: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000), // Il y a 1 jour
      read: true,
      priority: 'low'
    }
  ]);
  
  // Menu state
  isMenuOpen = signal<boolean>(false);
  isNotificationsSubmenuOpen = signal<boolean>(false);

  // Language
  lang = signal<AppLang>('fr');
  readonly langs: AppLang[] = ['fr', 'en', 'ar'];

  constructor(
    private router: Router,
    private authApi: AuthApiService,
    private blockchainApi: BlockchainApiService,
    private session: SessionService,
    private language: LanguageService
  ) {
    // credit remains off-chain for now (UI placeholder)
    this.creditAmount.set(10000.0);
    this.hasNotifications.set(this.notifications().some((n) => !n.read));
  }

  onLogoError() {
    if (this.logoPath !== this.fallbackLogoPath) {
      this.logoPath = this.fallbackLogoPath;
    }
  }

  unreadCount = signal<number>(this.notifications().filter(n => !n.read).length);

  ngOnInit(): void {
    this.lang.set(this.language.current);
    // Best-effort: if user is logged in and has a WaaS wallet, load on-chain balances.
    this.authApi.me().subscribe({
      next: (u) => {
        this.isBackofficeAdmin.set(!!(u as any)?.isBackofficeAdmin);
        this.backofficeRole.set(String((u as any)?.backofficeRole ?? 'NONE').toUpperCase());
        const wallet = (u as any)?.walletAddress as string | undefined;
        if (!wallet || !wallet.startsWith('0x') || wallet.length !== 42) return;
        localStorage.setItem('walletAddress', wallet);
        this.refreshWallet(wallet);
      },
      error: () => {},
    });
  }

  setLang(l: AppLang) {
    this.language.use(l);
    this.lang.set(l);
  }

  private refreshWallet(wallet: string) {
    this.blockchainApi.getPortfolio(wallet as any).subscribe({
      next: (p) => {
        const atlas = this.pickFund(p.positions, 'atlas', 0);
        const didon = this.pickFund(p.positions, 'didon', 1);
        this.tokensAlpha.set(atlas ? this.from1e8(atlas.balanceTokens) : 0);
        this.tokensBeta.set(didon ? this.from1e8(didon.balanceTokens) : 0);
        this.cashAmount.set(p.cashBalanceTnd ? this.from1e8(p.cashBalanceTnd) : 0);
      },
      error: () => {},
    });
  }

  private pickFund(positions: PortfolioPosition[], nameHint: string, idHint: number): PortfolioPosition | null {
    const byName =
      positions.find((x) => (x.name ?? '').toLowerCase().includes(nameHint)) ??
      positions.find((x) => (x.symbol ?? '').toLowerCase().includes(nameHint));
    if (byName) return byName;
    return positions.find((x) => x.fundId === idHint) ?? null;
  }

  private from1e8(v: string): number {
    return Number(v) / 1e8;
  }

  getNotificationIcon(type: string): string {
    if (type === 'price') return 'M13 7h8m0 0v8m0-8l-8 8-4-4-6 6';
    if (type === 'security') return 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z';
    return 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z';
  }

  getNotificationColor(type: string): string {
    if (type === 'price') return 'text-blue-600 bg-blue-50';
    if (type === 'security') return 'text-green-600 bg-green-50';
    return 'text-red-600 bg-red-50';
  }

  getPriorityColor(priority: string): string {
    if (priority === 'high') return 'bg-red-500';
    if (priority === 'medium') return 'bg-yellow-500';
    return 'bg-gray-400';
  }

  markAsRead(notificationId: number) {
    const updated = this.notifications().map(n => 
      n.id === notificationId ? { ...n, read: true } : n
    );
    this.notifications.set(updated);
    this.hasNotifications.set(updated.some(n => !n.read));
    this.unreadCount.set(updated.filter(n => !n.read).length);
  }

  getTimeAgo(date: Date): string {
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'À l\'instant';
    if (diffMins < 60) return `Il y a ${diffMins} min`;
    if (diffHours < 24) return `Il y a ${diffHours}h`;
    if (diffDays === 1) return 'Hier';
    return `Il y a ${diffDays} jours`;
  }

  onDeposit() {
    this.router.navigate(['/transaction'], { queryParams: { type: 'depot' } });
  }

  onWithdraw() {
    this.router.navigate(['/transaction'], { queryParams: { type: 'retrait' } });
  }

  onProfile() {
    this.router.navigate(['/profil']);
    this.isMenuOpen.set(false);
  }

  toggleMenu() {
    this.isMenuOpen.set(!this.isMenuOpen());
    if (!this.isMenuOpen()) {
      this.isNotificationsSubmenuOpen.set(false);
    }
  }

  toggleNotificationsSubmenu() {
    this.isNotificationsSubmenuOpen.set(!this.isNotificationsSubmenuOpen());
  }

  onPriceAlert() {
    // TODO: Navigate to price alert configuration
    console.log('Alertes de Prix clicked');
    this.isMenuOpen.set(false);
    this.isNotificationsSubmenuOpen.set(false);
  }

  onSecurityAlert() {
    // TODO: Navigate to security alert configuration
    console.log('Alertes de Sécurité clicked');
    this.isMenuOpen.set(false);
    this.isNotificationsSubmenuOpen.set(false);
  }

  onMarginAlert() {
    // TODO: Navigate to margin alert configuration
    console.log('Alertes de Marge clicked');
    this.isMenuOpen.set(false);
    this.isNotificationsSubmenuOpen.set(false);
  }

  onLogout() {
    this.session.clearSession(true);
    this.isMenuOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('.menu-container')) {
      this.isMenuOpen.set(false);
      this.isNotificationsSubmenuOpen.set(false);
    }
  }
}
