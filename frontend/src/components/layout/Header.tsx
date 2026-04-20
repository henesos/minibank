import React, { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Menu, Bell } from 'lucide-react'
import NotificationPanel from '../notifications/NotificationPanel'
import { notificationsApi } from '../../api'
import { useAuth } from '../../hooks'

interface HeaderProps {
  onMenuClick: () => void
}

const Header: React.FC<HeaderProps> = ({ onMenuClick }) => {
  const [isNotificationOpen, setIsNotificationOpen] = useState(false)
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const { data: unreadCount } = useQuery({
    queryKey: ['notifications', 'unread-count', user?.id],
    queryFn: () => notificationsApi.getUnreadCount(user!.id),
    enabled: !!user?.id,
    refetchInterval: 30000, // Refetch every 30 seconds
  })

  const handleNotificationOpen = () => {
    setIsNotificationOpen(true)
  }

  const handleNotificationClose = () => {
    setIsNotificationOpen(false)
    // Refetch unread count when closing panel
    queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
  }

  return (
    <>
      <header className="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-6">
        {/* Left side */}
        <div className="flex items-center gap-4">
          <button
            onClick={onMenuClick}
            className="lg:hidden p-2 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100"
          >
            <Menu className="w-5 h-5" />
          </button>
          <h1 className="text-lg font-semibold text-gray-900">Dashboard</h1>
        </div>

        {/* Right side */}
        <div className="flex items-center gap-3">
          <button
            onClick={handleNotificationOpen}
            className="p-2 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 relative"
          >
            <Bell className="w-5 h-5" />
            {unreadCount && unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] bg-red-500 text-white text-xs font-bold rounded-full flex items-center justify-center px-1">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>
        </div>
      </header>

      <NotificationPanel
        isOpen={isNotificationOpen}
        onClose={handleNotificationClose}
      />
    </>
  )
}

export default Header
