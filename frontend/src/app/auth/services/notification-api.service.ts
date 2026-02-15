import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AUTH_API_BASE_URL } from '../auth-api.tokens';
import type { Notification, NotificationListResponse } from '../models/notification.models';

@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(AUTH_API_BASE_URL);

  getNotifications(limit = 50) {
    return this.http.get<NotificationListResponse>(`${this.baseUrl}/notifications`, {
      params: { limit },
    });
  }

  markAsRead(notificationId: string) {
    return this.http.post<{ status: string }>(
      `${this.baseUrl}/notifications/${notificationId}/read`,
      {}
    );
  }

  /** Signale une alerte marge LTV (page AST). Crée une notification si LTV >= 75%. */
  reportMarginAlert(ltvPercent: number) {
    return this.http.post<{ status: string }>(`${this.baseUrl}/notifications/margin-alert`, {
      ltvPercent,
    });
  }
}
