/**
 * Transcription Segment Component
 *
 * Individual transcription segment with speaker, timestamp, text, and confidence
 */

import { Card } from '@/components/ui/card';
import { SpeakerAvatar } from './speaker-avatar';
import { ConfidenceIndicator } from './confidence-indicator';
import { cn } from '@/lib/utils/cn';
import { formatTime } from '@/lib/utils/format-time';
import type { TranscriptionSegment } from '@/lib/types/transcription';

export interface TranscriptionSegmentProps {
  segment: TranscriptionSegment;
  isActive?: boolean;
  onClick?: (segment: TranscriptionSegment) => void;
  className?: string;
}

export function TranscriptionSegmentComponent({
  segment,
  isActive = false,
  onClick,
  className,
}: TranscriptionSegmentProps) {
  const handleClick = () => {
    onClick?.(segment);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleClick();
    }
  };

  return (
    <Card
      className={cn(
        'p-4 transition-all cursor-pointer hover:border-primary/50 hover:shadow-md',
        isActive && 'border-primary bg-primary/5 shadow-md',
        className
      )}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      tabIndex={0}
      role="button"
      aria-label={`Transcription segment from ${formatTime(segment.startTime)} to ${formatTime(segment.endTime)}`}
    >
      <article className="space-y-3">
        {/* Header: Speaker + Timestamp */}
        <header className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3 flex-1 min-w-0">
            <SpeakerAvatar
              role={segment.speaker.role}
              name={segment.speaker.name}
              size="md"
            />
            <div className="flex-1 min-w-0">
              <h3 className="text-sm font-semibold truncate">
                {segment.speaker.name || segment.speaker.role}
              </h3>
              <p className="text-xs text-muted-foreground">
                {formatTime(segment.startTime)} - {formatTime(segment.endTime)}
              </p>
            </div>
          </div>

          {/* Active indicator */}
          {isActive && (
            <div className="flex-shrink-0">
              <div className="h-2 w-2 rounded-full bg-primary animate-pulse" />
            </div>
          )}
        </header>

        {/* Transcript text */}
        <p className="text-sm leading-relaxed">{segment.text}</p>

        {/* Footer: Confidence */}
        <footer>
          <ConfidenceIndicator
            confidence={segment.confidence}
            size="sm"
            showPercentage={true}
          />
        </footer>
      </article>
    </Card>
  );
}
