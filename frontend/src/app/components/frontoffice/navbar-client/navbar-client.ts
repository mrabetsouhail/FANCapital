import { Component, signal, HostListener, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthApiService } from '../../../auth/services/auth-api.service';
import { BlockchainApiService } from '../../../blockchain/services/blockchain-api.service';
import { NotificationApiService } from '../../../auth/services/notification-api.service';
import { CreditWalletTrackingService } from '../../../auth/services/credit-wallet-tracking.service';
import type { PortfolioPosition } from '../../../blockchain/models/portfolio.models';
import type { SciScoreResult } from '../../../blockchain/models/investor.models';
import type { Notification } from '../../../auth/models/notification.models';
import { SessionService } from '../../../auth/services/session.service';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService, type AppLang } from '../../../i18n/language.service';
import { SciNudgeBanner } from '../sci-nudge-banner/sci-nudge-banner';

@Component({
  selector: 'app-navbar-client',
  imports: [CommonModule, RouterLink, RouterLinkActive, TranslateModule, SciNudgeBanner],
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
  sciScore = signal<SciScoreResult | null>(null);
  isBackofficeAdmin = signal<boolean>(false);
  backofficeRole = signal<string>('NONE');
  
  // Logo
  logoPath: string = 'assets/images/fancapital_logo.svg';
  private readonly fallbackLogoPath: string = 'assets/images/fancapital_logo.jpg';
  
  // Notifications (chargées depuis l'API)
  hasNotifications = signal<boolean>(false);
  notifications = signal<Notification[]>([]);
  
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
    private notificationApi: NotificationApiService,
    private session: SessionService,
    private language: LanguageService,
    private creditTracking: CreditWalletTrackingService
  ) {
    this.creditAmount.set(0);
  }

  onLogoError() {
    if (this.logoPath !== this.fallbackLogoPath) {
      this.logoPath = this.fallbackLogoPath;
    }
  }

  unreadCount = signal<number>(0);

  ngOnInit(): void {
    this.lang.set(this.language.current);
    this.loadNotifications();
    // Best-effort: if user is logged in and has a WaaS wallet, load on-chain balances.
    this.authApi.me().subscribe({
      next: (u) => {
        this.isBackofficeAdmin.set(!!(u as any)?.isBackofficeAdmin);
        this.backofficeRole.set(String((u as any)?.backofficeRole ?? 'NONE').toUpperCase());
        const wallet = (u as any)?.walletAddress as string | undefined;
        if (!wallet || !wallet.startsWith('0x') || wallet.length !== 42) return;
        localStorage.setItem('walletAddress', wallet);
        this.refreshWallet(wallet);
        
        // Set up periodic refresh every 5 seconds to ensure balance is always up-to-date
        setInterval(() => {
          this.refreshWalletBalance();
        }, 5000);
        setInterval(() => this.loadNotifications(), 60_000);
      },
      error: () => {},
    });
    
    // Also try to refresh from localStorage if available
    const savedWallet = localStorage.getItem('walletAddress');
    if (savedWallet && savedWallet.startsWith('0x') && savedWallet.length === 42) {
      this.refreshWallet(savedWallet);
      // Set up periodic refresh
      setInterval(() => {
        this.refreshWalletBalance();
      }, 5000);
    }
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
        const cashBalance = p.cashBalanceTnd ? this.from1e8(p.cashBalanceTnd) : 0;
        const creditDebt = (p as any).creditDebtTnd ? this.from1e8((p as any).creditDebtTnd) : 0;
        this.creditTracking.syncWithCreditDebt(wallet, creditDebt);
        this.cashAmount.set(this.creditTracking.getCashDisplay(cashBalance, creditDebt, wallet));
        this.creditAmount.set(this.creditTracking.getCreditDisplay(cashBalance, creditDebt, wallet));
      },
      error: (err) => {
        console.error('[NavbarClient] Error refreshing wallet:', err);
      },
    });
    // SCI v4.5: fetch score for nudge (near threshold + KYC1)
    this.blockchainApi.getSciScore(wallet).subscribe({
      next: (sci) => this.sciScore.set(sci),
      error: () => this.sciScore.set(null),
    });
  }

  /**
   * Public method to refresh wallet balance from blockchain.
   * Can be called from other components after transactions.
   */
  public refreshWalletBalance() {
    console.log('[NavbarClient] refreshWalletBalance() called');
    const wallet = localStorage.getItem('walletAddress');
    console.log('[NavbarClient] Wallet from localStorage:', wallet);
    if (wallet && wallet.startsWith('0x') && wallet.length === 42) {
      this.refreshWallet(wallet);
    } else {
      // Try to get from auth service
      console.log('[NavbarClient] No wallet in localStorage, fetching from auth service...');
      this.authApi.me().subscribe({
        next: (u) => {
          const walletAddr = (u as any)?.walletAddress as string | undefined;
          console.log('[NavbarClient] Wallet from auth service:', walletAddr);
          if (walletAddr && walletAddr.startsWith('0x') && walletAddr.length === 42) {
            localStorage.setItem('walletAddress', walletAddr);
            this.refreshWallet(walletAddr);
          } else {
            console.warn('[NavbarClient] Invalid wallet address from auth service:', walletAddr);
          }
        },
        error: (err) => {
          console.error('[NavbarClient] Error fetching user from auth service:', err);
        },
      });
    }
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

  loadNotifications(): void {
    this.notificationApi.getNotifications(50).subscribe({
      next: (res) => {
        this.notifications.set(res.items);
        this.unreadCount.set(res.unreadCount);
        this.hasNotifications.set(res.unreadCount > 0);
      },
      error: () => {},
    });
  }

  markAsRead(notificationId: string): void {
    this.notificationApi.markAsRead(notificationId).subscribe({
      next: () => {
        const updated = this.notifications().map(n =>
          n.id === notificationId ? { ...n, read: true } : n
        );
        this.notifications.set(updated);
        const unread = updated.filter(n => !n.read).length;
        this.unreadCount.set(unread);
        this.hasNotifications.set(unread > 0);
      },
      error: () => {},
    });
  }

  getTimeAgo(timestamp: string): string {
    const date = new Date(timestamp);
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
    const willOpen = !this.isMenuOpen();
    this.isMenuOpen.set(willOpen);
    if (willOpen) this.loadNotifications();
    if (!willOpen) this.isNotificationsSubmenuOpen.set(false);
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
