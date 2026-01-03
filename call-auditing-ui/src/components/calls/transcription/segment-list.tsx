/**
 * Segment List Component
 *
 * Virtualized list of transcription segments for performance
 */

import { useRef, useEffect } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { TranscriptionSegmentComponent } from './transcription-segment';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils/cn';
import type { TranscriptionSegment } from '@/lib/types/transcription';

export interface SegmentListProps {
  segments: TranscriptionSegment[];
  currentSegmentId?: string | null;
  onSegmentClick?: (segment: TranscriptionSegment) => void;
  isLoading?: boolean;
  className?: string;
}

export function SegmentList({
  segments,
  currentSegmentId,
  onSegmentClick,
  isLoading = false,
  className,
}: SegmentListProps) {
  const parentRef = useRef<HTMLDivElement>(null);

  // Virtualizer for performance with large lists
  const virtualizer = useVirtualizer({
    count: segments.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 150, // Estimated segment height
    overscan: 5, // Render 5 items above/below viewport
  });

  // Auto-scroll to active segment
  useEffect(() => {
    if (!currentSegmentId) return;

    const index = segments.findIndex((seg) => seg.id === currentSegmentId);
    if (index !== -1) {
      virtualizer.scrollToIndex(index, {
        align: 'center',
        behavior: 'smooth',
      });
    }
  }, [currentSegmentId, segments, virtualizer]);

  // Loading state
  if (isLoading) {
    return (
      <div className={cn('space-y-4', className)}>
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="space-y-3 p-4 border rounded-lg">
            <div className="flex items-center gap-3">
              <Skeleton shape="circle" className="h-8 w-8" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-3 w-32" />
              </div>
            </div>
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-5/6" />
            <Skeleton className="h-2 w-full" />
          </div>
        ))}
      </div>
    );
  }

  // Empty state
  if (segments.length === 0) {
    return (
      <div className={cn('flex flex-col items-center justify-center py-12', className)}>
        <p className="text-sm text-muted-foreground">
          No transcription segments available
        </p>
      </div>
    );
  }

  return (
    <div
      ref={parentRef}
      className={cn('h-full overflow-auto', className)}
      role="list"
      aria-label="Transcription segments"
    >
      <div
        style={{
          height: `${virtualizer.getTotalSize()}px`,
          width: '100%',
          position: 'relative',
        }}
      >
        {virtualizer.getVirtualItems().map((virtualItem) => {
          const segment = segments[virtualItem.index];
          const isActive = segment.id === currentSegmentId;

          return (
            <div
              key={virtualItem.key}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: `${virtualItem.size}px`,
                transform: `translateY(${virtualItem.start}px)`,
              }}
            >
              <div className="pb-4">
                <TranscriptionSegmentComponent
                  segment={segment}
                  isActive={isActive}
                  onClick={onSegmentClick}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
