'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  Home,
  Phone,
  BarChart3,
  MessageSquare,
  CheckCircle2,
  Settings,
  LogOut,
} from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import { useAuthStore } from '@/lib/stores/auth-store';
import { Button } from '@/components/ui/button';

const navigation = [
  { name: 'Dashboard', href: '/dashboard', icon: Home },
  { name: 'Calls', href: '/dashboard/calls', icon: Phone },
  { name: 'Analytics', href: '/dashboard/analytics', icon: BarChart3 },
  { name: 'Voice of Customer', href: '/dashboard/voc', icon: MessageSquare },
  { name: 'Compliance', href: '/dashboard/compliance', icon: CheckCircle2 },
  { name: 'Settings', href: '/dashboard/settings', icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuthStore();

  return (
    <div className="flex h-full flex-col bg-gray-900 text-white">
      {/* Logo */}
      <div className="flex h-16 items-center px-6 border-b border-gray-800">
        <h1 className="text-xl font-bold">Call Auditing</h1>
      </div>

      {/* User info */}
      <div className="px-6 py-4 border-b border-gray-800">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-full bg-primary flex items-center justify-center font-semibold">
            {user?.fullName?.charAt(0).toUpperCase() || 'U'}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium truncate">{user?.fullName || 'User'}</p>
            <p className="text-xs text-gray-400 truncate">{user?.email || 'user@example.com'}</p>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {navigation.map((item) => {
          const isActive = pathname === item.href;
          const Icon = item.icon;

          return (
            <Link
              key={item.name}
              href={item.href}
              className={cn(
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-gray-800 text-white'
                  : 'text-gray-300 hover:bg-gray-800 hover:text-white'
              )}
            >
              <Icon className="h-5 w-5" aria-hidden="true" />
              {item.name}
            </Link>
          );
        })}
      </nav>

      {/* Logout button */}
      <div className="p-4 border-t border-gray-800">
        <Button
          variant="ghost"
          className="w-full justify-start text-gray-300 hover:text-white hover:bg-gray-800"
          onClick={logout}
        >
          <LogOut className="mr-2 h-5 w-5" />
          Sign out
        </Button>
      </div>
    </div>
  );
}
