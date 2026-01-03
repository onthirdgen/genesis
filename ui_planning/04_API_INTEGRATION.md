# API Integration Strategy - Call Auditing Platform UI

**Status**: Planning Phase
**Last Updated**: 2025-12-31
**Backend**: Spring Cloud Gateway (Port 8080)

---

## Overview

This document outlines how the Next.js frontend integrates with the backend microservices through the Spring Cloud API Gateway.

**Backend Architecture**:
- **API Gateway**: localhost:8080 (routes to all services)
- **Call Ingestion**: localhost:8081
- **Transcription**: localhost:8082
- **Sentiment**: localhost:8083
- **VoC**: localhost:8084
- **Audit**: localhost:8085
- **Analytics**: localhost:8086
- **Notification**: localhost:8087
- **Monitor**: localhost:8088

---

## 1. Axios Client Configuration

### Base Client Setup

```typescript
// lib/api/client.ts
import axios, { AxiosInstance, AxiosError } from 'axios';
import { useAuthStore } from '@/lib/stores/authStore';
import { useNotificationStore } from '@/lib/stores/notificationStore';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request Interceptor: Add auth token
apiClient.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().token;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: Handle errors globally
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const { response } = error;

    if (response?.status === 401) {
      // Unauthorized: clear auth and redirect to login
      useAuthStore.getState().logout();
      window.location.href = '/login';
    } else if (response?.status === 403) {
      // Forbidden
      useNotificationStore.getState().addNotification({
        type: 'error',
        title: 'Access Denied',
        message: 'You do not have permission to access this resource.',
      });
    } else if (response?.status === 500) {
      // Server error
      useNotificationStore.getState().addNotification({
        type: 'error',
        title: 'Server Error',
        message: 'An unexpected error occurred. Please try again later.',
      });
    } else if (error.code === 'ECONNABORTED') {
      // Timeout
      useNotificationStore.getState().addNotification({
        type: 'error',
        title: 'Request Timeout',
        message: 'The request took too long. Please try again.',
      });
    }

    return Promise.reject(error);
  }
);
```

### Multipart Form Data Client (for file uploads)

```typescript
// lib/api/upload-client.ts
import axios from 'axios';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export const uploadClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 300000, // 5 minutes for large files
  headers: {
    'Content-Type': 'multipart/form-data',
  },
});

// Same interceptors as apiClient
uploadClient.interceptors.request.use(/* ... */);
uploadClient.interceptors.response.use(/* ... */);
```

---

## 2. API Function Structure

### Call Ingestion API

```typescript
// lib/api/calls.ts
import { apiClient, uploadClient } from './client';
import { Call, CallStatus, UploadFormData } from '@/types/api';

export const callsApi = {
  /**
   * Fetch list of calls with optional filters
   */
  getCallsList: async (params?: {
    page?: number;
    limit?: number;
    status?: CallStatus;
    agentId?: string;
    callerId?: string;
    startDate?: string;
    endDate?: string;
  }): Promise<{ calls: Call[]; total: number }> => {
    const { data } = await apiClient.get('/api/calls', { params });
    return data;
  },

  /**
   * Get call details by ID
   */
  getCallDetails: async (callId: string): Promise<Call> => {
    const { data } = await apiClient.get(`/api/calls/${callId}`);
    return data;
  },

  /**
   * Get call processing status
   */
  getCallStatus: async (callId: string): Promise<{ status: CallStatus }> => {
    const { data } = await apiClient.get(`/api/calls/${callId}/status`);
    return data;
  },

  /**
   * Upload audio file
   */
  uploadAudioFile: async (
    formData: UploadFormData,
    onProgress?: (progress: number) => void
  ): Promise<{ callId: string; message: string }> => {
    const form = new FormData();
    form.append('file', formData.file);
    form.append('callerId', formData.callerId);
    form.append('agentId', formData.agentId);
    form.append('channel', formData.channel);

    const { data } = await uploadClient.post('/api/calls/upload', form, {
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total
          );
          onProgress(percentCompleted);
        }
      },
    });

    return data;
  },

  /**
   * Download audio file
   */
  getAudioFileUrl: (callId: string): string => {
    return `${apiClient.defaults.baseURL}/api/calls/${callId}/audio`;
  },

  /**
   * Get transcription for a call
   */
  getTranscription: async (callId: string) => {
    const { data } = await apiClient.get(`/api/calls/${callId}/transcription`);
    return data;
  },
};
```

### Analytics API

```typescript
// lib/api/analytics.ts
import { apiClient } from './client';

export const analyticsApi = {
  /**
   * Get dashboard summary metrics
   */
  getDashboard: async (params?: { dateRange?: string }) => {
    const { data } = await apiClient.get('/api/analytics/dashboard', { params });
    return data;
  },

  /**
   * Get agent performance metrics
   */
  getAgentPerformance: async (agentId: string, params?: { dateRange?: string }) => {
    const { data } = await apiClient.get(
      `/api/analytics/agents/${agentId}/performance`,
      { params }
    );
    return data;
  },

  /**
   * Get compliance summary
   */
  getComplianceSummary: async (params?: { dateRange?: string }) => {
    const { data } = await apiClient.get('/api/analytics/compliance/summary', {
      params,
    });
    return data;
  },

  /**
   * Get customer satisfaction trends
   */
  getCustomerSatisfaction: async (params?: { dateRange?: string; groupBy?: string }) => {
    const { data } = await apiClient.get('/api/analytics/customer-satisfaction', {
      params,
    });
    return data;
  },

  /**
   * Get call volume trends
   */
  getCallVolumeTrends: async (params?: { dateRange?: string; granularity?: string }) => {
    const { data } = await apiClient.get('/api/analytics/call-volume', { params });
    return data;
  },
};
```

### VoC (Voice of Customer) API

```typescript
// lib/api/voc.ts
import { apiClient } from './client';

export const vocApi = {
  /**
   * Get VoC insights for a specific call
   */
  getInsights: async (callId: string) => {
    const { data } = await apiClient.get(`/api/voc/insights/${callId}`);
    return data;
  },

  /**
   * Get trending themes
   */
  getTrendingThemes: async (params?: { dateRange?: string; limit?: number }) => {
    const { data } = await apiClient.get('/api/voc/themes/trending', { params });
    return data;
  },

  /**
   * Get calls related to a theme
   */
  getThemeCalls: async (themeId: string, params?: { limit?: number }) => {
    const { data } = await apiClient.get(`/api/voc/themes/${themeId}/calls`, { params });
    return data;
  },

  /**
   * Get sentiment trends
   */
  getSentimentTrends: async (params?: { dateRange?: string; groupBy?: string }) => {
    const { data } = await apiClient.get('/api/voc/sentiment/trends', { params });
    return data;
  },

  /**
   * Get actionable insights (high-priority items)
   */
  getActionableInsights: async (params?: { priority?: string; limit?: number }) => {
    const { data } = await apiClient.get('/api/voc/insights/actionable', { params });
    return data;
  },

  /**
   * Get customer journey for a specific customer
   */
  getCustomerJourney: async (customerId: string) => {
    const { data } = await apiClient.get(`/api/voc/customer-journey/${customerId}`);
    return data;
  },

  /**
   * Generate VoC report
   */
  generateReport: async (params: { dateRange: string; format?: 'pdf' | 'csv' }) => {
    const { data } = await apiClient.post('/api/voc/reports/generate', params);
    return data;
  },
};
```

### Audit API

```typescript
// lib/api/audit.ts
import { apiClient } from './client';

export const auditApi = {
  /**
   * Get audit results for a call
   */
  getCallAudit: async (callId: string) => {
    const { data } = await apiClient.get(`/api/audit/calls/${callId}`);
    return data;
  },

  /**
   * Get compliance reports
   */
  getReports: async (params?: { dateRange?: string; status?: string }) => {
    const { data } = await apiClient.get('/api/audit/reports', { params });
    return data;
  },

  /**
   * Get compliance violations
   */
  getViolations: async (params?: { severity?: string; limit?: number }) => {
    const { data } = await apiClient.get('/api/audit/violations', { params });
    return data;
  },

  /**
   * Get audit rules (configuration)
   */
  getRules: async () => {
    const { data } = await apiClient.get('/api/audit/rules');
    return data;
  },

  /**
   * Create/update audit rule
   */
  saveRule: async (rule: AuditRule) => {
    const { data } = await apiClient.post('/api/audit/rules', rule);
    return data;
  },
};
```

### Sentiment API

```typescript
// lib/api/sentiment.ts
import { apiClient } from './client';

export const sentimentApi = {
  /**
   * Get sentiment analysis for a call
   */
  getSentiment: async (callId: string) => {
    const { data } = await apiClient.get(`/api/sentiment/${callId}`);
    return data;
  },

  /**
   * Get sentiment timeline (segment-by-segment)
   */
  getSentimentTimeline: async (callId: string) => {
    const { data } = await apiClient.get(`/api/sentiment/${callId}/timeline`);
    return data;
  },
};
```

---

## 3. TypeScript Types for API Responses

### Domain Models

```typescript
// types/models.ts

export type CallStatus =
  | 'uploaded'
  | 'processing'
  | 'transcribing'
  | 'analyzing'
  | 'completed'
  | 'failed';

export type Channel = 'inbound' | 'outbound';

export interface Call {
  callId: string;
  audioFileUrl: string;
  duration: number;
  status: CallStatus;
  uploadedAt: string;
  metadata: {
    callerId: string;
    agentId: string;
    timestamp: string;
    channel: Channel;
  };
}

export interface Transcription {
  callId: string;
  fullText: string;
  segments: TranscriptionSegment[];
  language: string;
  confidence: number;
}

export interface TranscriptionSegment {
  speaker: 'agent' | 'customer';
  startTime: number;
  endTime: number;
  text: string;
}

export interface SentimentAnalysis {
  callId: string;
  overallSentiment: 'positive' | 'neutral' | 'negative';
  sentimentScore: number;
  segments: SentimentSegment[];
  escalationDetected: boolean;
}

export interface SentimentSegment {
  startTime: number;
  endTime: number;
  sentiment: string;
  score: number;
  emotions: string[];
}

export interface VoCInsights {
  callId: string;
  insights: {
    primaryIntent: string;
    topics: string[];
    keywords: string[];
    customerSatisfaction: string;
    actionableItems: ActionableItem[];
    predictedChurnRisk: number;
  };
}

export interface ActionableItem {
  category: string;
  priority: 'low' | 'medium' | 'high';
  description: string;
}

export interface AuditResults {
  callId: string;
  auditResults: {
    overallScore: number;
    complianceStatus: 'passed' | 'failed' | 'needs_review';
    violations: ComplianceViolation[];
    qualityMetrics: {
      scriptAdherence: number;
      customerService: number;
      resolutionEffectiveness: number;
    };
    flagsForReview: boolean;
    reviewReason: string | null;
  };
}

export interface ComplianceViolation {
  ruleId: string;
  ruleName: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
  description: string;
  timestamp: number;
}
```

### API Response Types

```typescript
// types/api.ts

export interface ApiResponse<T> {
  data: T;
  message?: string;
  timestamp: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  limit: number;
  hasMore: boolean;
}

export interface ErrorResponse {
  error: string;
  message: string;
  statusCode: number;
  timestamp: string;
}
```

---

## 4. TanStack Query Hooks

### Call Hooks

```typescript
// lib/hooks/useCalls.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { callsApi } from '@/lib/api/calls';

export function useCallsList(filters?: CallFilters) {
  return useQuery({
    queryKey: ['calls', filters],
    queryFn: () => callsApi.getCallsList(filters),
    staleTime: 1000 * 60 * 2,
  });
}

export function useCallDetails(callId: string) {
  return useQuery({
    queryKey: ['calls', callId],
    queryFn: () => callsApi.getCallDetails(callId),
    enabled: !!callId,
  });
}

export function useCallTranscription(callId: string) {
  return useQuery({
    queryKey: ['transcription', callId],
    queryFn: () => callsApi.getTranscription(callId),
    enabled: !!callId,
  });
}

export function useUploadCall() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ formData, onProgress }: { formData: UploadFormData; onProgress?: (p: number) => void }) =>
      callsApi.uploadAudioFile(formData, onProgress),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calls'] });
    },
  });
}
```

### Analytics Hooks

```typescript
// lib/hooks/useAnalytics.ts
import { useQuery } from '@tanstack/react-query';
import { analyticsApi } from '@/lib/api/analytics';

export function useDashboard(dateRange?: string) {
  return useQuery({
    queryKey: ['analytics', 'dashboard', dateRange],
    queryFn: () => analyticsApi.getDashboard({ dateRange }),
    staleTime: 1000 * 60 * 5,
  });
}

export function useAgentPerformance(agentId: string, dateRange?: string) {
  return useQuery({
    queryKey: ['analytics', 'agents', agentId, dateRange],
    queryFn: () => analyticsApi.getAgentPerformance(agentId, { dateRange }),
    enabled: !!agentId,
  });
}

export function useComplianceSummary(dateRange?: string) {
  return useQuery({
    queryKey: ['analytics', 'compliance', dateRange],
    queryFn: () => analyticsApi.getComplianceSummary({ dateRange }),
  });
}
```

### VoC Hooks

```typescript
// lib/hooks/useVoC.ts
import { useQuery, useMutation } from '@tanstack/react-query';
import { vocApi } from '@/lib/api/voc';

export function useVoCInsights(callId: string) {
  return useQuery({
    queryKey: ['voc', 'insights', callId],
    queryFn: () => vocApi.getInsights(callId),
    enabled: !!callId,
  });
}

export function useTrendingThemes(dateRange?: string) {
  return useQuery({
    queryKey: ['voc', 'themes', 'trending', dateRange],
    queryFn: () => vocApi.getTrendingThemes({ dateRange }),
  });
}

export function useActionableInsights(priority?: string) {
  return useQuery({
    queryKey: ['voc', 'actionable', priority],
    queryFn: () => vocApi.getActionableInsights({ priority }),
  });
}

export function useGenerateVoCReport() {
  return useMutation({
    mutationFn: (params: { dateRange: string; format?: 'pdf' | 'csv' }) =>
      vocApi.generateReport(params),
  });
}
```

---

## 5. Real-Time Updates via SSE

### Next.js API Route for SSE

```typescript
// app/api/events/stream/route.ts
export async function GET(request: Request) {
  const encoder = new TextEncoder();

  const stream = new ReadableStream({
    async start(controller) {
      try {
        // Connect to Monitor Service Kafka consumer
        const monitorUrl = 'http://localhost:8088/api/consume/calls.received/from-beginning?limit=100';
        const response = await fetch(monitorUrl);

        if (!response.ok) {
          throw new Error('Failed to connect to Kafka consumer');
        }

        const reader = response.body?.getReader();
        if (!reader) {
          throw new Error('No readable stream');
        }

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          // Forward Kafka events to client as SSE
          const chunk = new TextDecoder().decode(value);
          const lines = chunk.split('\n').filter(Boolean);

          for (const line of lines) {
            try {
              const event = JSON.parse(line);
              const sseData = `data: ${JSON.stringify(event)}\n\n`;
              controller.enqueue(encoder.encode(sseData));
            } catch (e) {
              console.error('Failed to parse event:', e);
            }
          }
        }
      } catch (error) {
        console.error('SSE error:', error);
        controller.close();
      }
    },
  });

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  });
}
```

### Client-Side SSE Hook

```typescript
// lib/hooks/useRealtime.ts
'use client';

import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNotificationStore } from '@/lib/stores/notificationStore';

export function useRealtimeEvents() {
  const queryClient = useQueryClient();
  const addNotification = useNotificationStore((state) => state.addNotification);

  useEffect(() => {
    const eventSource = new EventSource('/api/events/stream');

    eventSource.onmessage = (event) => {
      try {
        const kafkaEvent = JSON.parse(event.data);
        const { eventType, aggregateId } = kafkaEvent;

        // Invalidate affected queries
        switch (eventType) {
          case 'CallReceived':
            queryClient.invalidateQueries({ queryKey: ['calls'] });
            addNotification({
              type: 'info',
              title: 'New Call Uploaded',
              message: `Call ${aggregateId} is being processed`,
            });
            break;

          case 'CallTranscribed':
            queryClient.invalidateQueries({ queryKey: ['calls', aggregateId] });
            queryClient.invalidateQueries({ queryKey: ['transcription', aggregateId] });
            break;

          case 'SentimentAnalyzed':
            queryClient.invalidateQueries({ queryKey: ['sentiment', aggregateId] });
            break;

          case 'VoCAAnalyzed':
            queryClient.invalidateQueries({ queryKey: ['voc', 'insights', aggregateId] });
            queryClient.invalidateQueries({ queryKey: ['voc', 'themes'] });
            break;

          case 'CallAudited':
            queryClient.invalidateQueries({ queryKey: ['audit', aggregateId] });
            queryClient.invalidateQueries({ queryKey: ['analytics', 'compliance'] });
            break;
        }
      } catch (error) {
        console.error('Failed to process SSE event:', error);
      }
    };

    eventSource.onerror = () => {
      console.error('SSE connection error, will auto-reconnect');
    };

    return () => {
      eventSource.close();
    };
  }, [queryClient, addNotification]);
}
```

---

## 6. Error Handling Patterns

### API Error Handler Utility

```typescript
// lib/utils/error-handler.ts
import { AxiosError } from 'axios';

export function handleApiError(error: unknown): string {
  if (error instanceof AxiosError) {
    if (error.response) {
      return error.response.data?.message || 'An error occurred';
    } else if (error.request) {
      return 'No response from server. Please check your connection.';
    }
  }

  return 'An unexpected error occurred';
}
```

### Usage in Components

```typescript
// components/calls/CallsList.tsx
'use client';

import { useCallsList } from '@/lib/hooks/useCalls';
import { handleApiError } from '@/lib/utils/error-handler';

export function CallsList() {
  const { data, isLoading, error } = useCallsList();

  if (isLoading) return <LoadingSpinner />;
  if (error) {
    const errorMessage = handleApiError(error);
    return <ErrorMessage message={errorMessage} />;
  }

  return <CallTable calls={data.calls} />;
}
```

---

## 7. Environment Variables

```bash
# .env.local (development)
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=your-secret-key-here
```

```bash
# .env.production (production)
NEXT_PUBLIC_API_URL=https://api.yourcompany.com
NEXTAUTH_URL=https://yourcompany.com
NEXTAUTH_SECRET=production-secret-key
```

---

## Summary

This API integration strategy provides:

✅ **Type-Safe API Calls**: Full TypeScript support end-to-end
✅ **Centralized Error Handling**: Global interceptors for common errors
✅ **Real-Time Updates**: SSE integration with automatic cache invalidation
✅ **File Upload Support**: Progress tracking for large audio files
✅ **Automatic Retries**: TanStack Query built-in retry logic
✅ **Optimistic Updates**: Better UX for mutations
✅ **Request Cancellation**: Prevent race conditions

**Next Document**: `05_DESIGN_SYSTEM.md` for UI/UX design patterns and component guidelines
