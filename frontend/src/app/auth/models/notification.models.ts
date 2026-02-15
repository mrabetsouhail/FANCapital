export type NotificationType = 'price' | 'security' | 'margin';
export type NotificationPriority = 'low' | 'medium' | 'high';

export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  read: boolean;
  priority: NotificationPriority;
  timestamp: string;
}

export interface NotificationListResponse {
  items: Notification[];
  unreadCount: number;
}
