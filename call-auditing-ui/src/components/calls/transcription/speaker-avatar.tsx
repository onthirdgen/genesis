/**
 * Speaker Avatar Component
 *
 * Displays speaker avatar with color coding by role
 */

import { User, Headphones, HelpCircle } from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import type { SpeakerRole } from '@/lib/types/transcription';

export interface SpeakerAvatarProps {
  role: SpeakerRole;
  name?: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export function SpeakerAvatar({
  role,
  name,
  size = 'md',
  className,
}: SpeakerAvatarProps) {
  const config = getSpeakerConfig(role);
  const sizeClasses = {
    sm: 'h-6 w-6',
    md: 'h-8 w-8',
    lg: 'h-10 w-10',
  };

  const iconSizeClasses = {
    sm: 'h-3 w-3',
    md: 'h-4 w-4',
    lg: 'h-5 w-5',
  };

  return (
    <div
      className={cn(
        'flex items-center justify-center rounded-full',
        sizeClasses[size],
        config.bgColor,
        config.textColor,
        className
      )}
      title={name || config.label}
      aria-label={`${config.label} speaker`}
    >
      {name ? (
        <span className="text-xs font-semibold uppercase">
          {name.charAt(0)}
        </span>
      ) : (
        <config.icon className={iconSizeClasses[size]} />
      )}
    </div>
  );
}

function getSpeakerConfig(role: SpeakerRole) {
  switch (role) {
    case 'AGENT':
      return {
        label: 'Agent',
        icon: Headphones,
        bgColor: 'bg-blue-500/10',
        textColor: 'text-blue-600 dark:text-blue-400',
      };

    case 'CUSTOMER':
      return {
        label: 'Customer',
        icon: User,
        bgColor: 'bg-purple-500/10',
        textColor: 'text-purple-600 dark:text-purple-400',
      };

    case 'UNKNOWN':
    default:
      return {
        label: 'Unknown',
        icon: HelpCircle,
        bgColor: 'bg-gray-500/10',
        textColor: 'text-gray-600 dark:text-gray-400',
      };
  }
}
