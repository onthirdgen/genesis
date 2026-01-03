/**
 * File Preview Component
 *
 * Displays uploaded file information with remove option
 */

import { FileAudio, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils/cn';
import { formatBytes } from '@/lib/utils/format-bytes';
import { formatTime } from '@/lib/utils/format-time';
import { getAudioFormat } from '@/lib/utils/audio-utils';

export interface FilePreviewProps {
  file: File;
  duration?: number;
  onRemove?: () => void;
  className?: string;
}

export function FilePreview({
  file,
  duration,
  onRemove,
  className,
}: FilePreviewProps) {
  const format = getAudioFormat(file);

  return (
    <Card className={cn('p-4', className)}>
      <div className="flex items-start gap-4">
        {/* File icon */}
        <div className="flex-shrink-0">
          <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10">
            <FileAudio className="h-6 w-6 text-primary" />
          </div>
        </div>

        {/* File info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate" title={file.name}>
                {file.name}
              </p>
              <div className="flex flex-wrap items-center gap-2 mt-1">
                <Badge variant="secondary" size="sm">
                  {format}
                </Badge>
                <span className="text-xs text-muted-foreground">
                  {formatBytes(file.size)}
                </span>
                {duration !== undefined && (
                  <span className="text-xs text-muted-foreground">
                    • {formatTime(duration)}
                  </span>
                )}
              </div>
            </div>

            {/* Remove button */}
            {onRemove && (
              <Button
                variant="ghost"
                size="icon"
                onClick={onRemove}
                className="flex-shrink-0 h-8 w-8"
                aria-label="Remove file"
              >
                <X className="h-4 w-4" />
              </Button>
            )}
          </div>
        </div>
      </div>
    </Card>
  );
}
