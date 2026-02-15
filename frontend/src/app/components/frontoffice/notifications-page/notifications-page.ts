import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { NavbarClient } from '../navbar-client/navbar-client';
import { BackButton } from '../../shared/back-button/back-button';
import { NotificationApiService } from '../../../auth/services/notification-api.service';
import type { Notification } from '../../../auth/models/notification.models';

@Component({
  selector: 'app-notifications-page',
  imports: [CommonModule, RouterModule, NavbarClient, BackButton],
  templateUrl: './notifications-page.html',
  styleUrl: './notifications-page.css',
})
export class NotificationsPage implements OnInit {
  notifications = signal<Notification[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  filterType = signal<string>('all');

  filteredNotifications = computed(() => {
    const list = this.notifications();
    const type = this.filterType();
    if (type === 'all') return list;
    return list.filter((n) => n.type === type);
  });

  unreadCount = computed(() => this.notifications().filter((n) => !n.read).length);

  constructor(private notificationApi: NotificationApiService) {}

  ngOnInit(): void {
    this.loadNotifications();
  }

  loadNotifications(): void {
    this.loading.set(true);
    this.error.set(null);
    this.notificationApi.getNotifications(100).subscribe({
      next: (res) => {
        this.notifications.set(res.items);
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.error.set(e?.error?.message ?? e?.message ?? 'Erreur lors du chargement');
      },
    });
  }

  markAsRead(notification: Notification): void {
    if (notification.read) return;
    this.notificationApi.markAsRead(notification.id).subscribe({
      next: () => {
        const updated = this.notifications().map((n) =>
          n.id === notification.id ? { ...n, read: true } : n
        );
        this.notifications.set(updated);
      },
      error: () => {},
    });
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

  getTimeAgo(timestamp: string): string {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);
    if (diffMins < 1) return "À l'instant";
    if (diffMins < 60) return `Il y a ${diffMins} min`;
    if (diffHours < 24) return `Il y a ${diffHours}h`;
    if (diffDays === 1) return 'Hier';
    return `Il y a ${diffDays} jours`;
  }
}
