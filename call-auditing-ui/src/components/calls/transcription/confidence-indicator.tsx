/**
 * Confidence Indicator Component
 *
 * Visual indicator for transcription confidence score
 */

import { cn } from '@/lib/utils/cn';

export interface ConfidenceIndicatorProps {
  confidence: number; // 0-1
  showPercentage?: boolean;
  size?: 'sm' | 'md';
  className?: string;
}

export function ConfidenceIndicator({
  confidence,
  showPercentage = true,
  size = 'md',
  className,
}: ConfidenceIndicatorProps) {
  const percentage = Math.round(confidence * 100);
  const config = getConfidenceConfig(confidence);

  const heightClasses = {
    sm: 'h-1',
    md: 'h-2',
  };

  return (
    <div className={cn('flex items-center gap-2', className)}>
      {/* Progress bar */}
      <div
        className={cn(
          'flex-1 bg-muted rounded-full overflow-hidden',
          heightClasses[size]
        )}
        role="progressbar"
        aria-valuenow={percentage}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`Confidence: ${percentage}%`}
      >
        <div
          className={cn('h-full transition-all', config.bgColor)}
          style={{ width: `${percentage}%` }}
        />
      </div>

      {/* Percentage text */}
      {showPercentage && (
        <span
          className={cn('text-xs font-medium tabular-nums', config.textColor)}
        >
          {percentage}%
        </span>
      )}
    </div>
  );
}

function getConfidenceConfig(confidence: number) {
  if (confidence >= 0.9) {
    // High confidence (90%+)
    return {
      bgColor: 'bg-green-500',
      textColor: 'text-green-600 dark:text-green-400',
    };
  } else if (confidence >= 0.7) {
    // Medium confidence (70-89%)
    return {
      bgColor: 'bg-yellow-500',
      textColor: 'text-yellow-600 dark:text-yellow-400',
    };
  } else {
    // Low confidence (<70%)
    return {
      bgColor: 'bg-red-500',
      textColor: 'text-red-600 dark:text-red-400',
    };
  }
}
