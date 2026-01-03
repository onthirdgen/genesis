/**
 * WebSocket Status Indicator
 *
 * Displays connection status for WebSocket with auto-hide after successful connection
 */

import { useEffect, useState } from 'react';
import { ConnectionStatus } from '@/lib/types/websocket';
import { cn } from '@/lib/utils/cn';

export interface WebSocketStatusProps {
  status: ConnectionStatus;
  className?: string;
  autoHideDelay?: number; // Delay in ms before hiding when connected (default: 3000)
}

export function WebSocketStatus({
  status,
  className,
  autoHideDelay = 3000,
}: WebSocketStatusProps) {
  const [isVisible, setIsVisible] = useState(true);

  // Auto-hide after successful connection
  useEffect(() => {
    if (status === ConnectionStatus.CONNECTED && autoHideDelay > 0) {
      const timer = setTimeout(() => {
        setIsVisible(false);
      }, autoHideDelay);

      return () => clearTimeout(timer);
    } else if (status !== ConnectionStatus.CONNECTED) {
      setIsVisible(true);
    }
  }, [status, autoHideDelay]);

  // Don't render if hidden
  if (!isVisible) {
    return null;
  }

  const statusConfig = getStatusConfig(status);

  return (
    <div
      className={cn(
        'fixed bottom-4 right-4 z-50 flex items-center gap-2 rounded-lg border px-4 py-2 shadow-lg transition-all',
        statusConfig.bgColor,
        statusConfig.borderColor,
        statusConfig.textColor,
        className
      )}
      role="status"
      aria-live="polite"
    >
      {/* Status indicator dot */}
      <div className="relative flex h-3 w-3">
        <span
          className={cn(
            'absolute inline-flex h-full w-full rounded-full opacity-75',
            statusConfig.pulseColor,
            status === ConnectionStatus.CONNECTING ||
              status === ConnectionStatus.RECONNECTING
              ? 'animate-ping'
              : ''
          )}
        />
        <span
          className={cn(
            'relative inline-flex h-3 w-3 rounded-full',
            statusConfig.dotColor
          )}
        />
      </div>

      {/* Status text */}
      <span className="text-sm font-medium">{statusConfig.label}</span>
    </div>
  );
}

/**
 * Get status configuration (colors, labels) for each status
 */
function getStatusConfig(status: ConnectionStatus) {
  switch (status) {
    case ConnectionStatus.CONNECTING:
      return {
        label: 'Connecting...',
        bgColor: 'bg-blue-50 dark:bg-blue-950',
        borderColor: 'border-blue-200 dark:border-blue-800',
        textColor: 'text-blue-700 dark:text-blue-300',
        dotColor: 'bg-blue-500',
        pulseColor: 'bg-blue-400',
      };

    case ConnectionStatus.CONNECTED:
      return {
        label: 'Connected',
        bgColor: 'bg-green-50 dark:bg-green-950',
        borderColor: 'border-green-200 dark:border-green-800',
        textColor: 'text-green-700 dark:text-green-300',
        dotColor: 'bg-green-500',
        pulseColor: 'bg-green-400',
      };

    case ConnectionStatus.RECONNECTING:
      return {
        label: 'Reconnecting...',
        bgColor: 'bg-yellow-50 dark:bg-yellow-950',
        borderColor: 'border-yellow-200 dark:border-yellow-800',
        textColor: 'text-yellow-700 dark:text-yellow-300',
        dotColor: 'bg-yellow-500',
        pulseColor: 'bg-yellow-400',
      };

    case ConnectionStatus.ERROR:
      return {
        label: 'Connection Error',
        bgColor: 'bg-red-50 dark:bg-red-950',
        borderColor: 'border-red-200 dark:border-red-800',
        textColor: 'text-red-700 dark:text-red-300',
        dotColor: 'bg-red-500',
        pulseColor: 'bg-red-400',
      };

    case ConnectionStatus.DISCONNECTED:
    default:
      return {
        label: 'Disconnected',
        bgColor: 'bg-gray-50 dark:bg-gray-950',
        borderColor: 'border-gray-200 dark:border-gray-800',
        textColor: 'text-gray-700 dark:text-gray-300',
        dotColor: 'bg-gray-500',
        pulseColor: 'bg-gray-400',
      };
  }
}
