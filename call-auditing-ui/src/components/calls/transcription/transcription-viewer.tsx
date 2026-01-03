/**
 * Transcription Viewer Component
 *
 * Main component for displaying transcription with segments
 */

import { SegmentList } from './segment-list';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { AlertCircle } from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import type { Transcription } from '@/lib/types/transcription';

export interface TranscriptionViewerProps {
  transcription: Transcription | null;
  currentSegmentId?: string | null;
  onSegmentClick?: (segmentId: string, startTime: number) => void;
  isLoading?: boolean;
  error?: string | null;
  className?: string;
}

export function TranscriptionViewer({
  transcription,
  currentSegmentId,
  onSegmentClick,
  isLoading = false,
  error = null,
  className,
}: TranscriptionViewerProps) {
  // Error state
  if (error) {
    return (
      <Card className={cn('p-8', className)}>
        <div className="flex flex-col items-center justify-center text-center space-y-4">
          <AlertCircle className="h-12 w-12 text-red-500" />
          <div>
            <h3 className="text-lg font-semibold mb-2">Transcription Failed</h3>
            <p className="text-sm text-muted-foreground">{error}</p>
          </div>
        </div>
      </Card>
    );
  }

  // Loading state
  if (isLoading) {
    return (
      <Card className={cn('p-6', className)}>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">Transcription</h2>
            <Badge variant="secondary">Loading...</Badge>
          </div>
          <SegmentList
            segments={[]}
            isLoading={true}
          />
        </div>
      </Card>
    );
  }

  // No transcription yet
  if (!transcription) {
    return (
      <Card className={cn('p-8', className)}>
        <div className="flex flex-col items-center justify-center text-center space-y-2">
          <p className="text-sm text-muted-foreground">
            Transcription will appear here once processing is complete
          </p>
        </div>
      </Card>
    );
  }

  const handleSegmentClick = (segment: any) => {
    onSegmentClick?.(segment.id, segment.startTime);
  };

  return (
    <Card className={cn('flex flex-col h-full', className)}>
      {/* Header */}
      <div className="p-6 border-b">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold">Transcription</h2>
            <p className="text-sm text-muted-foreground mt-1">
              {transcription.segments.length} segments • {transcription.language}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="secondary">
              {Math.round(transcription.confidence * 100)}% confidence
            </Badge>
          </div>
        </div>
      </div>

      {/* Segments */}
      <div className="flex-1 overflow-hidden p-6">
        <SegmentList
          segments={transcription.segments}
          currentSegmentId={currentSegmentId}
          onSegmentClick={handleSegmentClick}
        />
      </div>
    </Card>
  );
}
