import React from 'react'
import { X, CheckCircle, AlertCircle, AlertTriangle, Info } from 'lucide-react'
import { cn } from '../../utils/cn'
import { useNotification } from '../../hooks'

const NotificationToast: React.FC = () => {
  const { notifications, remove } = useNotification()

  if (notifications.length === 0) return null

  const icons = {
    success: CheckCircle,
    error: AlertCircle,
    warning: AlertTriangle,
    info: Info,
  }

  const colors = {
    success: 'bg-green-50 border-green-200 text-green-800',
    error: 'bg-red-50 border-red-200 text-red-800',
    warning: 'bg-yellow-50 border-yellow-200 text-yellow-800',
    info: 'bg-blue-50 border-blue-200 text-blue-800',
  }

  const iconColors = {
    success: 'text-green-500',
    error: 'text-red-500',
    warning: 'text-yellow-500',
    info: 'text-blue-500',
  }

  return (
    <div className="fixed top-4 right-4 z-50 space-y-2">
      {notifications.map((notification) => {
        const Icon = icons[notification.type]
        return (
          <div
            key={notification.id}
            className={cn(
              'flex items-center gap-3 px-4 py-3 rounded-lg border shadow-lg animate-in slide-in-from-right',
              colors[notification.type]
            )}
          >
            <Icon className={cn('w-5 h-5', iconColors[notification.type])} />
            <p className="text-sm font-medium">{notification.message}</p>
            <button
              onClick={() => remove(notification.id)}
              className="ml-2 p-1 rounded hover:bg-black/5"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        )
      })}
    </div>
  )
}

export default NotificationToast
