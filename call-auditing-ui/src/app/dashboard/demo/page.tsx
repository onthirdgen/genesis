'use client';

import { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { WebSocketStatus } from '@/components/calls/websocket-status';
import { ConnectionStatus } from '@/lib/types/websocket';
import { formatTime } from '@/lib/utils/format-time';
import { formatBytes } from '@/lib/utils/format-bytes';
import { getAudioFormat } from '@/lib/utils/audio-utils';
import { validateAudioFile } from '@/lib/utils/validation';
import { Input } from '@/components/ui/input';

export default function DemoPage() {
  const [uploadProgress, setUploadProgress] = useState(0);
  const [wsStatus, setWsStatus] = useState<ConnectionStatus>(ConnectionStatus.DISCONNECTED);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [validationResult, setValidationResult] = useState<string>('');

  // Simulate upload progress
  const simulateUpload = () => {
    setUploadProgress(0);
    const interval = setInterval(() => {
      setUploadProgress((prev) => {
        if (prev >= 100) {
          clearInterval(interval);
          return 100;
        }
        return prev + 10;
      });
    }, 500);
  };

  // Cycle through WebSocket statuses
  const cycleWsStatus = () => {
    const statuses = [
      ConnectionStatus.DISCONNECTED,
      ConnectionStatus.CONNECTING,
      ConnectionStatus.CONNECTED,
      ConnectionStatus.RECONNECTING,
      ConnectionStatus.ERROR,
    ];
    const currentIndex = statuses.indexOf(wsStatus);
    const nextIndex = (currentIndex + 1) % statuses.length;
    setWsStatus(statuses[nextIndex]);
  };

  // Handle file selection
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      const result = validateAudioFile(file);
      const format = getAudioFormat(file);

      if (result.valid) {
        setValidationResult(`✅ Valid ${format} file: ${file.name} (${formatBytes(file.size)})`);
      } else {
        setValidationResult(`❌ Invalid file: ${result.errors.join(', ')}`);
      }
    }
  };

  return (
    <div className="container mx-auto p-6 space-y-8">
      <div>
        <h1 className="text-3xl font-bold mb-2">Components Demo</h1>
        <p className="text-muted-foreground">
          Showcasing the Calls Page infrastructure built in Phases 1-3
        </p>
      </div>

      {/* Utility Functions Demo */}
      <Card className="p-6">
        <h2 className="text-xl font-semibold mb-4">Utility Functions</h2>
        <div className="space-y-2 text-sm">
          <p><strong>formatTime(125)</strong> → {formatTime(125)}</p>
          <p><strong>formatTime(3665)</strong> → {formatTime(3665)}</p>
          <p><strong>formatBytes(1024)</strong> → {formatBytes(1024)}</p>
          <p><strong>formatBytes(1048576)</strong> → {formatBytes(1048576)}</p>
          <p><strong>formatBytes(52428800)</strong> → {formatBytes(52428800)}</p>
        </div>
      </Card>

      {/* Progress Component */}
      <Card className="p-6">
        <h2 className="text-xl font-semibold mb-4">Progress Component</h2>
        <div className="space-y-4">
          <div>
            <div className="flex justify-between mb-2 text-sm">
              <span>Upload Progress</span>
              <span className="text-muted-foreground">{uploadProgress}%</span>
            </div>
            <Progress value={uploadProgress} />
          </div>

          <div>
            <div className="flex justify-between mb-2 text-sm">
              <span>Fixed 25%</span>
              <span className="text-muted-foreground">25%</span>
            </div>
            <Progress value={25} />
          </div>

          <div>
            <div className="flex justify-between mb-2 text-sm">
              <span>Fixed 75%</span>
              <span className="text-muted-foreground">75%</span>
            </div>
            <Progress value={75} />
          </div>

          <Button onClick={simulateUpload}>Simulate Upload</Button>
        </div>
      </Card>

      {/* Badge Component */}
      <Card className="p-6">
        <h2 className="text-xl font-semibold mb-4">Badge Component</h2>
        <div className="flex flex-wrap gap-2">
          <Badge variant="default">Default</Badge>
          <Badge variant="secondary">Secondary</Badge>
          <Badge variant="success">Success</Badge>
          <Badge variant="warning">Warning</Badge>
          <Badge variant="destructive">Destructive</Badge>
          <Badge variant="outline">Outline</Badge>
        </div>
        <div className="flex flex-wrap gap-2 mt-4">
          <Badge size="sm">Small</Badge>
          <Badge size="default">Default</Badge>
          <Badge size="lg">Large</Badge>
        </div>
        <div className="flex flex-wrap gap-2 mt-4">
          <Badge variant="success">TRANSCRIBING</Badge>
          <Badge variant="success">COMPLETED</Badge>
          <Badge variant="destructive">FAILED</Badge>
          <Badge variant="secondary">UPLOADED</Badge>
        </div>
      </Card>

      {/* Skeleton Component */}
      <Card className="p-6">
        <h2 className="text-xl font-semibold mb-4">Skeleton Component</h2>
        <div className="space-y-4">
          <div className="space-y-2">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
          </div>
          <div className="flex items-center gap-4">
            <Skeleton shape="circle" className="h-12 w-12" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-2/3" />
            </div>
          </div>
        </div>
      </Card>

      {/* WebSocket Status Indicator */}
      <Card className="p-6">
        <h2 className="text-xl font-semibold mb-4">WebSocket Status Indicator</h2>
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Current status: <strong>{wsStatus}</strong>
          </p>
          <Button onClick={cycleWsStatus}>Cycle Status</Button>
          <p className="text-xs text-muted-foreground">
            The status indicator appears in the bottom-right corner →
          </p>
        </div>
      </Card>

      {/* File Validation Demo */}
      <Card className="p-6">
        <h2 className="text-xl font-semibold mb-4">Audio File Validation</h2>
        <div className="space-y-4">
          <div>
            <label htmlFor="audio-file" className="text-sm font-medium mb-2 block">
              Select Audio File
            </label>
            <Input
              id="audio-file"
              type="file"
              accept="audio/*"
              onChange={handleFileChange}
            />
          </div>

          {validationResult && (
            <div className="p-4 bg-muted rounded-md">
              <p className="text-sm font-mono">{validationResult}</p>
              {selectedFile && (
                <div className="mt-2 text-xs text-muted-foreground space-y-1">
                  <p>Name: {selectedFile.name}</p>
                  <p>Size: {formatBytes(selectedFile.size)}</p>
                  <p>Type: {selectedFile.type || 'unknown'}</p>
                  <p>Format: {getAudioFormat(selectedFile)}</p>
                </div>
              )}
            </div>
          )}

          <div className="text-xs text-muted-foreground">
            <p>Supported formats: WAV, MP3, M4A, FLAC, OGG, WEBM</p>
            <p>Max size: 100 MB</p>
          </div>
        </div>
      </Card>

      {/* Types Demo */}
      <Card className="p-6">
        <h2 className="text-xl font-semibold mb-4">Type Definitions</h2>
        <div className="space-y-2 text-sm font-mono bg-muted p-4 rounded-md">
          <p>✅ Call, CallStatus, CallChannel</p>
          <p>✅ CallMetadata, CallUploadResponse</p>
          <p>✅ Transcription, TranscriptionSegment</p>
          <p>✅ Speaker, SpeakerRole, Word</p>
          <p>✅ WebSocketMessage, WebSocketMessageType</p>
          <p>✅ ConnectionStatus</p>
        </div>
      </Card>

      {/* API & Hooks Demo */}
      <Card className="p-6">
        <h2 className="text-xl font-semibold mb-4">API & Hooks Ready</h2>
        <div className="space-y-2 text-sm font-mono bg-muted p-4 rounded-md">
          <p>✅ uploadCall() - with progress tracking</p>
          <p>✅ getCallStatus() - for polling</p>
          <p>✅ getTranscription() - fetch segments</p>
          <p>✅ updateCallMetadata() - update call info</p>
          <p className="pt-2 border-t border-border mt-2">✅ useCallUpload() - upload hook</p>
          <p>✅ useCallStatus() - status polling hook</p>
          <p>✅ useTranscription() - transcription hook</p>
          <p>✅ useCallWebSocket() - real-time updates</p>
        </div>
      </Card>

      {/* Implementation Status */}
      <Card className="p-6">
        <h2 className="text-xl font-semibold mb-4">Implementation Status</h2>
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Badge variant="success">✓</Badge>
            <span className="text-sm">Phase 1: Foundation (11 tasks)</span>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="success">✓</Badge>
            <span className="text-sm">Phase 2: API Integration (8 tasks)</span>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="success">✓</Badge>
            <span className="text-sm">Phase 3: WebSocket (7 tasks)</span>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="outline">○</Badge>
            <span className="text-sm text-muted-foreground">Phase 4: File Upload UI (19 tasks)</span>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="outline">○</Badge>
            <span className="text-sm text-muted-foreground">Phase 5: Audio Player (9 tasks)</span>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="outline">○</Badge>
            <span className="text-sm text-muted-foreground">Phase 6: Transcription Display (15 tasks)</span>
          </div>

          <div className="pt-4 border-t border-border mt-4">
            <div className="flex justify-between mb-2 text-sm">
              <span className="font-medium">Overall Progress</span>
              <span className="text-muted-foreground">26/121 tasks</span>
            </div>
            <Progress value={21.5} className="h-3" />
            <p className="text-xs text-muted-foreground mt-1">21.5% complete</p>
          </div>
        </div>
      </Card>

      {/* WebSocket Status Component (visible in bottom-right) */}
      <WebSocketStatus status={wsStatus} />
    </div>
  );
}
