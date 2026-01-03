'use client';

import { useEffect, useState } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { DropzoneArea } from '@/components/calls/file-upload/dropzone-area';
import { FilePreview } from '@/components/calls/file-upload/file-preview';
import { MetadataForm } from '@/components/calls/file-upload/metadata-form';
import { UploadProgress } from '@/components/calls/file-upload/upload-progress';
import { UploadStatusBanner } from '@/components/calls/file-upload/upload-status-banner';
import { AudioPlayer } from '@/components/calls/transcription/audio-player';
import { TranscriptionViewer } from '@/components/calls/transcription/transcription-viewer';
import { WebSocketStatus } from '@/components/calls/websocket-status';
import { useCallsPageStore } from '@/lib/stores/calls-page-store';
import { useCallUpload } from '@/lib/hooks/use-call-upload';
import { useCallWebSocket } from '@/lib/hooks/use-call-websocket';
import { useTranscription } from '@/lib/hooks/use-calls';
import { useTranscriptionSync } from '@/lib/hooks/use-transcription-sync';
import { extractAudioDuration } from '@/lib/utils/audio-utils';
import { getAudioUrl } from '@/lib/api/calls';
import { CallChannel } from '@/lib/types/call';
import { ArrowLeft, Upload as UploadIcon } from 'lucide-react';

export default function CallsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const callIdFromUrl = searchParams.get('callId');

  // Store
  const {
    mode,
    upload,
    transcription,
    playback,
    setMode,
    setFile,
    setMetadata,
    setUploadProgress,
    setUploadStatus,
    setUploadError,
    setCallId,
    resetUpload,
    setCurrentSegment,
    setSegments,
    addSegment,
    setCurrentTime,
    setDuration,
  } = useCallsPageStore();

  // Upload hook
  const {
    mutate: uploadFile,
    uploadProgress,
    isUploading,
    isSuccess: uploadSuccess,
    isError: uploadError,
    error: uploadErrorMessage,
    resetUpload: resetUploadMutation,
  } = useCallUpload({
    onSuccess: (data) => {
      setCallId(data.callId);
      setUploadStatus('success');
      // Redirect to transcription view
      router.push(`/dashboard/calls?callId=${data.callId}`);
      setMode('transcription');
    },
    onError: (error) => {
      setUploadStatus('error');
      setUploadError(error.message);
    },
    onProgress: (progress) => {
      setUploadProgress(progress);
    },
  });

  // Transcription hook
  const {
    data: transcriptionData,
    isLoading: transcriptionLoading,
    error: transcriptionError,
  } = useTranscription(upload.callId || callIdFromUrl);

  // WebSocket hook for real-time updates
  const {
    status: wsStatus,
    segments: wsSegments,
    progress: wsProgress,
  } = useCallWebSocket({
    callId: upload.callId || callIdFromUrl,
    autoConnect: mode === 'transcription',
    onSegmentReceived: (segment) => {
      addSegment(segment);
    },
  });

  // Sync audio with transcription
  const { currentSegment } = useTranscriptionSync({
    segments: transcriptionData?.segments || wsSegments || [],
    currentTime: playback.currentTime,
    onSegmentChange: (segmentId) => {
      setCurrentSegment(segmentId);
    },
  });

  // Handle file selection
  const handleFileAccepted = async (file: File) => {
    setFile(file);
    setUploadStatus('idle');
    setUploadError(null);

    // Auto-detect duration
    try {
      const duration = await extractAudioDuration(file);
      setMetadata({
        ...upload.metadata,
        duration,
        autoDetected: {
          ...upload.metadata.autoDetected,
          duration: true,
        },
      });
    } catch (error) {
      console.warn('Could not extract audio duration:', error);
    }
  };

  // Handle file rejected
  const handleFileRejected = (errors: string[]) => {
    setUploadStatus('error');
    setUploadError(errors.join('. '));
  };

  // Handle file removal
  const handleFileRemove = () => {
    setFile(null);
    setUploadStatus('idle');
    setUploadError(null);
    resetUploadMutation();
  };

  // Handle upload
  const handleUpload = () => {
    if (!upload.file) return;

    setUploadStatus('uploading');
    uploadFile({
      file: upload.file,
      metadata: upload.metadata,
    });
  };

  // Handle back to upload
  const handleBackToUpload = () => {
    router.push('/dashboard/calls');
    setMode('upload');
    resetUpload();
  };

  // Handle segment click (seek audio)
  const handleSegmentClick = (segmentId: string, startTime: number) => {
    // This will be handled by the audio player via the sync hook
    setCurrentTime(startTime);
  };

  // Set mode based on URL
  useEffect(() => {
    if (callIdFromUrl) {
      setMode('transcription');
      setCallId(callIdFromUrl);
    } else {
      setMode('upload');
    }
  }, [callIdFromUrl, setMode, setCallId]);

  return (
    <div className="container mx-auto p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Call Recordings</h1>
          <p className="text-muted-foreground mt-1">
            {mode === 'upload'
              ? 'Upload and analyze call recordings'
              : 'View transcription and analysis'}
          </p>
        </div>
        {mode === 'transcription' && (
          <Button variant="outline" onClick={handleBackToUpload}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Upload
          </Button>
        )}
      </div>

      {/* Upload Mode */}
      {mode === 'upload' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Left: File Upload */}
          <div className="space-y-6">
            {!upload.file && upload.status === 'idle' && (
              <DropzoneArea
                onFileAccepted={handleFileAccepted}
                onFileRejected={handleFileRejected}
                disabled={isUploading}
              />
            )}

            {upload.file && (
              <>
                <FilePreview
                  file={upload.file}
                  duration={upload.metadata.duration}
                  onRemove={!isUploading ? handleFileRemove : undefined}
                />

                {isUploading && (
                  <UploadProgress
                    progress={uploadProgress}
                    fileName={upload.file.name}
                    bytesUploaded={(upload.file.size * uploadProgress) / 100}
                    totalBytes={upload.file.size}
                  />
                )}
              </>
            )}

            {upload.status === 'error' && upload.error && (
              <UploadStatusBanner
                type="error"
                message={upload.error}
                onDismiss={() => setUploadError(null)}
                onRetry={upload.file ? handleUpload : undefined}
              />
            )}

            {upload.status === 'success' && (
              <UploadStatusBanner
                type="success"
                message="Upload successful! Redirecting to transcription..."
                autoHideDuration={3000}
              />
            )}
          </div>

          {/* Right: Metadata Form */}
          {upload.file && (
            <div className="space-y-6">
              <MetadataForm
                metadata={upload.metadata}
                onChange={setMetadata}
              />

              <Button
                className="w-full"
                size="lg"
                onClick={handleUpload}
                disabled={isUploading || !upload.file}
              >
                <UploadIcon className="mr-2 h-5 w-5" />
                {isUploading ? 'Uploading...' : 'Upload Call'}
              </Button>
            </div>
          )}
        </div>
      )}

      {/* Transcription Mode */}
      {mode === 'transcription' && (upload.callId || callIdFromUrl) && (
        <div className="grid grid-cols-1 xl:grid-cols-5 gap-6">
          {/* Left: Audio Player (spans 2 columns on xl) */}
          <div className="xl:col-span-2 space-y-6">
            <AudioPlayer
              src={getAudioUrl(upload.callId || callIdFromUrl || '')}
              onTimeUpdate={(time) => setCurrentTime(time)}
            />

            {wsProgress !== undefined && wsProgress < 100 && (
              <Card className="p-4">
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="font-medium">Transcription Progress</span>
                    <span className="text-muted-foreground">{wsProgress}%</span>
                  </div>
                  <div className="h-2 bg-muted rounded-full overflow-hidden">
                    <div
                      className="h-full bg-primary transition-all"
                      style={{ width: `${wsProgress}%` }}
                    />
                  </div>
                </div>
              </Card>
            )}
          </div>

          {/* Right: Transcription Viewer (spans 3 columns on xl) */}
          <div className="xl:col-span-3">
            <TranscriptionViewer
              transcription={transcriptionData || null}
              currentSegmentId={transcription.currentSegmentId}
              onSegmentClick={handleSegmentClick}
              isLoading={transcriptionLoading}
              error={transcriptionError?.message}
            />
          </div>
        </div>
      )}

      {/* WebSocket Status Indicator */}
      {mode === 'transcription' && <WebSocketStatus status={wsStatus} />}
    </div>
  );
}
