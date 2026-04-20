import React from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Bell, X, Mail, Smartphone, Clock, CheckCircle, AlertCircle, CheckCheck, Trash2 } from 'lucide-react'
import { notificationsApi } from '../../api'
import { useAuth } from '../../hooks'
import { formatDate } from '../../utils'
import type { Notification, NotificationType, NotificationStatus } from '../../types'

interface NotificationPanelProps {
  isOpen: boolean
  onClose: () => void
}

const NotificationPanel: React.FC<NotificationPanelProps> = ({ isOpen, onClose }) => {
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const { data: notifications, isLoading } = useQuery({
    queryKey: ['notifications', user?.id],
    queryFn: () => notificationsApi.getByUserId(user!.id, 0, 50),
    enabled: !!user?.id && isOpen,
  })

  const markAsReadMutation = useMutation({
    mutationFn: (notificationId: string) => notificationsApi.markAsRead(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
    },
  })

  const markAllAsReadMutation = useMutation({
    mutationFn: () => notificationsApi.markAllAsRead(user!.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (notificationId: string) => notificationsApi.delete(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
    },
  })

  const unreadCount = notifications?.content?.filter(n => !n.read).length || 0

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="fixed inset-0 bg-black/50" onClick={onClose} />
      <div className="relative min-h-screen flex items-start justify-end p-4">
        <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md max-h-[80vh] overflow-hidden">
          {/* Header */}
          <div className="flex items-center justify-between p-4 border-b border-gray-200">
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-gray-900">Notifications</h2>
              {unreadCount > 0 && (
                <span className="bg-red-100 text-red-700 text-xs font-medium px-2 py-0.5 rounded-full">
                  {unreadCount} unread
                </span>
              )}
            </div>
            <div className="flex items-center gap-2">
              {unreadCount > 0 && (
                <button
                  onClick={() => markAllAsReadMutation.mutate()}
                  disabled={markAllAsReadMutation.isPending}
                  className="text-sm text-primary-600 hover:text-primary-700 font-medium flex items-center gap-1"
                >
                  <CheckCheck className="w-4 h-4" />
                  Mark all read
                </button>
              )}
              <button
                onClick={onClose}
                className="p-1 rounded-lg hover:bg-gray-100 transition-colors"
              >
                <X className="w-5 h-5 text-gray-500" />
              </button>
            </div>
          </div>

          {/* Content */}
          <div className="overflow-y-auto max-h-[60vh]">
            {isLoading ? (
              <div className="flex justify-center py-12">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
              </div>
            ) : notifications?.content && notifications.content.length > 0 ? (
              <div className="divide-y divide-gray-100">
                {notifications.content.map((notification) => (
                  <NotificationItem
                    key={notification.id}
                    notification={notification}
                    onMarkRead={() => markAsReadMutation.mutate(notification.id)}
                    onDelete={() => deleteMutation.mutate(notification.id)}
                    isMarkingRead={markAsReadMutation.isPending}
                    isDeleting={deleteMutation.isPending}
                  />
                ))}
              </div>
            ) : (
              <div className="text-center py-12">
                <Bell className="w-12 h-12 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-500">No notifications yet</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

interface NotificationItemProps {
  notification: Notification
  onMarkRead: () => void
  onDelete: () => void
  isMarkingRead: boolean
  isDeleting: boolean
}

const NotificationItem: React.FC<NotificationItemProps> = ({
  notification,
  onMarkRead,
  onDelete,
  isMarkingRead,
  isDeleting,
}) => {
  const getNotificationIcon = (type: NotificationType) => {
    switch (type) {
      case 'EMAIL':
        return <Mail className="w-5 h-5 text-blue-500" />
      case 'SMS':
        return <Smartphone className="w-5 h-5 text-green-500" />
      case 'PUSH':
      case 'IN_APP':
        return <Bell className="w-5 h-5 text-purple-500" />
      default:
        return <Bell className="w-5 h-5 text-gray-500" />
    }
  }

  const getStatusIcon = (status: NotificationStatus) => {
    switch (status) {
      case 'SENT':
      case 'DELIVERED':
        return <CheckCircle className="w-4 h-4 text-green-500" />
      case 'FAILED':
        return <AlertCircle className="w-4 h-4 text-red-500" />
      default:
        return <Clock className="w-4 h-4 text-yellow-500" />
    }
  }

  return (
    <div className={`p-4 hover:bg-gray-50 transition-colors ${!notification.read ? 'bg-blue-50/50' : ''}`}>
      <div className="flex items-start gap-3">
        <div className="flex-shrink-0 mt-0.5">
          {getNotificationIcon(notification.type)}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <p className={`font-medium text-gray-900 truncate ${!notification.read ? 'font-semibold' : ''}`}>
              {notification.subject}
            </p>
            <div className="flex items-center gap-1">
              {getStatusIcon(notification.status)}
              {!notification.read && (
                <span className="w-2 h-2 bg-blue-500 rounded-full" />
              )}
            </div>
          </div>
          <p className="text-sm text-gray-600 mt-1 line-clamp-2">{notification.content}</p>
          <div className="flex items-center justify-between mt-2">
            <p className="text-xs text-gray-400">{formatDate(notification.createdAt)}</p>
            <div className="flex items-center gap-2">
              {!notification.read && (
                <button
                  onClick={onMarkRead}
                  disabled={isMarkingRead}
                  className="text-xs text-primary-600 hover:text-primary-700 font-medium flex items-center gap-1"
                >
                  <CheckCheck className="w-3 h-3" />
                  Mark read
                </button>
              )}
              <button
                onClick={onDelete}
                disabled={isDeleting}
                className="text-xs text-gray-400 hover:text-red-500 flex items-center gap-1"
              >
                <Trash2 className="w-3 h-3" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default NotificationPanel
