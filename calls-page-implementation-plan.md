# Calls Page Implementation Plan

**Document Version**: 1.0
**Date**: 2026-01-01
**Status**: Planning Phase
**Target Page**: `/dashboard/calls`

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Design System Analysis](#design-system-analysis)
3. [Component Architecture](#component-architecture)
4. [UI/UX Design Specifications](#uiux-design-specifications)
5. [API Integration Strategy](#api-integration-strategy)
6. [WebSocket Implementation](#websocket-implementation)
7. [State Management](#state-management)
8. [File Structure](#file-structure)
9. [Implementation Roadmap](#implementation-roadmap)
10. [Testing Strategy](#testing-strategy)
11. [Accessibility Considerations](#accessibility-considerations)
12. [Performance Optimizations](#performance-optimizations)
13. [Future Enhancements](#future-enhancements)

---

## Executive Summary

### Objective
Redesign the `/dashboard/calls` page to provide a comprehensive audio file upload and transcription viewing interface that integrates seamlessly with the Call Auditing Platform's event-driven microservices architecture.

### Key Features
- Drag-and-drop audio file upload with progress tracking
- Real-time transcription status updates via WebSocket
- Rich transcription display with speaker diarization, timestamps, and confidence scores
- Audio playback synchronized with transcription segments
- Metadata management (auto-detected with manual override capability)
- Responsive design consistent with existing UI patterns


---

## Component Architecture

### Component Specifications

#### 1. UploadedFileCard

**Purpose**: Display selected file with editable metadata before upload

**Layout**:
```
┌─────────────────────────────────────────────────┐
│  [Audio Icon]  meeting-recording.wav            │
│                12.5 MB • 15:30 duration         │
├─────────────────────────────────────────────────┤
│  Metadata (Auto-detected ✓)                     │
│  ┌───────────────────────────────────────────┐  │
│  │ Caller ID:    [555-0123        ] [Edit]   │  │
│  │ Agent ID:     [agent-001       ] [Edit]   │  │
│  │ Customer ID:  [customer-123    ] [Edit]   │  │
│  │ Duration:     [930 seconds     ] [Edit]   │  │
│  │ Channel:      [PHONE      ▼    ] [Edit]   │  │
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  [███████████████░░░░░] 75%                     │
├─────────────────────────────────────────────────┤
│  [Cancel]                      [Upload File]    │
└─────────────────────────────────────────────────┘
```

**Metadata Field Behavior**:
- Display "Auto-detected" badge if value was auto-filled
- Click "Edit" to make field editable (inline editing)
- Show validation errors inline
- Save changes automatically on blur

#### 2. TranscriptionSection

**Purpose**: Display call transcription with audio playback and rich metadata

**Conditional Rendering**:
```typescript
// Only show after:
// 1. Upload successful
// 2. WebSocket confirms transcription started
// 3. At least partial transcription available
```

**Loading States**:
1. **Uploading**: Progress bar + "Uploading audio file..."
2. **Processing**: Spinner + "Processing audio... This may take a few minutes"
3. **Transcribing**: Animated dots + "Generating transcription..."
4. **Partial Ready**: Show available segments + "Transcribing... X% complete"
5. **Complete**: Full transcription with all features enabled

#### 3. AudioPlayer

**Purpose**: Play audio file with synchronized transcription highlighting

**Features**:
- Play/Pause button
- Current time / Total duration display
- Seekable timeline
- Volume control with mute toggle
- Playback speed control (0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x)
- Skip forward/backward 10 seconds
- Keyboard shortcuts (Space: play/pause, ←→: skip, ↑↓: volume)

**Layout**:
```
┌─────────────────────────────────────────────────┐
│  [▶] ━━━━━━━━━━━●━━━━━━━━━━━━━  3:45 / 15:30    │
│       [🔊] ▂▃▅▆█ [1x▼] [⏮ 10s] [10s ⏭]         │
└─────────────────────────────────────────────────┘
```

#### 4. TranscriptionViewer

**Purpose**: Display transcription segments with rich metadata

**Segment Data Structure**:
```typescript
interface TranscriptionSegment {
  id: string;
  startTime: number; // seconds
  endTime: number;
  speaker: {
    id: string;
    label: string; // "Speaker 1", "Agent", "Customer"
    role?: 'agent' | 'customer' | 'unknown';
  };
  text: string;
  confidence: number; // 0-1
  words?: Array<{
    word: string;
    startTime: number;
    endTime: number;
    confidence: number;
  }>;
}
```

**Segment Card Layout**:
```
┌─────────────────────────────────────────────────┐
│ [A] Agent Smith              00:03:45 - 00:03:52│
│     ────────────────────────────────── 94%      │
│     Thank you for calling. How can I help you   │
│     today?                                      │
└─────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────┐
│ [C] Customer                 00:03:52 - 00:04:10│
│     ────────────────────────────────── 87%      │
│     Hi, I'm calling about my recent order. I    │
│     haven't received it yet and it's been over  │
│     two weeks.                                  │
└─────────────────────────────────────────────────┘
```

**Visual Design**:
- Speaker avatar/initial with color coding
  - Agent: Blue (primary-500)
  - Customer: Purple (accent-500)
  - Unknown: Gray (gray-500)
- Timestamp in gray-500, right-aligned
- Confidence bar below speaker (green: >90%, yellow: 70-90%, red: <70%)
- Text in readable font size (text-base)
- Clickable to seek audio
- Highlight active segment with primary border + light background

**Virtualization**:
```typescript
// Use @tanstack/react-virtual for large transcriptions
import { useVirtualizer } from '@tanstack/react-virtual';

// Render only visible segments (performance optimization)
const virtualizer = useVirtualizer({
  count: segments.length,
  getScrollElement: () => parentRef.current,
  estimateSize: () => 120, // estimated segment height
  overscan: 5, // render 5 extra items above/below viewport
});
```

#### 7. WebSocketStatusIndicator

**Purpose**: Show real-time connection status and updates

**States**:
1. **Connecting**: Yellow dot + "Connecting to updates..."
2. **Connected**: Green dot + "Live updates active"
3. **Disconnected**: Red dot + "Disconnected - Retrying..."
4. **Error**: Red dot + error message

**Position**: Fixed to top-right or bottom-right corner, z-index: 50

**Auto-hide**: Fade out after 3 seconds when connected

---

## UI/UX Design Specifications

### Page Layout Structure

```
┌────────────────────────────────────────────────────────┐
│  Header: Call Recordings                               │
│  Upload and manage call recordings for analysis        │
└────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────┐
│  [WebSocket Status: Connected ●]        [← Back]       │
└────────────────────────────────────────────────────────┘

UPLOAD MODE (no file uploaded yet):
┌────────────────────────────────────────────────────────┐
│  📤 Upload Audio File                                  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  [☁ Upload Icon]                                 │  │
│  │  Drag and drop audio file here, or click to      │  │
│  │  browse                                          │  │
│  │                                                  │  │
│  │  Supported: WAV, MP3, M4A, FLAC, OGG             │  │
│  │  Max size: 100MB                                 │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

UPLOAD MODE (file selected, uploading):
┌────────────────────────────────────────────────────────┐
│  📤 Upload Audio File                                  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  [🎵] meeting-recording.wav                      │  │
│  │  12.5 MB • 15:30 duration                        │  │
│  │  ───────────────────────                         │  │
│  │  Metadata (Auto-detected ✓)                      │  │
│  │  • Caller ID: 555-0123        [Edit]             │  │
│  │  • Agent ID: agent-001        [Edit]             │  │
│  │  • Customer ID: customer-123  [Edit]             │  │
│  │  • Duration: 930 seconds      [Edit]             │  │
│  │  • Channel: PHONE             [Edit]             │  │
│  │  ───────────────────────                         │  │
│  │  [████████████████░░░░] 75%                      │  │
│  │  Uploading... 9.4 MB of 12.5 MB                  │  │
│  │  ───────────────────────                         │  │
│  │  [Cancel Upload]                  [Upload File]  │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘

TRANSCRIPTION VIEW MODE:
┌────────────────────────────────────────────────────────┐
│  Call #CALL-123456                                     │
│  Uploaded: Jan 1, 2026 • Duration: 15:30 • PHONE       │
│  Status: [✓ Transcribed]                               │
│  [🗑 Delete] [⬇ Download] [🔗 Share]                  │
└────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────┐
│  🎵 Audio Player                                       │
│  [▶] ━━━━━━━━━━━●━━━━━━━━━━━━━  3:45 / 15:30           │
│       [🔊] ▂▃▅▆█ [1x▼] [⏮ 10s] [10s ⏭]                │
└────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────┐
│  📝 Transcription                                      │
│  [🔍 Search] [⬇ Export] [Settings ⚙]                  │
│  ───────────────────────                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │ [A] Agent Smith          00:00:05 - 00:00:12     │  │
│  │     ────────────────────────────── 94%           │  │
│  │     Thank you for calling. How can I help you    │  │
│  │     today?                                       │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ [C] Customer             00:00:12 - 00:00:30     │  │
│  │     ────────────────────────────── 87%           │  │
│  │     Hi, I'm calling about my recent order...     │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ [A] Agent Smith          00:00:30 - 00:00:45     │  │
│  │     ────────────────────────────── 96%  ← ACTIVE │  │
│  │     I understand. Let me pull up your order      │  │
│  │     information right now.                       │  │
│  └──────────────────────────────────────────────────┘  │
│                    [Load More ↓]                       │
└────────────────────────────────────────────────────────┘
```

### Responsive Breakpoints

```typescript
// Mobile First Design
const breakpoints = {
  sm: '640px',   // Mobile landscape
  md: '768px',   // Tablet
  lg: '1024px',  // Desktop
  xl: '1280px',  // Large desktop
};

// Layout Adjustments
- Mobile (<768px):
  * Stack audio player controls vertically
  * Full-width cards
  * Simplified metadata view (hide auto-detect badges)
  * Smaller text sizes

- Tablet (768px-1024px):
  * Two-column metadata layout
  * Compact audio controls

- Desktop (>1024px):
  * Side-by-side audio + metadata
  * Full feature set visible
```

### Color Coding System

```typescript
// Speaker Role Colors
const speakerColors = {
  agent: 'bg-primary-100 text-primary-900 border-primary-500',
  customer: 'bg-accent-100 text-accent-900 border-accent-500',
  unknown: 'bg-gray-100 text-gray-900 border-gray-500',
};

// Confidence Score Colors
const confidenceColors = {
  high: 'bg-success-500',    // >90%
  medium: 'bg-warning-500',  // 70-90%
  low: 'bg-error-500',       // <70%
};

// Status Colors
const statusColors = {
  uploading: 'bg-blue-100 text-blue-800',
  processing: 'bg-yellow-100 text-yellow-800',
  transcribed: 'bg-green-100 text-green-800',
  failed: 'bg-red-100 text-red-800',
};
```

### Animation & Transitions

```css
/* File Upload Animations */
.file-enter {
  animation: slideUp 0.3s ease-out;
}

.upload-progress {
  transition: width 0.3s ease-out;
}

/* Transcription Segment Animations */
.segment-highlight {
  transition: all 0.2s ease;
  background-color: hsl(var(--primary) / 0.1);
  border-left: 4px solid hsl(var(--primary));
}

.segment-enter {
  animation: fadeIn 0.4s ease-in;
}

/* WebSocket Status */
.status-pulse {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
```

### Loading States

```typescript
// Skeleton Loaders for Transcription
<div className="space-y-4">
  {[1, 2, 3].map(i => (
    <div key={i} className="animate-pulse">
      <div className="h-4 bg-gray-200 rounded w-1/4 mb-2" />
      <div className="h-20 bg-gray-100 rounded" />
    </div>
  ))}
</div>

// Upload Progress
<div className="relative">
  <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
    <div
      className="h-full bg-primary-500 transition-all duration-300"
      style={{ width: `${progress}%` }}
    />
  </div>
  <p className="text-sm text-muted-foreground mt-1">
    {progress}% - {bytesUploaded} of {totalBytes}
  </p>
</div>
```

### Error States

```typescript
// Upload Error Banner
<div className="rounded-lg border border-red-200 bg-red-50 p-4">
  <div className="flex">
    <AlertCircle className="h-5 w-5 text-red-400" />
    <div className="ml-3">
      <h3 className="text-sm font-medium text-red-800">
        Upload Failed
      </h3>
      <p className="mt-2 text-sm text-red-700">
        {errorMessage}
      </p>
      <button
        onClick={retry}
        className="mt-3 text-sm font-medium text-red-800 hover:text-red-900"
      >
        Try Again
      </button>
    </div>
  </div>
</div>

// Transcription Error
<div className="text-center py-12">
  <AlertCircle className="h-12 w-12 text-red-400 mx-auto mb-4" />
  <h3 className="text-lg font-semibold">Transcription Failed</h3>
  <p className="text-sm text-muted-foreground mt-2">
    We couldn't process this audio file. Please try uploading again.
  </p>
  <Button onClick={retry} className="mt-4">
    Retry Transcription
  </Button>
</div>
```

---

## API Integration Strategy

### Backend Endpoints

#### 1. Upload Audio File

**Endpoint**: `POST /api/calls/upload`
**Method**: POST
**Content-Type**: multipart/form-data
**Authentication**: Bearer token (JWT)

**Request**:
```typescript
const formData = new FormData();
formData.append('file', audioFile);
formData.append('callerId', metadata.callerId);
formData.append('agentId', metadata.agentId);
formData.append('customerId', metadata.customerId);
formData.append('duration', metadata.duration.toString());
formData.append('channel', metadata.channel);

const response = await uploadClient.post('/api/calls/upload', formData);
```

**Response**:
```json
{
  "callId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "UPLOADED",
  "audioFileUrl": "s3://calls/550e8400-e29b-41d4-a716-446655440000.wav",
  "uploadedAt": "2026-01-01T12:00:00Z",
  "metadata": {
    "callerId": "555-0123",
    "agentId": "agent-001",
    "customerId": "customer-123",
    "duration": 930,
    "channel": "PHONE"
  }
}
```

**Error Responses**:
```json
// 400 Bad Request - Invalid file format
{
  "error": "INVALID_FILE_FORMAT",
  "message": "File format not supported. Accepted: WAV, MP3, M4A, FLAC, OGG"
}

// 413 Payload Too Large
{
  "error": "FILE_TOO_LARGE",
  "message": "File size exceeds maximum limit of 100MB"
}

// 401 Unauthorized
{
  "error": "UNAUTHORIZED",
  "message": "Invalid or missing authentication token"
}

// 500 Internal Server Error
{
  "error": "UPLOAD_FAILED",
  "message": "Failed to upload file to storage"
}
```

#### 2. Get Call Status

**Endpoint**: `GET /api/calls/{callId}/status`
**Method**: GET
**Authentication**: Bearer token

**Response**:
```json
{
  "callId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "TRANSCRIBING",
  "progress": 65,
  "stages": {
    "uploaded": {
      "status": "COMPLETED",
      "completedAt": "2026-01-01T12:00:00Z"
    },
    "transcription": {
      "status": "IN_PROGRESS",
      "progress": 65,
      "startedAt": "2026-01-01T12:00:05Z"
    },
    "sentiment": {
      "status": "PENDING"
    },
    "voc": {
      "status": "PENDING"
    }
  }
}
```

**Status Values**:
- `UPLOADED` - File uploaded, waiting for processing
- `TRANSCRIBING` - Transcription in progress
- `TRANSCRIBED` - Transcription complete
- `ANALYZING` - Sentiment/VoC analysis in progress
- `COMPLETED` - All processing complete
- `FAILED` - Processing failed

#### 3. Get Transcription

**Endpoint**: `GET /api/calls/{callId}/transcription`
**Method**: GET
**Authentication**: Bearer token

**Response**:
```json
{
  "callId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "language": "en-US",
  "confidence": 0.92,
  "duration": 930,
  "segments": [
    {
      "id": "seg-001",
      "startTime": 5.2,
      "endTime": 12.8,
      "speaker": {
        "id": "speaker-1",
        "label": "Agent Smith",
        "role": "agent"
      },
      "text": "Thank you for calling. How can I help you today?",
      "confidence": 0.94,
      "words": [
        {
          "word": "Thank",
          "startTime": 5.2,
          "endTime": 5.5,
          "confidence": 0.98
        },
        {
          "word": "you",
          "startTime": 5.5,
          "endTime": 5.7,
          "confidence": 0.96
        }
        // ... more words
      ]
    },
    {
      "id": "seg-002",
      "startTime": 12.8,
      "endTime": 30.5,
      "speaker": {
        "id": "speaker-2",
        "label": "Customer",
        "role": "customer"
      },
      "text": "Hi, I'm calling about my recent order. I haven't received it yet and it's been over two weeks.",
      "confidence": 0.87,
      "words": [
        // ... word-level details
      ]
    }
    // ... more segments
  ]
}
```

#### 4. Get Audio File

**Endpoint**: `GET /api/calls/{callId}/audio`
**Method**: GET
**Authentication**: Bearer token
**Response Type**: Audio stream (binary)

**Usage**:
```typescript
const response = await fetch(`${API_URL}/api/calls/${callId}/audio`, {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const blob = await response.blob();
const audioUrl = URL.createObjectURL(blob);

// Set as audio source
audioElement.src = audioUrl;

// Clean up when component unmounts
return () => URL.revokeObjectURL(audioUrl);
```

#### 5. Update Call Metadata

**Endpoint**: `PATCH /api/calls/{callId}`
**Method**: PATCH
**Content-Type**: application/json
**Authentication**: Bearer token

**Request**:
```json
{
  "callerId": "555-9999",
  "agentId": "agent-002",
  "customerId": "customer-456",
  "channel": "EMAIL"
}
```

**Response**:
```json
{
  "callId": "550e8400-e29b-41d4-a716-446655440000",
  "updatedAt": "2026-01-01T12:05:00Z",
  "metadata": {
    "callerId": "555-9999",
    "agentId": "agent-002",
    "customerId": "customer-456",
    "duration": 930,
    "channel": "EMAIL"
  }
}
```

### API Client Configuration

```typescript
// src/lib/api/calls.ts

import { uploadClient, apiClient, handleApiError } from './client';

export interface UploadCallParams {
  file: File;
  metadata: {
    callerId: string;
    agentId: string;
    customerId?: string;
    duration?: number;
    channel: 'PHONE' | 'EMAIL' | 'CHAT' | 'INTERNAL';
  };
  onProgress?: (progress: number) => void;
}

export interface UploadCallResponse {
  callId: string;
  status: string;
  audioFileUrl: string;
  uploadedAt: string;
  metadata: {
    callerId: string;
    agentId: string;
    customerId?: string;
    duration?: number;
    channel: string;
  };
}

/**
 * Upload audio file with progress tracking
 */
export async function uploadCall({
  file,
  metadata,
  onProgress,
}: UploadCallParams): Promise<UploadCallResponse> {
  try {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('callerId', metadata.callerId);
    formData.append('agentId', metadata.agentId);
    if (metadata.customerId) {
      formData.append('customerId', metadata.customerId);
    }
    if (metadata.duration) {
      formData.append('duration', metadata.duration.toString());
    }
    formData.append('channel', metadata.channel);

    const response = await uploadClient.post<UploadCallResponse>(
      '/api/calls/upload',
      formData,
      {
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total && onProgress) {
            const percentCompleted = Math.round(
              (progressEvent.loaded * 100) / progressEvent.total
            );
            onProgress(percentCompleted);
          }
        },
      }
    );

    return response.data;
  } catch (error) {
    throw new Error(handleApiError(error));
  }
}

/**
 * Get call status
 */
export async function getCallStatus(callId: string) {
  try {
    const response = await apiClient.get(`/api/calls/${callId}/status`);
    return response.data;
  } catch (error) {
    throw new Error(handleApiError(error));
  }
}

/**
 * Get transcription
 */
export async function getTranscription(callId: string) {
  try {
    const response = await apiClient.get(`/api/calls/${callId}/transcription`);
    return response.data;
  } catch (error) {
    throw new Error(handleApiError(error));
  }
}

/**
 * Update call metadata
 */
export async function updateCallMetadata(
  callId: string,
  metadata: Partial<UploadCallParams['metadata']>
) {
  try {
    const response = await apiClient.patch(`/api/calls/${callId}`, metadata);
    return response.data;
  } catch (error) {
    throw new Error(handleApiError(error));
  }
}
```

### TanStack Query Hooks

```typescript
// src/lib/hooks/use-call-upload.ts

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  uploadCall,
  getCallStatus,
  getTranscription,
  updateCallMetadata,
  type UploadCallParams
} from '@/lib/api/calls';

/**
 * Upload call with progress tracking
 */
export function useCallUpload() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: uploadCall,
    onSuccess: (data) => {
      // Invalidate calls list
      queryClient.invalidateQueries({ queryKey: ['calls'] });
      // Pre-populate cache with upload response
      queryClient.setQueryData(['call', data.callId], data);
    },
  });
}

/**
 * Poll call status
 */
export function useCallStatus(callId: string | null, enabled = true) {
  return useQuery({
    queryKey: ['call', callId, 'status'],
    queryFn: () => getCallStatus(callId!),
    enabled: !!callId && enabled,
    refetchInterval: (data) => {
      // Stop polling if transcription is complete or failed
      if (data?.status === 'COMPLETED' || data?.status === 'FAILED') {
        return false;
      }
      // Poll every 2 seconds while processing
      return 2000;
    },
  });
}

/**
 * Fetch transcription
 */
export function useTranscription(callId: string | null) {
  return useQuery({
    queryKey: ['call', callId, 'transcription'],
    queryFn: () => getTranscription(callId!),
    enabled: !!callId,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Update call metadata
 */
export function useUpdateCallMetadata() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ callId, metadata }: { callId: string; metadata: any }) =>
      updateCallMetadata(callId, metadata),
    onSuccess: (data, { callId }) => {
      // Invalidate related queries
      queryClient.invalidateQueries({ queryKey: ['call', callId] });
    },
  });
}
```

---

## WebSocket Implementation

### WebSocket Connection Setup

**Endpoint**: `ws://localhost:8080/ws/calls`
**Protocol**: WebSocket (native browser API)
**Authentication**: JWT token in connection URL or initial message

### Connection Architecture

```typescript
// src/lib/websocket/call-updates.ts

export class CallUpdatesWebSocket {
  private ws: WebSocket | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000; // Start at 1 second
  private listeners: Map<string, Set<(data: any) => void>> = new Map();
  private callId: string | null = null;

  constructor(private token: string) {}

  /**
   * Connect to WebSocket server
   */
  connect(callId: string): void {
    this.callId = callId;
    const wsUrl = `ws://localhost:8080/ws/calls/${callId}?token=${this.token}`;

    try {
      this.ws = new WebSocket(wsUrl);
      this.setupEventHandlers();
    } catch (error) {
      console.error('WebSocket connection error:', error);
      this.handleReconnect();
    }
  }

  /**
   * Setup WebSocket event handlers
   */
  private setupEventHandlers(): void {
    if (!this.ws) return;

    this.ws.onopen = () => {
      console.log('WebSocket connected');
      this.reconnectAttempts = 0;
      this.reconnectDelay = 1000;
      this.emit('connection', { status: 'connected' });
    };

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        this.handleMessage(data);
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
      }
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      this.emit('error', { error });
    };

    this.ws.onclose = (event) => {
      console.log('WebSocket disconnected:', event.code, event.reason);
      this.emit('connection', { status: 'disconnected' });

      // Attempt reconnect if not intentionally closed
      if (event.code !== 1000) {
        this.handleReconnect();
      }
    };
  }

  /**
   * Handle incoming WebSocket messages
   */
  private handleMessage(data: any): void {
    const { type, payload } = data;

    switch (type) {
      case 'TRANSCRIPTION_STARTED':
        this.emit('transcription:started', payload);
        break;

      case 'TRANSCRIPTION_PROGRESS':
        this.emit('transcription:progress', payload);
        break;

      case 'TRANSCRIPTION_SEGMENT':
        this.emit('transcription:segment', payload);
        break;

      case 'TRANSCRIPTION_COMPLETED':
        this.emit('transcription:completed', payload);
        break;

      case 'TRANSCRIPTION_FAILED':
        this.emit('transcription:failed', payload);
        break;

      case 'STATUS_UPDATE':
        this.emit('status:update', payload);
        break;

      default:
        console.warn('Unknown WebSocket message type:', type);
    }
  }

  /**
   * Handle reconnection with exponential backoff
   */
  private handleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnection attempts reached');
      this.emit('connection', { status: 'failed' });
      return;
    }

    this.reconnectAttempts++;
    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

    setTimeout(() => {
      if (this.callId) {
        this.connect(this.callId);
      }
    }, delay);
  }

  /**
   * Subscribe to WebSocket events
   */
  on(event: string, callback: (data: any) => void): void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)!.add(callback);
  }

  /**
   * Unsubscribe from WebSocket events
   */
  off(event: string, callback: (data: any) => void): void {
    this.listeners.get(event)?.delete(callback);
  }

  /**
   * Emit event to all listeners
   */
  private emit(event: string, data: any): void {
    this.listeners.get(event)?.forEach(callback => callback(data));
  }

  /**
   * Send message to WebSocket server
   */
  send(data: any): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    } else {
      console.warn('WebSocket not connected');
    }
  }

  /**
   * Close WebSocket connection
   */
  disconnect(): void {
    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = null;
    }
  }

  /**
   * Get current connection status
   */
  getStatus(): 'connecting' | 'connected' | 'disconnected' | 'error' {
    if (!this.ws) return 'disconnected';

    switch (this.ws.readyState) {
      case WebSocket.CONNECTING:
        return 'connecting';
      case WebSocket.OPEN:
        return 'connected';
      case WebSocket.CLOSING:
      case WebSocket.CLOSED:
        return 'disconnected';
      default:
        return 'error';
    }
  }
}
```

### React Hook for WebSocket

```typescript
// src/lib/hooks/use-call-websocket.ts

import { useEffect, useRef, useState } from 'react';
import { CallUpdatesWebSocket } from '@/lib/websocket/call-updates';

export interface WebSocketStatus {
  status: 'connecting' | 'connected' | 'disconnected' | 'error';
  message?: string;
}

export function useCallWebSocket(callId: string | null, token: string) {
  const wsRef = useRef<CallUpdatesWebSocket | null>(null);
  const [status, setStatus] = useState<WebSocketStatus>({ status: 'disconnected' });
  const [transcriptionProgress, setTranscriptionProgress] = useState(0);
  const [newSegments, setNewSegments] = useState<any[]>([]);

  useEffect(() => {
    if (!callId || !token) return;

    // Create WebSocket instance
    const ws = new CallUpdatesWebSocket(token);
    wsRef.current = ws;

    // Subscribe to connection events
    ws.on('connection', (data) => {
      setStatus({ status: data.status });
    });

    // Subscribe to transcription events
    ws.on('transcription:started', (data) => {
      setTranscriptionProgress(0);
      setStatus({ status: 'connected', message: 'Transcription started' });
    });

    ws.on('transcription:progress', (data) => {
      setTranscriptionProgress(data.progress);
    });

    ws.on('transcription:segment', (data) => {
      setNewSegments(prev => [...prev, data.segment]);
    });

    ws.on('transcription:completed', (data) => {
      setTranscriptionProgress(100);
      setStatus({ status: 'connected', message: 'Transcription completed' });
    });

    ws.on('transcription:failed', (data) => {
      setStatus({ status: 'error', message: data.error });
    });

    ws.on('error', (data) => {
      setStatus({ status: 'error', message: 'Connection error' });
    });

    // Connect to WebSocket
    ws.connect(callId);

    // Cleanup on unmount
    return () => {
      ws.disconnect();
      wsRef.current = null;
    };
  }, [callId, token]);

  return {
    status,
    transcriptionProgress,
    newSegments,
    send: (data: any) => wsRef.current?.send(data),
  };
}
```

### WebSocket Message Formats

#### 1. Transcription Started
```json
{
  "type": "TRANSCRIPTION_STARTED",
  "payload": {
    "callId": "550e8400-e29b-41d4-a716-446655440000",
    "startedAt": "2026-01-01T12:00:10Z",
    "estimatedDuration": 45
  }
}
```

#### 2. Transcription Progress
```json
{
  "type": "TRANSCRIPTION_PROGRESS",
  "payload": {
    "callId": "550e8400-e29b-41d4-a716-446655440000",
    "progress": 65,
    "processedSeconds": 603,
    "totalSeconds": 930
  }
}
```

#### 3. Transcription Segment (Real-time)
```json
{
  "type": "TRANSCRIPTION_SEGMENT",
  "payload": {
    "callId": "550e8400-e29b-41d4-a716-446655440000",
    "segment": {
      "id": "seg-015",
      "startTime": 125.5,
      "endTime": 142.8,
      "speaker": {
        "id": "speaker-1",
        "label": "Agent Smith",
        "role": "agent"
      },
      "text": "I understand your concern. Let me check that for you right away.",
      "confidence": 0.91
    }
  }
}
```

#### 4. Transcription Completed
```json
{
  "type": "TRANSCRIPTION_COMPLETED",
  "payload": {
    "callId": "550e8400-e29b-41d4-a716-446655440000",
    "completedAt": "2026-01-01T12:01:05Z",
    "totalSegments": 42,
    "averageConfidence": 0.89,
    "duration": 930
  }
}
```

#### 5. Transcription Failed
```json
{
  "type": "TRANSCRIPTION_FAILED",
  "payload": {
    "callId": "550e8400-e29b-41d4-a716-446655440000",
    "error": "AUDIO_FORMAT_UNSUPPORTED",
    "message": "Audio codec not supported for transcription",
    "failedAt": "2026-01-01T12:00:15Z"
  }
}
```

#### 6. Status Update
```json
{
  "type": "STATUS_UPDATE",
  "payload": {
    "callId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "TRANSCRIBING",
    "updatedAt": "2026-01-01T12:00:30Z"
  }
}
```

---

## State Management

### Page-Level State (Zustand)

```typescript
// src/lib/stores/calls-page-store.ts

import { create } from 'zustand';
import { devtools } from 'zustand/middleware';

export interface CallMetadata {
  callerId: string;
  agentId: string;
  customerId?: string;
  duration?: number;
  channel: 'PHONE' | 'EMAIL' | 'CHAT' | 'INTERNAL';
}

export interface UploadState {
  selectedFile: File | null;
  uploadProgress: number;
  uploadStatus: 'idle' | 'uploading' | 'success' | 'error';
  uploadError: string | null;
  metadata: CallMetadata;
  autoDetectedMetadata: Partial<CallMetadata>;
}

export interface TranscriptionState {
  currentCallId: string | null;
  currentSegmentId: string | null; // For audio sync
  isPlaying: boolean;
  playbackTime: number;
  playbackSpeed: 1 | 0.5 | 0.75 | 1.25 | 1.5 | 2;
}

export interface CallsPageState {
  // Upload State
  upload: UploadState;
  setSelectedFile: (file: File | null) => void;
  setUploadProgress: (progress: number) => void;
  setUploadStatus: (status: UploadState['uploadStatus']) => void;
  setUploadError: (error: string | null) => void;
  updateMetadata: (metadata: Partial<CallMetadata>) => void;
  setAutoDetectedMetadata: (metadata: Partial<CallMetadata>) => void;
  resetUpload: () => void;

  // Transcription State
  transcription: TranscriptionState;
  setCurrentCallId: (callId: string | null) => void;
  setCurrentSegment: (segmentId: string | null) => void;
  setIsPlaying: (isPlaying: boolean) => void;
  setPlaybackTime: (time: number) => void;
  setPlaybackSpeed: (speed: TranscriptionState['playbackSpeed']) => void;
  resetTranscription: () => void;

  // Page Mode
  mode: 'upload' | 'transcription';
  setMode: (mode: 'upload' | 'transcription') => void;
}

const initialUploadState: UploadState = {
  selectedFile: null,
  uploadProgress: 0,
  uploadStatus: 'idle',
  uploadError: null,
  metadata: {
    callerId: '',
    agentId: '',
    customerId: '',
    duration: undefined,
    channel: 'PHONE',
  },
  autoDetectedMetadata: {},
};

const initialTranscriptionState: TranscriptionState = {
  currentCallId: null,
  currentSegmentId: null,
  isPlaying: false,
  playbackTime: 0,
  playbackSpeed: 1,
};

export const useCallsPageStore = create<CallsPageState>()(
  devtools(
    (set) => ({
      // Initial State
      upload: initialUploadState,
      transcription: initialTranscriptionState,
      mode: 'upload',

      // Upload Actions
      setSelectedFile: (file) =>
        set((state) => ({
          upload: { ...state.upload, selectedFile: file },
        })),

      setUploadProgress: (progress) =>
        set((state) => ({
          upload: { ...state.upload, uploadProgress: progress },
        })),

      setUploadStatus: (status) =>
        set((state) => ({
          upload: { ...state.upload, uploadStatus: status },
        })),

      setUploadError: (error) =>
        set((state) => ({
          upload: { ...state.upload, uploadError: error },
        })),

      updateMetadata: (metadata) =>
        set((state) => ({
          upload: {
            ...state.upload,
            metadata: { ...state.upload.metadata, ...metadata },
          },
        })),

      setAutoDetectedMetadata: (metadata) =>
        set((state) => ({
          upload: {
            ...state.upload,
            autoDetectedMetadata: metadata,
          },
        })),

      resetUpload: () =>
        set((state) => ({
          upload: initialUploadState,
        })),

      // Transcription Actions
      setCurrentCallId: (callId) =>
        set((state) => ({
          transcription: { ...state.transcription, currentCallId: callId },
        })),

      setCurrentSegment: (segmentId) =>
        set((state) => ({
          transcription: { ...state.transcription, currentSegmentId: segmentId },
        })),

      setIsPlaying: (isPlaying) =>
        set((state) => ({
          transcription: { ...state.transcription, isPlaying },
        })),

      setPlaybackTime: (time) =>
        set((state) => ({
          transcription: { ...state.transcription, playbackTime: time },
        })),

      setPlaybackSpeed: (speed) =>
        set((state) => ({
          transcription: { ...state.transcription, playbackSpeed: speed },
        })),

      resetTranscription: () =>
        set((state) => ({
          transcription: initialTranscriptionState,
        })),

      // Page Mode
      setMode: (mode) => set({ mode }),
    }),
    { name: 'CallsPageStore' }
  )
);
```

### Server State (TanStack Query)

Already covered in API Integration section. Key queries:
- `useCallUpload()` - Upload mutation
- `useCallStatus()` - Poll status with auto-refetch
- `useTranscription()` - Fetch transcription data
- `useUpdateCallMetadata()` - Update metadata mutation

### WebSocket State

Managed by `useCallWebSocket` hook (see WebSocket section)

---

## File Structure

```
call-auditing-ui/
├── src/
│   ├── app/
│   │   └── dashboard/
│   │       └── calls/
│   │           ├── page.tsx                    # Main Calls Page (REDESIGNED)
│   │           └── [callId]/
│   │               └── page.tsx                # Individual Call View (future)
│   │
│   ├── components/
│   │   ├── calls/                              # NEW: Call-specific components
│   │   │   ├── file-upload/
│   │   │   │   ├── dropzone-area.tsx           # Drag-and-drop upload zone
│   │   │   │   ├── file-preview.tsx            # Selected file preview
│   │   │   │   ├── upload-progress.tsx         # Progress bar with cancel
│   │   │   │   ├── metadata-form.tsx           # Editable metadata fields
│   │   │   │   └── upload-status-banner.tsx    # Success/Error messages
│   │   │   │
│   │   │   ├── transcription/
│   │   │   │   ├── audio-player.tsx            # Audio playback controls
│   │   │   │   ├── transcription-viewer.tsx    # Main transcription display
│   │   │   │   ├── transcription-segment.tsx   # Individual segment card
│   │   │   │   ├── segment-list.tsx            # Virtualized segment list
│   │   │   │   ├── speaker-avatar.tsx          # Speaker identification
│   │   │   │   ├── confidence-indicator.tsx    # Confidence score bar
│   │   │   │   └── transcription-actions.tsx   # Search/Export/Edit toolbar
│   │   │   │
│   │   │   ├── websocket-status.tsx            # WebSocket connection indicator
│   │   │   └── call-metadata-display.tsx       # Call info header
│   │   │
│   │   ├── ui/                                 # Existing shadcn components
│   │   │   ├── button.tsx
│   │   │   ├── card.tsx
│   │   │   ├── input.tsx
│   │   │   ├── label.tsx
│   │   │   ├── dialog.tsx
│   │   │   ├── toast.tsx
│   │   │   ├── toaster.tsx
│   │   │   ├── progress.tsx                    # NEW: Progress bar component
│   │   │   ├── badge.tsx                       # NEW: Status badges
│   │   │   └── skeleton.tsx                    # NEW: Loading skeletons
│   │   │
│   │   └── layout/
│   │       ├── header.tsx
│   │       └── sidebar.tsx
│   │
│   ├── lib/
│   │   ├── api/
│   │   │   ├── client.ts                       # Existing API client
│   │   │   ├── query-client.ts                 # Existing query client
│   │   │   └── calls.ts                        # NEW: Call-specific API functions
│   │   │
│   │   ├── hooks/
│   │   │   ├── use-calls.ts                    # Existing (UPDATE)
│   │   │   ├── use-call-upload.ts              # NEW: Upload hooks
│   │   │   ├── use-call-websocket.ts           # NEW: WebSocket hook
│   │   │   ├── use-audio-player.ts             # NEW: Audio player logic
│   │   │   └── use-transcription-sync.ts       # NEW: Audio-transcription sync
│   │   │
│   │   ├── stores/
│   │   │   ├── auth-store.ts                   # Existing
│   │   │   └── calls-page-store.ts             # NEW: Calls page state
│   │   │
│   │   ├── websocket/
│   │   │   ├── call-updates.ts                 # NEW: WebSocket client
│   │   │   └── types.ts                        # NEW: WebSocket message types
│   │   │
│   │   ├── utils/
│   │   │   ├── cn.ts                           # Existing
│   │   │   ├── format-time.ts                  # NEW: Time formatting (mm:ss)
│   │   │   ├── format-bytes.ts                 # NEW: File size formatting
│   │   │   ├── audio-utils.ts                  # NEW: Audio file utilities
│   │   │   └── validation.ts                   # NEW: File validation
│   │   │
│   │   └── types/
│   │       ├── index.ts                        # Existing (UPDATE)
│   │       ├── call.ts                         # NEW: Call-related types
│   │       ├── transcription.ts                # NEW: Transcription types
│   │       └── websocket.ts                    # NEW: WebSocket types
│   │
│   └── styles/
│       └── globals.css                         # Existing (may need updates)
│
├── public/
│   └── audio/
│       └── sample-call.wav                     # Optional: Sample audio for testing
│
└── .private/
    ├── calls-page-implementation-plan.md       # THIS DOCUMENT
    └── calls-page-checklist.md                 # Implementation checklist
```

### New Files to Create

#### Components (13 files)
1. `src/components/calls/file-upload/dropzone-area.tsx`
2. `src/components/calls/file-upload/file-preview.tsx`
3. `src/components/calls/file-upload/upload-progress.tsx`
4. `src/components/calls/file-upload/metadata-form.tsx`
5. `src/components/calls/file-upload/upload-status-banner.tsx`
6. `src/components/calls/transcription/audio-player.tsx`
7. `src/components/calls/transcription/transcription-viewer.tsx`
8. `src/components/calls/transcription/transcription-segment.tsx`
9. `src/components/calls/transcription/segment-list.tsx`
10. `src/components/calls/transcription/speaker-avatar.tsx`
11. `src/components/calls/transcription/confidence-indicator.tsx`
12. `src/components/calls/transcription/transcription-actions.tsx`
13. `src/components/calls/websocket-status.tsx`
14. `src/components/calls/call-metadata-display.tsx`

#### UI Primitives (3 files)
15. `src/components/ui/progress.tsx`
16. `src/components/ui/badge.tsx`
17. `src/components/ui/skeleton.tsx`

#### API & Hooks (5 files)
18. `src/lib/api/calls.ts`
19. `src/lib/hooks/use-call-upload.ts`
20. `src/lib/hooks/use-call-websocket.ts`
21. `src/lib/hooks/use-audio-player.ts`
22. `src/lib/hooks/use-transcription-sync.ts`

#### State & WebSocket (3 files)
23. `src/lib/stores/calls-page-store.ts`
24. `src/lib/websocket/call-updates.ts`
25. `src/lib/websocket/types.ts`

#### Utils & Types (7 files)
26. `src/lib/utils/format-time.ts`
27. `src/lib/utils/format-bytes.ts`
28. `src/lib/utils/audio-utils.ts`
29. `src/lib/utils/validation.ts`
30. `src/lib/types/call.ts`
31. `src/lib/types/transcription.ts`
32. `src/lib/types/websocket.ts`

#### Files to Update (2 files)
33. `src/app/dashboard/calls/page.tsx` - Complete redesign
34. `src/lib/hooks/use-calls.ts` - Add upload functionality

**Total**: 32 new files + 2 updated files = **34 files**

---

## Implementation Roadmap

### Phase 1: Foundation (Days 1-2)

#### 1.1 Type Definitions
- [ ] Create `src/lib/types/call.ts`
- [ ] Create `src/lib/types/transcription.ts`
- [ ] Create `src/lib/types/websocket.ts`
- [ ] Update `src/lib/types/index.ts` to export new types

#### 1.2 Utilities
- [ ] Create `src/lib/utils/format-time.ts` (mm:ss formatting)
- [ ] Create `src/lib/utils/format-bytes.ts` (MB, KB formatting)
- [ ] Create `src/lib/utils/audio-utils.ts` (duration extraction, format validation)
- [ ] Create `src/lib/utils/validation.ts` (file validation functions)

#### 1.3 UI Primitives
- [ ] Create `src/components/ui/progress.tsx` (shadcn-style)
- [ ] Create `src/components/ui/badge.tsx` (shadcn-style)
- [ ] Create `src/components/ui/skeleton.tsx` (shadcn-style)

**Deliverables**: Core utilities and reusable UI components

---

### Phase 2: API Integration (Days 3-4)

#### 2.1 API Client
- [ ] Create `src/lib/api/calls.ts`
  - [ ] `uploadCall()` with progress tracking
  - [ ] `getCallStatus()`
  - [ ] `getTranscription()`
  - [ ] `updateCallMetadata()`
  - [ ] Error handling for all functions

#### 2.2 TanStack Query Hooks
- [ ] Create `src/lib/hooks/use-call-upload.ts`
  - [ ] `useCallUpload()` mutation
  - [ ] Progress callback integration
- [ ] Update `src/lib/hooks/use-calls.ts`
  - [ ] Add `useCallStatus()` with polling
  - [ ] Add `useTranscription()`
  - [ ] Add `useUpdateCallMetadata()`

#### 2.3 Testing
- [ ] Test upload with sample audio file
- [ ] Test API error handling
- [ ] Test progress tracking
- [ ] Verify polling behavior

**Deliverables**: Working API integration with upload and status polling

---

### Phase 3: WebSocket Implementation (Days 5-6)

#### 3.1 WebSocket Client
- [ ] Create `src/lib/websocket/types.ts` (message type definitions)
- [ ] Create `src/lib/websocket/call-updates.ts`
  - [ ] Connection management
  - [ ] Reconnection logic with exponential backoff
  - [ ] Event subscription system
  - [ ] Message parsing and routing

#### 3.2 React Integration
- [ ] Create `src/lib/hooks/use-call-websocket.ts`
  - [ ] Connection lifecycle management
  - [ ] Event listeners
  - [ ] State updates for real-time data

#### 3.3 Status Indicator
- [ ] Create `src/components/calls/websocket-status.tsx`
  - [ ] Connection status display
  - [ ] Auto-hide when connected
  - [ ] Error state handling

#### 3.4 Testing
- [ ] Test WebSocket connection
- [ ] Test reconnection logic
- [ ] Test real-time segment delivery
- [ ] Test error scenarios

**Deliverables**: Working WebSocket with real-time transcription updates

---

### Phase 4: File Upload UI (Days 7-9)

#### 4.1 Dropzone Component
- [ ] Create `src/components/calls/file-upload/dropzone-area.tsx`
  - [ ] Integrate react-dropzone
  - [ ] Visual states (default, active, error)
  - [ ] File validation
  - [ ] Accessibility features

#### 4.2 File Preview & Metadata
- [ ] Create `src/components/calls/file-upload/file-preview.tsx`
  - [ ] File name, size, duration display
  - [ ] Audio metadata extraction
- [ ] Create `src/components/calls/file-upload/metadata-form.tsx`
  - [ ] Editable fields
  - [ ] Auto-detect indicators
  - [ ] Validation

#### 4.3 Upload Progress
- [ ] Create `src/components/calls/file-upload/upload-progress.tsx`
  - [ ] Progress bar
  - [ ] Percentage display
  - [ ] Cancel button
  - [ ] Bytes uploaded/total

#### 4.4 Status Banners
- [ ] Create `src/components/calls/file-upload/upload-status-banner.tsx`
  - [ ] Success message
  - [ ] Error message with retry
  - [ ] Info messages

#### 4.5 State Management
- [ ] Create `src/lib/stores/calls-page-store.ts`
  - [ ] Upload state slice
  - [ ] Metadata management
  - [ ] Progress tracking

#### 4.6 Testing
- [ ] Test drag-and-drop
- [ ] Test file selection via click
- [ ] Test file validation (format, size)
- [ ] Test metadata auto-detection
- [ ] Test upload progress
- [ ] Test cancel upload
- [ ] Test error handling

**Deliverables**: Fully functional file upload interface

---

### Phase 5: Audio Player (Days 10-11)

#### 5.1 Audio Player Component
- [ ] Create `src/lib/hooks/use-audio-player.ts`
  - [ ] Audio element management
  - [ ] Playback controls
  - [ ] Time tracking
  - [ ] Speed control
  - [ ] Keyboard shortcuts
- [ ] Create `src/components/calls/transcription/audio-player.tsx`
  - [ ] Play/pause button
  - [ ] Timeline with seeking
  - [ ] Time display
  - [ ] Volume control
  - [ ] Speed selector
  - [ ] Skip buttons

#### 5.2 Audio Fetching
- [ ] Implement audio file download from API
- [ ] Blob URL creation
- [ ] Memory cleanup on unmount

#### 5.3 Testing
- [ ] Test playback controls
- [ ] Test seeking
- [ ] Test speed control
- [ ] Test keyboard shortcuts
- [ ] Test mobile responsiveness

**Deliverables**: Working audio player with all controls

---

### Phase 6: Transcription Display (Days 12-14)

#### 6.1 Segment Components
- [ ] Create `src/components/calls/transcription/speaker-avatar.tsx`
  - [ ] Role-based coloring
  - [ ] Initial display
- [ ] Create `src/components/calls/transcription/confidence-indicator.tsx`
  - [ ] Confidence bar
  - [ ] Color coding
- [ ] Create `src/components/calls/transcription/transcription-segment.tsx`
  - [ ] Speaker info
  - [ ] Timestamp
  - [ ] Transcript text
  - [ ] Confidence
  - [ ] Click to seek

#### 6.2 Segment List
- [ ] Create `src/components/calls/transcription/segment-list.tsx`
  - [ ] Virtualization with @tanstack/react-virtual
  - [ ] Scroll to active segment
  - [ ] Highlight current segment

#### 6.3 Transcription Viewer
- [ ] Create `src/components/calls/transcription/transcription-viewer.tsx`
  - [ ] Header with call info
  - [ ] Segment list integration
  - [ ] Loading states
  - [ ] Empty state

#### 6.4 Actions Toolbar
- [ ] Create `src/components/calls/transcription/transcription-actions.tsx`
  - [ ] Search functionality (future)
  - [ ] Export button (future)
  - [ ] Settings (future)

#### 6.5 Testing
- [ ] Test segment rendering
- [ ] Test virtualization with large transcripts
- [ ] Test click-to-seek
- [ ] Test loading states

**Deliverables**: Complete transcription display with all features

---

### Phase 7: Audio-Transcription Sync (Days 15-16)

#### 7.1 Synchronization Hook
- [ ] Create `src/lib/hooks/use-transcription-sync.ts`
  - [ ] Find segment by time
  - [ ] Scroll to segment
  - [ ] Highlight active segment
  - [ ] Handle segment click

#### 7.2 Integration
- [ ] Connect audio player time updates to segment highlighting
- [ ] Connect segment clicks to audio seeking
- [ ] Test bi-directional sync

#### 7.3 State Management
- [ ] Update `calls-page-store.ts` with sync state
  - [ ] Current segment ID
  - [ ] Playback time
  - [ ] Playing state

#### 7.4 Testing
- [ ] Test auto-scroll to active segment
- [ ] Test highlighting accuracy
- [ ] Test segment click seeking
- [ ] Test edge cases (segment boundaries)

**Deliverables**: Fully synchronized audio and transcription

---

### Phase 8: Main Page Integration (Days 17-18)

#### 8.1 Page Redesign
- [ ] Update `src/app/dashboard/calls/page.tsx`
  - [ ] Remove placeholder content
  - [ ] Add mode switcher (upload vs transcription)
  - [ ] Integrate FileUploadSection
  - [ ] Integrate TranscriptionSection
  - [ ] Add WebSocket status indicator

#### 8.2 Navigation Flow
- [ ] Implement upload success → transcription view redirect
- [ ] Add back to upload button
- [ ] Handle browser back button

#### 8.3 Metadata Display
- [ ] Create `src/components/calls/call-metadata-display.tsx`
  - [ ] Call ID
  - [ ] Upload date
  - [ ] Duration
  - [ ] Channel
  - [ ] Status badge

#### 8.4 Testing
- [ ] Test full upload flow
- [ ] Test navigation
- [ ] Test page state persistence
- [ ] Test error recovery

**Deliverables**: Fully integrated Calls Page

---

### Phase 9: Polish & Optimization (Days 19-20)

#### 9.1 Responsive Design
- [ ] Test all breakpoints (mobile, tablet, desktop)
- [ ] Adjust layouts for small screens
- [ ] Test touch interactions
- [ ] Test landscape orientation

#### 9.2 Performance
- [ ] Optimize bundle size
- [ ] Lazy load heavy components
- [ ] Optimize re-renders
- [ ] Test with large transcriptions (1000+ segments)

#### 9.3 Accessibility
- [ ] Keyboard navigation audit
- [ ] Screen reader testing
- [ ] Focus management
- [ ] ARIA labels and roles
- [ ] Color contrast verification

#### 9.4 Error Handling
- [ ] Network errors
- [ ] WebSocket disconnections
- [ ] Upload failures
- [ ] Transcription failures
- [ ] Invalid audio formats

#### 9.5 Loading States
- [ ] Skeleton loaders
- [ ] Progress indicators
- [ ] Empty states
- [ ] Optimistic updates

**Deliverables**: Production-ready UI

---

### Phase 10: Testing & Documentation (Days 21-22)

#### 10.1 Component Testing
- [ ] Unit tests for utilities
- [ ] Component tests with Testing Library
- [ ] Mock API responses
- [ ] Mock WebSocket events

#### 10.2 Integration Testing
- [ ] E2E tests with Playwright
- [ ] Upload flow test
- [ ] Transcription view test
- [ ] Error scenario tests

#### 10.3 Documentation
- [ ] Component documentation (JSDoc)
- [ ] API documentation
- [ ] WebSocket protocol documentation
- [ ] User guide (how to use the page)

#### 10.4 Code Review Prep
- [ ] Code cleanup
- [ ] Remove console.logs
- [ ] Add TODO comments for future features
- [ ] Update TypeScript strict mode compliance

**Deliverables**: Tested and documented implementation

---

### Timeline Summary

| Phase | Days | Focus |
|-------|------|-------|
| Phase 1: Foundation | 1-2 | Types, utils, UI primitives |
| Phase 2: API Integration | 3-4 | API client, TanStack Query hooks |
| Phase 3: WebSocket | 5-6 | Real-time updates |
| Phase 4: File Upload UI | 7-9 | Dropzone, metadata, progress |
| Phase 5: Audio Player | 10-11 | Playback controls |
| Phase 6: Transcription Display | 12-14 | Segments, virtualization |
| Phase 7: Audio-Transcription Sync | 15-16 | Synchronization logic |
| Phase 8: Main Page Integration | 17-18 | Full page assembly |
| Phase 9: Polish & Optimization | 19-20 | Responsive, accessible, performant |
| Phase 10: Testing & Docs | 21-22 | Tests, documentation |

**Total Estimated Time**: 22 working days (~4-5 weeks)

---

## Testing Strategy

### Unit Tests

```typescript
// Example: src/lib/utils/__tests__/format-time.test.ts
import { formatTime } from '../format-time';

describe('formatTime', () => {
  it('formats seconds to mm:ss', () => {
    expect(formatTime(65)).toBe('01:05');
    expect(formatTime(3661)).toBe('61:01');
    expect(formatTime(0)).toBe('00:00');
  });

  it('handles edge cases', () => {
    expect(formatTime(-5)).toBe('00:00');
    expect(formatTime(NaN)).toBe('00:00');
  });
});
```

```typescript
// Example: src/lib/utils/__tests__/validation.test.ts
import { validateAudioFile } from '../validation';

describe('validateAudioFile', () => {
  it('accepts valid audio formats', () => {
    const wavFile = new File([''], 'test.wav', { type: 'audio/wav' });
    expect(validateAudioFile(wavFile)).toBe(true);
  });

  it('rejects invalid formats', () => {
    const pdfFile = new File([''], 'test.pdf', { type: 'application/pdf' });
    expect(validateAudioFile(pdfFile)).toBe(false);
  });

  it('rejects files over 100MB', () => {
    const largeFile = new File(['x'.repeat(101 * 1024 * 1024)], 'large.wav', {
      type: 'audio/wav',
    });
    expect(validateAudioFile(largeFile)).toBe(false);
  });
});
```

### Component Tests

```typescript
// Example: src/components/calls/file-upload/__tests__/dropzone-area.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { DropzoneArea } from '../dropzone-area';

describe('DropzoneArea', () => {
  it('renders dropzone with correct text', () => {
    render(<DropzoneArea onFileSelect={jest.fn()} />);
    expect(screen.getByText(/drag and drop/i)).toBeInTheDocument();
  });

  it('calls onFileSelect when file is dropped', async () => {
    const onFileSelect = jest.fn();
    render(<DropzoneArea onFileSelect={onFileSelect} />);

    const file = new File(['audio'], 'test.wav', { type: 'audio/wav' });
    const input = screen.getByLabelText(/upload audio file/i);

    fireEvent.change(input, { target: { files: [file] } });

    expect(onFileSelect).toHaveBeenCalledWith(file);
  });

  it('shows error for invalid file type', () => {
    const onFileSelect = jest.fn();
    render(<DropzoneArea onFileSelect={onFileSelect} />);

    const file = new File(['text'], 'test.txt', { type: 'text/plain' });
    const input = screen.getByLabelText(/upload audio file/i);

    fireEvent.change(input, { target: { files: [file] } });

    expect(screen.getByText(/invalid file format/i)).toBeInTheDocument();
    expect(onFileSelect).not.toHaveBeenCalled();
  });
});
```

### Integration Tests (Playwright)

```typescript
// Example: e2e/calls-upload.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Calls Page - Upload Flow', () => {
  test('uploads audio file and shows transcription', async ({ page }) => {
    await page.goto('/dashboard/calls');

    // Upload file
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles('fixtures/sample-call.wav');

    // Verify file preview
    await expect(page.getByText('sample-call.wav')).toBeVisible();

    // Fill metadata
    await page.fill('input[name="callerId"]', '555-0123');
    await page.fill('input[name="agentId"]', 'agent-001');

    // Click upload
    await page.click('button:has-text("Upload File")');

    // Wait for upload progress
    await expect(page.getByRole('progressbar')).toBeVisible();

    // Wait for redirect to transcription
    await expect(page.getByText(/transcription/i)).toBeVisible({ timeout: 10000 });

    // Verify audio player
    await expect(page.getByRole('button', { name: /play/i })).toBeVisible();

    // Verify segments appear
    await expect(page.getByText(/speaker/i)).toBeVisible();
  });

  test('handles upload error gracefully', async ({ page }) => {
    // Mock API to return error
    await page.route('**/api/calls/upload', (route) =>
      route.fulfill({
        status: 400,
        body: JSON.stringify({ error: 'Invalid file format' }),
      })
    );

    await page.goto('/dashboard/calls');

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles('fixtures/invalid-file.txt');

    await page.click('button:has-text("Upload File")');

    // Verify error message
    await expect(page.getByText(/invalid file format/i)).toBeVisible();

    // Verify retry button
    await expect(page.getByRole('button', { name: /try again/i })).toBeVisible();
  });
});
```

### WebSocket Tests

```typescript
// Example: src/lib/websocket/__tests__/call-updates.test.ts
import { CallUpdatesWebSocket } from '../call-updates';
import WS from 'jest-websocket-mock';

describe('CallUpdatesWebSocket', () => {
  let server: WS;
  let client: CallUpdatesWebSocket;

  beforeEach(() => {
    server = new WS('ws://localhost:8080/ws/calls/test-id');
    client = new CallUpdatesWebSocket('test-token');
  });

  afterEach(() => {
    WS.clean();
  });

  it('connects to WebSocket server', async () => {
    client.connect('test-id');
    await server.connected;
    expect(server).toHaveReceivedMessages([]);
  });

  it('handles transcription segment messages', async () => {
    const onSegment = jest.fn();
    client.on('transcription:segment', onSegment);
    client.connect('test-id');

    await server.connected;

    server.send(
      JSON.stringify({
        type: 'TRANSCRIPTION_SEGMENT',
        payload: {
          segment: {
            id: 'seg-1',
            text: 'Hello world',
          },
        },
      })
    );

    expect(onSegment).toHaveBeenCalledWith(
      expect.objectContaining({
        segment: expect.objectContaining({
          id: 'seg-1',
          text: 'Hello world',
        }),
      })
    );
  });

  it('reconnects on disconnect', async () => {
    jest.useFakeTimers();
    client.connect('test-id');

    await server.connected;
    server.close();

    // Fast-forward reconnect delay
    jest.advanceTimersByTime(2000);

    await server.connected;
    expect(server).toBeDefined();

    jest.useRealTimers();
  });
});
```

---

## Accessibility Considerations

### WCAG 2.1 AA Compliance

#### 1. Keyboard Navigation

**Requirements**:
- All interactive elements must be keyboard accessible
- Logical tab order
- Visible focus indicators
- Escape key to dismiss modals/dialogs

**Implementation**:
```typescript
// Dropzone keyboard support
<div
  role="button"
  tabIndex={0}
  onKeyDown={(e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      openFilePicker();
    }
  }}
  aria-label="Upload audio file"
>
  {/* Dropzone content */}
</div>

// Audio player keyboard shortcuts
useEffect(() => {
  const handleKeyDown = (e: KeyboardEvent) => {
    switch (e.key) {
      case ' ':
        e.preventDefault();
        togglePlay();
        break;
      case 'ArrowLeft':
        skipBackward(10);
        break;
      case 'ArrowRight':
        skipForward(10);
        break;
      case 'ArrowUp':
        increaseVolume();
        break;
      case 'ArrowDown':
        decreaseVolume();
        break;
    }
  };

  document.addEventListener('keydown', handleKeyDown);
  return () => document.removeEventListener('keydown', handleKeyDown);
}, []);
```

#### 2. Screen Reader Support

**ARIA Labels**:
```typescript
// Upload progress
<div
  role="progressbar"
  aria-valuenow={progress}
  aria-valuemin={0}
  aria-valuemax={100}
  aria-label="Upload progress"
>
  <div style={{ width: `${progress}%` }} />
</div>

// Audio player
<button
  aria-label={isPlaying ? 'Pause audio' : 'Play audio'}
  aria-pressed={isPlaying}
>
  {isPlaying ? <Pause /> : <Play />}
</button>

// Transcription segment
<div
  role="article"
  aria-labelledby={`speaker-${segment.id}`}
  aria-describedby={`transcript-${segment.id}`}
>
  <h3 id={`speaker-${segment.id}`}>{segment.speaker.label}</h3>
  <p id={`transcript-${segment.id}`}>{segment.text}</p>
</div>
```

**Live Regions**:
```typescript
// Status updates
<div role="status" aria-live="polite" aria-atomic="true">
  {uploadStatus === 'uploading' && `Uploading: ${progress}%`}
  {uploadStatus === 'success' && 'Upload complete'}
  {uploadStatus === 'error' && `Upload failed: ${error}`}
</div>

// WebSocket status
<div role="status" aria-live="polite">
  {wsStatus === 'connected' && 'Connected to live updates'}
  {wsStatus === 'disconnected' && 'Disconnected from live updates'}
</div>
```

#### 3. Color Contrast

**Minimum Ratios** (WCAG AA):
- Normal text: 4.5:1
- Large text (18pt+): 3:1
- UI components: 3:1

**Verification**:
```typescript
// Use Tailwind's contrast utilities
const confidenceColors = {
  high: 'bg-green-600 text-white',      // Verified 7:1 contrast
  medium: 'bg-yellow-600 text-black',   // Verified 4.8:1 contrast
  low: 'bg-red-600 text-white',         // Verified 6.5:1 contrast
};

// Status badges
const statusBadges = {
  success: 'bg-green-100 text-green-900',  // 7:1
  warning: 'bg-yellow-100 text-yellow-900', // 5:1
  error: 'bg-red-100 text-red-900',         // 6:1
};
```

#### 4. Focus Management

```typescript
// Auto-focus on upload button after file selection
const uploadButtonRef = useRef<HTMLButtonElement>(null);

useEffect(() => {
  if (selectedFile) {
    uploadButtonRef.current?.focus();
  }
}, [selectedFile]);

// Focus on first segment when transcription loads
const firstSegmentRef = useRef<HTMLDivElement>(null);

useEffect(() => {
  if (transcriptionLoaded) {
    firstSegmentRef.current?.focus();
  }
}, [transcriptionLoaded]);

// Trap focus in modal dialogs
import { FocusTrap } from '@radix-ui/react-focus-trap';

<FocusTrap>
  <Dialog>
    {/* Dialog content */}
  </Dialog>
</FocusTrap>
```

#### 5. Touch Targets

**Minimum Size**: 44x44 pixels (WCAG 2.1 Level AAA)

```css
/* Ensure all interactive elements meet touch target size */
.button,
.segment-card,
.audio-control {
  min-width: 44px;
  min-height: 44px;
}

/* Add padding to small icons */
.icon-button {
  padding: 12px; /* Makes 20px icon into 44px touch target */
}
```

#### 6. Error Identification

```typescript
// Form field errors
<div>
  <Label htmlFor="callerId">
    Caller ID <span aria-label="required">*</span>
  </Label>
  <Input
    id="callerId"
    aria-invalid={!!errors.callerId}
    aria-describedby={errors.callerId ? 'callerId-error' : undefined}
  />
  {errors.callerId && (
    <p id="callerId-error" role="alert" className="text-red-600">
      {errors.callerId}
    </p>
  )}
</div>

// Upload errors
{uploadError && (
  <div role="alert" className="border-red-500 bg-red-50">
    <AlertCircle className="text-red-500" aria-hidden="true" />
    <p>{uploadError}</p>
  </div>
)}
```

#### 7. Semantic HTML

```typescript
// Use semantic elements
<main>
  <header>
    <h1>Call Recordings</h1>
    <p>Upload and manage call recordings</p>
  </header>

  <section aria-labelledby="upload-heading">
    <h2 id="upload-heading">Upload Audio File</h2>
    {/* Upload UI */}
  </section>

  <section aria-labelledby="transcription-heading">
    <h2 id="transcription-heading">Transcription</h2>
    {/* Transcription UI */}
  </section>
</main>

// Use native audio controls where appropriate
<audio
  controls
  aria-label="Call recording audio"
  src={audioUrl}
/>
```

---

## Performance Optimizations

### 1. Code Splitting

```typescript
// Lazy load heavy components
import { lazy, Suspense } from 'react';

const TranscriptionViewer = lazy(() =>
  import('@/components/calls/transcription/transcription-viewer')
);

const AudioPlayer = lazy(() =>
  import('@/components/calls/transcription/audio-player')
);

// Use with Suspense
<Suspense fallback={<TranscriptionSkeleton />}>
  <TranscriptionViewer />
</Suspense>
```

### 2. Virtualization

```typescript
// Use @tanstack/react-virtual for large segment lists
import { useVirtualizer } from '@tanstack/react-virtual';

function SegmentList({ segments }: { segments: Segment[] }) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: segments.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 120,
    overscan: 5,
  });

  return (
    <div ref={parentRef} style={{ height: '600px', overflow: 'auto' }}>
      <div
        style={{
          height: `${virtualizer.getTotalSize()}px`,
          position: 'relative',
        }}
      >
        {virtualizer.getVirtualItems().map((item) => (
          <div
            key={item.key}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              height: `${item.size}px`,
              transform: `translateY(${item.start}px)`,
            }}
          >
            <TranscriptionSegment segment={segments[item.index]} />
          </div>
        ))}
      </div>
    </div>
  );
}
```

### 3. Memoization

```typescript
// Memoize expensive calculations
import { useMemo } from 'react';

const currentSegment = useMemo(() => {
  return segments.find(
    (seg) => playbackTime >= seg.startTime && playbackTime <= seg.endTime
  );
}, [segments, playbackTime]);

// Memoize components
import { memo } from 'react';

const TranscriptionSegment = memo(({ segment }: { segment: Segment }) => {
  // Component logic
}, (prevProps, nextProps) => {
  // Custom comparison
  return prevProps.segment.id === nextProps.segment.id &&
         prevProps.isActive === nextProps.isActive;
});
```

### 4. Debouncing

```typescript
// Debounce audio time updates
import { useDebouncedValue } from '@/lib/hooks/use-debounced-value';

function AudioPlayer() {
  const [currentTime, setCurrentTime] = useState(0);
  const debouncedTime = useDebouncedValue(currentTime, 100);

  // Use debouncedTime for segment highlighting
  useEffect(() => {
    highlightSegment(debouncedTime);
  }, [debouncedTime]);

  return (
    <audio
      onTimeUpdate={(e) => setCurrentTime(e.currentTarget.currentTime)}
    />
  );
}
```

### 5. Image/Asset Optimization

```typescript
// Use next/image for optimized images
import Image from 'next/image';

<Image
  src="/icons/audio-file.svg"
  alt="Audio file"
  width={48}
  height={48}
  priority={false}
/>

// Preload critical assets
<link rel="preload" href="/fonts/inter.woff2" as="font" type="font/woff2" crossOrigin="anonymous" />
```

### 6. Bundle Size Analysis

```bash
# Analyze bundle size
npm run build
npx @next/bundle-analyzer

# Ensure key chunks are small
# - Main bundle: <200kb gzipped
# - Calls page chunk: <100kb gzipped
# - Audio player chunk: <50kb gzipped
```

### 7. Service Worker (Future Enhancement)

```typescript
// Cache audio files for offline playback
// Implement in future phase
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js');
}
```

---

## Future Enhancements

### Phase 2 Features (Post-MVP)

#### 1. Advanced Search
- Full-text search across transcriptions
- Filter by speaker, date, sentiment
- Search within specific time ranges
- Highlight search terms in transcription

#### 2. Editing & Annotations
- Edit transcription text (with save/cancel)
- Add notes/comments to segments
- Flag important moments
- Create bookmarks

#### 3. Export Options
- Export as PDF with timestamps
- Export as SRT subtitle file
- Export as Word document
- Export audio with trimmed segments

#### 4. Batch Upload
- Upload multiple files at once
- Bulk metadata assignment
- Progress tracking for multiple files
- Queue management

#### 5. Advanced Audio Features
- Waveform visualization
- Audio trimming/editing
- Noise reduction toggle
- Multiple audio tracks (stereo separation)

#### 6. Collaboration
- Share transcription with team
- Real-time collaborative editing
- Comments and threaded discussions
- Version history

#### 7. AI Enhancements
- Auto-summarization
- Key points extraction
- Action items detection
- Speaker identification improvements

#### 8. Analytics Integration
- Link to sentiment analysis
- Link to compliance checks
- Link to VoC insights
- Dashboard widgets

#### 9. Accessibility Enhancements
- Closed captions overlay on audio
- Adjustable text size
- High contrast mode
- Screen reader optimizations

#### 10. Mobile App
- Native iOS/Android apps
- Offline transcription viewing
- Push notifications for transcription completion
- Mobile-optimized upload

---

## Appendix A: Design Mockups

### Upload Mode - Desktop

```
┌────────────────────────────────────────────────────────────────────────┐
│  HEADER                                                                │
│  Call Recordings                                          [WebSocket ●]│
│  Upload and manage call recordings for analysis                        │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│  📤 Upload Audio File                                                  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                                                                  │  │
│  │                         ☁️                                       │  │
│  │                                                                  │  │
│  │         Drag and drop audio file here, or click to browse        │  │
│  │                                                                  │  │
│  │              Supported: WAV, MP3, M4A, FLAC, OGG                 │  │
│  │                       Max size: 100MB                            │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Upload Mode - File Selected

```
┌────────────────────────────────────────────────────────────────────────┐
│  📤 Upload Audio File                                                  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  🎵  meeting-recording.wav                                       │  │
│  │      12.5 MB • 15:30 duration                                    │  │
│  │                                                                  │  │
│  │  ─────────────────────────────────────────                       │  │
│  │                                                                  │  │
│  │  Metadata (Auto-detected ✓)                                      │  │
│  │                                                                  │  │
│  │  Caller ID        [555-0123                    ] [✏️ Edit]       │  │
│  │  Agent ID         [agent-001                   ] [✏️ Edit]       │  │
│  │  Customer ID      [customer-123                ] [✏️ Edit]       │  │
│  │  Duration         [930 seconds                 ] [✏️ Edit]       │  │
│  │  Channel          [PHONE                    ▼  ]                 │  │
│  │                                                                  │  │
│  │  ─────────────────────────────────────────                       │  │
│  │                                                                  │  │
│  │  [████████████████████████░░░░░] 75%                             │  │
│  │  Uploading... 9.4 MB of 12.5 MB                                  │  │
│  │                                                                  │  │
│  │  [Cancel Upload]                              [Upload File]      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Transcription Mode - Desktop

```
┌────────────────────────────────────────────────────────────────────────┐
│  ← Back to Upload                                     [WebSocket ●]    │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│  Call #CALL-123456                                                     │
│  Uploaded: Jan 1, 2026 • Duration: 15:30 • PHONE                       │
│  Status: [✓ Transcribed]                                               │
│  [🗑 Delete] [⬇ Download] [🔗 Share]                                  │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│  🎵 Audio Player                                                       │
│                                                                        │
│  [▶️]  ━━━━━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━   3:45 / 15:30             │
│                                                                        │
│  [🔊] ▂▃▅▆▇█  [1.0x ▼]  [⏮ 10s]  [10s ⏭]                              │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│  📝 Transcription              [🔍 Search] [⬇ Export] [⚙️ Settings]   │
│  ──────────────────────────────────────────────────────────────────────│
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  [A] Agent Smith                          00:00:05 - 00:00:12    │  │
│  │      ─────────────────────────────────────── 94%                 │  │
│  │      Thank you for calling. How can I help you today?            │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  [C] Customer                             00:00:12 - 00:00:30    │  │
│  │      ─────────────────────────────────────── 87%                 │  │
│  │      Hi, I'm calling about my recent order. I haven't received   │  │
│  │      it yet and it's been over two weeks.                        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  [A] Agent Smith                          00:00:30 - 00:00:45    │  │
│  │  │   ─────────────────────────────────────── 96%  ← ACTIVE       │  │
│  │  │   I understand. Let me pull up your order information right   │  │
│  │  │   now.                                                        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│                         [Load More ↓]                                  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Appendix B: TODO Items for Future Phases

Create a `TODO.md` file in the project root to track future enhancements:

```markdown
# Calls Page - Future Enhancements

## Security & Permissions
- [ ] Implement role-based access control for file uploads
- [ ] Add user permissions for viewing/editing transcriptions
- [ ] Implement file upload validation (virus scanning)
- [ ] Add rate limiting for uploads
- [ ] Implement file encryption at rest

## Features
- [ ] Advanced transcription search (full-text, filters)
- [ ] Edit transcription text inline
- [ ] Export transcription as PDF, SRT, DOCX
- [ ] Batch upload multiple files
- [ ] Waveform visualization
- [ ] Speaker identification improvements
- [ ] Auto-summarization
- [ ] Collaborative editing
- [ ] Comments and annotations
- [ ] Version history

## Performance
- [ ] Implement service worker for offline support
- [ ] Add progressive web app (PWA) support
- [ ] Optimize bundle size (<200kb gzipped)
- [ ] Implement streaming transcription display
- [ ] Add request caching strategies

## UX Improvements
- [ ] Dark mode support
- [ ] Customizable keyboard shortcuts
- [ ] User preferences (playback speed default, etc.)
- [ ] Drag-to-reorder segments
- [ ] Multi-select and bulk actions
- [ ] Undo/redo functionality

## Accessibility
- [ ] WCAG 2.2 AAA compliance
- [ ] Improved screen reader support
- [ ] High contrast mode
- [ ] Adjustable text sizing
- [ ] Keyboard navigation improvements

## Analytics
- [ ] Track upload success/failure rates
- [ ] Monitor WebSocket connection stability
- [ ] Track user engagement metrics
- [ ] Performance monitoring (Core Web Vitals)

## Mobile
- [ ] Native mobile app (iOS/Android)
- [ ] Offline transcription viewing
- [ ] Push notifications
- [ ] Mobile-optimized upload flow
```

---

## Document Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-01 | Jon | Initial comprehensive implementation plan |

---

**END OF DOCUMENT**
