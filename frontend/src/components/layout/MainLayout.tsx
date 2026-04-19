import React, { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'
import NotificationToast from './NotificationToast'

const MainLayout: React.FC = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Sidebar */}
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* Main content */}
      <div className="lg:pl-64">
        <Header onMenuClick={() => setSidebarOpen(true)} />

        <main className="p-6">
          <Outlet />
        </main>
      </div>

      {/* Notifications */}
      <NotificationToast />
    </div>
  )
}

export default MainLayout
