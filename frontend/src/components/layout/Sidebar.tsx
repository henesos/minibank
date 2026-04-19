import React from 'react';
import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Wallet, ArrowLeftRight, User, Settings, HelpCircle } from 'lucide-react';

interface SidebarProps { isOpen: boolean; onClose: () => void; }

const navigation = [
  { name: 'Dashboard', href: '/dashboard', icon: LayoutDashboard },
  { name: 'Accounts', href: '/accounts', icon: Wallet },
  { name: 'Transactions', href: '/transactions', icon: ArrowLeftRight },
  { name: 'Profile', href: '/profile', icon: User },
];

const secondaryNavigation = [
  { name: 'Settings', href: '/settings', icon: Settings },
  { name: 'Help', href: '/help', icon: HelpCircle },
];

const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose }) => (
  <>
    {isOpen && <div className="fixed inset-0 bg-black bg-opacity-50 z-20 lg:hidden" onClick={onClose} />}
    <aside className={`fixed top-0 left-0 z-30 h-full w-64 bg-white border-r border-gray-200 transform transition-transform duration-300 ease-in-out lg:translate-x-0 lg:static lg:z-0 ${isOpen ? 'translate-x-0' : '-translate-x-full'}`}>
      <div className="flex flex-col h-full">
        <div className="h-16 flex items-center px-6 border-b border-gray-200">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 bg-primary-600 rounded-lg flex items-center justify-center"><span className="text-white font-bold text-sm">MB</span></div>
            <span className="font-semibold text-gray-900">MiniBank</span>
          </div>
        </div>
        <nav className="flex-1 p-4 space-y-1">
          {navigation.map((item) => (
            <NavLink key={item.name} to={item.href} onClick={onClose} className={({ isActive }: { isActive: boolean }) => `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? 'bg-primary-50 text-primary-700' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'}`}>
              <item.icon className="w-5 h-5" />{item.name}
            </NavLink>
          ))}
        </nav>
        <div className="p-4 border-t border-gray-200 space-y-1">
          {secondaryNavigation.map((item) => (
            <NavLink key={item.name} to={item.href} onClick={onClose} className={({ isActive }: { isActive: boolean }) => `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? 'bg-primary-50 text-primary-700' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'}`}>
              <item.icon className="w-5 h-5" />{item.name}
            </NavLink>
          ))}
        </div>
      </div>
    </aside>
  </>
);

export default Sidebar;
