/**
 * Audio Player Component
 *
 * Full-featured audio player with timeline, controls, and keyboard shortcuts
 */

import { Play, Pause, SkipBack, SkipForward, Volume2, VolumeX } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils/cn';
import { formatTime } from '@/lib/utils/format-time';
import { useAudioPlayer } from '@/lib/hooks/use-audio-player';

export interface AudioPlayerProps {
  src: string;
  className?: string;
  onTimeUpdate?: (time: number) => void;
  onPlay?: () => void;
  onPause?: () => void;
}

export function AudioPlayer({
  src,
  className,
  onTimeUpdate,
  onPlay,
  onPause,
}: AudioPlayerProps) {
  const {
    audioRef,
    isPlaying,
    currentTime,
    duration,
    volume,
    playbackRate,
    isLoading,
    error,
    isMuted,
    play,
    pause,
    toggle,
    seek,
    skipBackward,
    skipForward,
    setVolume,
    setPlaybackRate,
    toggleMute,
  } = useAudioPlayer({
    src,
    onTimeUpdate,
    onPlay,
    onPause,
  });

  // Handle timeline click
  const handleTimelineClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const timeline = e.currentTarget;
    const rect = timeline.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const percentage = x / rect.width;
    const newTime = percentage * duration;
    seek(newTime);
  };

  // Handle volume change
  const handleVolumeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newVolume = parseFloat(e.target.value);
    setVolume(newVolume);
  };

  // Handle playback rate change
  const handlePlaybackRateChange = (rate: number) => {
    setPlaybackRate(rate);
  };

  const playbackRates = [0.5, 0.75, 1, 1.25, 1.5, 2];

  return (
    <Card className={cn('p-4', className)}>
      {/* Hidden audio element */}
      <audio ref={audioRef} src={src} preload="metadata" />

      <div className="space-y-4">
        {/* Timeline */}
        <div className="space-y-2">
          <div
            className="relative h-2 bg-muted rounded-full cursor-pointer group"
            onClick={handleTimelineClick}
            role="slider"
            aria-label="Audio timeline"
            aria-valuemin={0}
            aria-valuemax={duration}
            aria-valuenow={currentTime}
            tabIndex={0}
          >
            {/* Progress */}
            <div
              className="absolute h-full bg-primary rounded-full transition-all"
              style={{ width: `${(currentTime / duration) * 100 || 0}%` }}
            />

            {/* Hover indicator */}
            <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity">
              <div className="absolute h-full w-1 bg-primary-foreground rounded-full right-0" />
            </div>
          </div>

          {/* Time display */}
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>{formatTime(currentTime)}</span>
            <span>{formatTime(duration)}</span>
          </div>
        </div>

        {/* Controls */}
        <div className="flex items-center justify-between gap-4">
          {/* Left: Playback controls */}
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => skipBackward(10)}
              disabled={isLoading}
              aria-label="Skip backward 10 seconds"
              title="Skip backward 10s (←)"
            >
              <SkipBack className="h-5 w-5" />
            </Button>

            <Button
              variant="default"
              size="icon"
              onClick={toggle}
              disabled={isLoading || !!error}
              aria-label={isPlaying ? 'Pause' : 'Play'}
              title={isPlaying ? 'Pause (Space)' : 'Play (Space)'}
              className="h-10 w-10"
            >
              {isPlaying ? (
                <Pause className="h-5 w-5" />
              ) : (
                <Play className="h-5 w-5" />
              )}
            </Button>

            <Button
              variant="ghost"
              size="icon"
              onClick={() => skipForward(10)}
              disabled={isLoading}
              aria-label="Skip forward 10 seconds"
              title="Skip forward 10s (→)"
            >
              <SkipForward className="h-5 w-5" />
            </Button>
          </div>

          {/* Center: Playback speed */}
          <div className="flex items-center gap-1">
            {playbackRates.map((rate) => (
              <Button
                key={rate}
                variant={playbackRate === rate ? 'default' : 'ghost'}
                size="sm"
                onClick={() => handlePlaybackRateChange(rate)}
                className="h-7 px-2 text-xs"
              >
                {rate}x
              </Button>
            ))}
          </div>

          {/* Right: Volume control */}
          <div className="flex items-center gap-2 min-w-[120px]">
            <Button
              variant="ghost"
              size="icon"
              onClick={toggleMute}
              aria-label={isMuted ? 'Unmute' : 'Mute'}
              title={isMuted ? 'Unmute (M)' : 'Mute (M)'}
              className="h-8 w-8"
            >
              {isMuted || volume === 0 ? (
                <VolumeX className="h-4 w-4" />
              ) : (
                <Volume2 className="h-4 w-4" />
              )}
            </Button>

            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              value={isMuted ? 0 : volume}
              onChange={handleVolumeChange}
              className="flex-1 h-1 bg-muted rounded-full appearance-none cursor-pointer accent-primary"
              aria-label="Volume"
              title="Volume (↑↓)"
            />

            <span className="text-xs text-muted-foreground w-8 text-right">
              {Math.round((isMuted ? 0 : volume) * 100)}%
            </span>
          </div>
        </div>

        {/* Error state */}
        {error && (
          <div className="text-sm text-red-500 text-center">
            Failed to load audio. Please try again.
          </div>
        )}

        {/* Loading state */}
        {isLoading && (
          <div className="text-sm text-muted-foreground text-center">
            Loading audio...
          </div>
        )}

        {/* Keyboard shortcuts hint */}
        <div className="text-xs text-muted-foreground text-center">
          <kbd className="px-1 py-0.5 bg-muted border border-border rounded">Space</kbd> Play/Pause •{' '}
          <kbd className="px-1 py-0.5 bg-muted border border-border rounded">←→</kbd> Skip •{' '}
          <kbd className="px-1 py-0.5 bg-muted border border-border rounded">↑↓</kbd> Volume •{' '}
          <kbd className="px-1 py-0.5 bg-muted border border-border rounded">M</kbd> Mute
        </div>
      </div>
    </Card>
  );
}
