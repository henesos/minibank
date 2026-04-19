import React from 'react'
import { Menu, Bell } from 'lucide-react'

interface HeaderProps {
  onMenuClick: () => void
}

const Header: React.FC<HeaderProps> = ({ onMenuClick }) => {
  return (
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
        <button className="p-2 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 relative">
          <Bell className="w-5 h-5" />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full" />
        </button>
      </div>
    </header>
  )
}

export default Header
