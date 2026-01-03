/**
 * Upload Progress Component
 *
 * Displays upload progress with cancel functionality
 */

import { Progress } from '@/components/ui/progress';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { X, Upload } from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import { formatBytes } from '@/lib/utils/format-bytes';

export interface UploadProgressProps {
  progress: number; // 0-100
  fileName?: string;
  bytesUploaded?: number;
  totalBytes?: number;
  onCancel?: () => void;
  className?: string;
}

export function UploadProgress({
  progress,
  fileName,
  bytesUploaded,
  totalBytes,
  onCancel,
  className,
}: UploadProgressProps) {
  const isComplete = progress >= 100;

  return (
    <Card className={cn('p-4', className)}>
      <div className="space-y-3">
        {/* Header */}
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-3 flex-1 min-w-0">
            <div
              className={cn(
                'flex h-10 w-10 items-center justify-center rounded-lg transition-colors',
                isComplete
                  ? 'bg-green-500/10 text-green-500'
                  : 'bg-primary/10 text-primary'
              )}
            >
              <Upload className="h-5 w-5" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate" title={fileName}>
                {fileName || 'Uploading file...'}
              </p>
              <p className="text-xs text-muted-foreground">
                {isComplete ? 'Upload complete' : 'Uploading...'}
              </p>
            </div>
          </div>

          {/* Cancel button */}
          {onCancel && !isComplete && (
            <Button
              variant="ghost"
              size="icon"
              onClick={onCancel}
              className="flex-shrink-0 h-8 w-8"
              aria-label="Cancel upload"
            >
              <X className="h-4 w-4" />
            </Button>
          )}
        </div>

        {/* Progress bar */}
        <div className="space-y-1">
          <Progress
            value={progress}
            className="h-2"
            indicatorClassName={cn(
              'transition-all',
              isComplete && 'bg-green-500'
            )}
          />
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>{Math.round(progress)}%</span>
            {bytesUploaded !== undefined && totalBytes !== undefined && (
              <span>
                {formatBytes(bytesUploaded)} / {formatBytes(totalBytes)}
              </span>
            )}
          </div>
        </div>

        {/* Estimated time remaining (optional) */}
        {!isComplete && bytesUploaded !== undefined && totalBytes !== undefined && (
          <div className="text-xs text-muted-foreground">
            {getEstimatedTimeRemaining(bytesUploaded, totalBytes, progress)}
          </div>
        )}
      </div>
    </Card>
  );
}

/**
 * Calculate estimated time remaining (basic implementation)
 */
function getEstimatedTimeRemaining(
  bytesUploaded: number,
  totalBytes: number,
  progress: number
): string {
  if (progress === 0) return 'Calculating...';

  const remainingBytes = totalBytes - bytesUploaded;
  const uploadSpeed = bytesUploaded / progress; // bytes per percent

  if (uploadSpeed === 0) return 'Calculating...';

  const remainingPercent = 100 - progress;
  const secondsRemaining = (remainingBytes / uploadSpeed) * remainingPercent;

  if (secondsRemaining < 60) {
    return `About ${Math.ceil(secondsRemaining)} seconds remaining`;
  } else {
    const minutes = Math.ceil(secondsRemaining / 60);
    return `About ${minutes} ${minutes === 1 ? 'minute' : 'minutes'} remaining`;
  }
}
