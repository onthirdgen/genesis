/**
 * Upload Status Banner Component
 *
 * Displays success, error, or info messages with dismiss and retry options
 */

import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils/cn';

export type BannerType = 'success' | 'error' | 'info';

export interface UploadStatusBannerProps {
  type: BannerType;
  message: string;
  onDismiss?: () => void;
  onRetry?: () => void;
  autoHideDuration?: number; // in milliseconds
  className?: string;
}

export function UploadStatusBanner({
  type,
  message,
  onDismiss,
  onRetry,
  autoHideDuration,
  className,
}: UploadStatusBannerProps) {
  // Auto-hide for success messages
  if (type === 'success' && autoHideDuration && onDismiss) {
    setTimeout(() => {
      onDismiss();
    }, autoHideDuration);
  }

  const config = getBannerConfig(type);

  return (
    <div
      className={cn(
        'flex items-start gap-3 rounded-lg border p-4',
        config.bgColor,
        config.borderColor,
        config.textColor,
        className
      )}
      role="alert"
      aria-live="polite"
    >
      {/* Icon */}
      <div className="flex-shrink-0 mt-0.5">
        {config.icon}
      </div>

      {/* Message */}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium">{message}</p>
      </div>

      {/* Actions */}
      <div className="flex-shrink-0 flex items-center gap-2">
        {/* Retry button for errors */}
        {type === 'error' && onRetry && (
          <Button
            variant="ghost"
            size="sm"
            onClick={onRetry}
            className={cn('h-8', config.buttonColor)}
          >
            Retry
          </Button>
        )}

        {/* Dismiss button */}
        {onDismiss && (
          <Button
            variant="ghost"
            size="icon"
            onClick={onDismiss}
            className={cn('h-8 w-8', config.buttonColor)}
            aria-label="Dismiss"
          >
            <X className="h-4 w-4" />
          </Button>
        )}
      </div>
    </div>
  );
}

/**
 * Get banner configuration based on type
 */
function getBannerConfig(type: BannerType) {
  switch (type) {
    case 'success':
      return {
        icon: <CheckCircle2 className="h-5 w-5 text-green-500" />,
        bgColor: 'bg-green-50 dark:bg-green-950',
        borderColor: 'border-green-200 dark:border-green-800',
        textColor: 'text-green-900 dark:text-green-100',
        buttonColor: 'text-green-700 hover:text-green-800 dark:text-green-300 dark:hover:text-green-200',
      };

    case 'error':
      return {
        icon: <AlertCircle className="h-5 w-5 text-red-500" />,
        bgColor: 'bg-red-50 dark:bg-red-950',
        borderColor: 'border-red-200 dark:border-red-800',
        textColor: 'text-red-900 dark:text-red-100',
        buttonColor: 'text-red-700 hover:text-red-800 dark:text-red-300 dark:hover:text-red-200',
      };

    case 'info':
    default:
      return {
        icon: <Info className="h-5 w-5 text-blue-500" />,
        bgColor: 'bg-blue-50 dark:bg-blue-950',
        borderColor: 'border-blue-200 dark:border-blue-800',
        textColor: 'text-blue-900 dark:text-blue-100',
        buttonColor: 'text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200',
      };
  }
}
