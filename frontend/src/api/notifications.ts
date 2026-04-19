import apiClient from './client'
import type { PaginatedResponse, Notification, CreateNotificationRequest } from '../types'

export type { NotificationType, NotificationStatus } from '../types'

export interface NotificationApiResponse extends Notification {
  referenceId?: string
  referenceType?: string
  errorMessage?: string
  deliveredAt?: string
}

export const notificationsApi = {
  create: async (data: CreateNotificationRequest): Promise<Notification> => {
    const response = await apiClient.post<Notification>('/api/v1/notifications', data)
    return response.data
  },

  getById: async (notificationId: string): Promise<Notification> => {
    const response = await apiClient.get<Notification>(`/api/v1/notifications/${notificationId}`)
    return response.data
  },

  getByUserId: async (userId: string, page = 0, size = 20): Promise<PaginatedResponse<Notification>> => {
    const response = await apiClient.get<PaginatedResponse<Notification>>(
      `/api/v1/notifications/user/${userId}`,
      { params: { page, size } }
    )
    return response.data
  },

  getUnread: async (userId: string): Promise<Notification[]> => {
    const response = await apiClient.get<Notification[]>(
      `/api/v1/notifications/user/${userId}/unread`
    )
    return response.data
  },

  getUnreadCount: async (userId: string): Promise<number> => {
    const response = await apiClient.get<number>(
      `/api/v1/notifications/user/${userId}/unread/count`
    )
    return response.data
  },

  markAsRead: async (notificationId: string): Promise<Notification> => {
    const response = await apiClient.put<Notification>(
      `/api/v1/notifications/${notificationId}/read`
    )
    return response.data
  },

  markAllAsRead: async (userId: string): Promise<number> => {
    const response = await apiClient.put<number>(
      `/api/v1/notifications/user/${userId}/read-all`
    )
    return response.data
  },

  delete: async (notificationId: string): Promise<void> => {
    await apiClient.delete(`/api/v1/notifications/${notificationId}`)
  },

  send: async (notificationId: string): Promise<Notification> => {
    const response = await apiClient.post<Notification>(
      `/api/v1/notifications/${notificationId}/send`
    )
    return response.data
  },

  getPending: async (): Promise<Notification[]> => {
    const response = await apiClient.get<Notification[]>('/api/v1/notifications/pending')
    return response.data
  },

  processPending: async (): Promise<number> => {
    const response = await apiClient.post<number>('/api/v1/notifications/process')
    return response.data
  },
}
