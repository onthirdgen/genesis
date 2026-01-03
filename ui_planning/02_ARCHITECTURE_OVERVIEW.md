# UI Architecture Overview - Call Auditing Platform

**Status**: Planning Phase
**Last Updated**: 2025-12-31
**Framework**: Next.js 15 (App Router) + TypeScript

---

## Table of Contents

1. [High-Level Architecture](#high-level-architecture)
2. [Application Structure](#application-structure)
3. [Routing Strategy](#routing-strategy)
4. [Data Flow](#data-flow)
5. [Real-Time Architecture](#real-time-architecture)
6. [Authentication Flow](#authentication-flow)
7. [Error Handling](#error-handling)
8. [Performance Considerations](#performance-considerations)

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Next.js 15 Frontend                              │
│                        (React Server Components)                         │
└───────────┬─────────────────────────────────────────────────────────────┘
            │
            ├─── Server Components (RSC)
            │    └─── Fetch data at build/request time
            │         └─── Direct API calls (server-side)
            │
            ├─── Client Components
            │    └─── Interactive UI, forms, real-time updates
            │         └─── TanStack Query (client-side data fetching)
            │              └─── Axios → API Gateway (localhost:8080)
            │
            └─── API Routes (Next.js middleware)
                 ├─── /api/auth/* → NextAuth.js
                 ├─── /api/events/stream → SSE for Kafka events
                 └─── /api/proxy/* → Proxy to backend (optional)

                      ↓ HTTP/HTTPS

┌─────────────────────────────────────────────────────────────────────────┐
│                    Spring Cloud API Gateway (Port 8080)                  │
└───────────┬─────────────────────────────────────────────────────────────┘
            │
            ├─→ Call Ingestion Service (8081)
            ├─→ VoC Service (8084)
            ├─→ Audit Service (8085)
            ├─→ Analytics Service (8086)
            └─→ Monitor Service (8088) → Kafka → Real-time events
```

---

## Application Structure

### Next.js 15 App Router Directory Structure

```
call-auditing-ui/
├── public/                          # Static assets
│   ├── favicon.ico
│   ├── images/
│   └── sounds/                      # Notification sounds
│
├── src/
│   ├── app/                         # Next.js 15 App Router
│   │   ├── (auth)/                  # Route group for auth pages
│   │   │   ├── login/
│   │   │   │   └── page.tsx         # /login
│   │   │   └── layout.tsx           # Auth layout (no sidebar)
│   │   │
│   │   ├── (dashboard)/             # Route group for authenticated pages
│   │   │   ├── layout.tsx           # Dashboard layout (sidebar, header)
│   │   │   ├── page.tsx             # /dashboard (home)
│   │   │   │
│   │   │   ├── calls/               # Call management
│   │   │   │   ├── page.tsx         # /calls (call list)
│   │   │   │   ├── upload/
│   │   │   │   │   └── page.tsx     # /calls/upload
│   │   │   │   └── [callId]/
│   │   │   │       ├── page.tsx     # /calls/:callId (details)
│   │   │   │       ├── transcription/
│   │   │   │       │   └── page.tsx # /calls/:callId/transcription
│   │   │   │       ├── sentiment/
│   │   │   │       │   └── page.tsx # /calls/:callId/sentiment
│   │   │   │       └── audit/
│   │   │   │           └── page.tsx # /calls/:callId/audit
│   │   │   │
│   │   │   ├── analytics/           # Analytics dashboard
│   │   │   │   ├── page.tsx         # /analytics
│   │   │   │   ├── agents/
│   │   │   │   │   └── page.tsx     # /analytics/agents
│   │   │   │   ├── compliance/
│   │   │   │   │   └── page.tsx     # /analytics/compliance
│   │   │   │   └── trends/
│   │   │   │       └── page.tsx     # /analytics/trends
│   │   │   │
│   │   │   ├── voc/                 # Voice of Customer
│   │   │   │   ├── page.tsx         # /voc (insights dashboard)
│   │   │   │   ├── themes/
│   │   │   │   │   └── page.tsx     # /voc/themes
│   │   │   │   ├── actionable/
│   │   │   │   │   └── page.tsx     # /voc/actionable (action items)
│   │   │   │   └── sentiment/
│   │   │   │       └── page.tsx     # /voc/sentiment (trends)
│   │   │   │
│   │   │   ├── audit/               # Compliance & Auditing
│   │   │   │   ├── page.tsx         # /audit (overview)
│   │   │   │   ├── violations/
│   │   │   │   │   └── page.tsx     # /audit/violations
│   │   │   │   ├── reports/
│   │   │   │   │   └── page.tsx     # /audit/reports
│   │   │   │   └── rules/
│   │   │   │       └── page.tsx     # /audit/rules (configure)
│   │   │   │
│   │   │   └── settings/            # Application settings
│   │   │       ├── page.tsx         # /settings
│   │   │       ├── profile/
│   │   │       │   └── page.tsx     # /settings/profile
│   │   │       └── notifications/
│   │   │           └── page.tsx     # /settings/notifications
│   │   │
│   │   ├── api/                     # API Routes
│   │   │   ├── auth/
│   │   │   │   └── [...nextauth]/
│   │   │   │       └── route.ts     # NextAuth.js handlers
│   │   │   ├── events/
│   │   │   │   └── stream/
│   │   │   │       └── route.ts     # SSE endpoint for Kafka events
│   │   │   └── upload/
│   │   │       └── route.ts         # Audio file upload proxy (optional)
│   │   │
│   │   ├── layout.tsx               # Root layout
│   │   ├── loading.tsx              # Global loading UI
│   │   ├── error.tsx                # Global error UI
│   │   └── not-found.tsx            # 404 page
│   │
│   ├── components/                  # Reusable components
│   │   ├── ui/                      # Shadcn/ui components (copy-paste)
│   │   │   ├── button.tsx
│   │   │   ├── card.tsx
│   │   │   ├── dialog.tsx
│   │   │   ├── table.tsx
│   │   │   ├── badge.tsx
│   │   │   └── ...
│   │   │
│   │   ├── layout/                  # Layout components
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Header.tsx
│   │   │   ├── Footer.tsx
│   │   │   └── BreadcrumbNav.tsx
│   │   │
│   │   ├── calls/                   # Call-specific components
│   │   │   ├── CallCard.tsx
│   │   │   ├── CallTable.tsx
│   │   │   ├── CallStatusBadge.tsx
│   │   │   ├── AudioPlayer.tsx
│   │   │   ├── TranscriptionViewer.tsx
│   │   │   └── UploadDropzone.tsx
│   │   │
│   │   ├── charts/                  # Chart components
│   │   │   ├── SentimentLineChart.tsx
│   │   │   ├── CallVolumeBarChart.tsx
│   │   │   ├── CompliancePieChart.tsx
│   │   │   └── TrendAreaChart.tsx
│   │   │
│   │   ├── analytics/               # Analytics components
│   │   │   ├── DashboardCard.tsx
│   │   │   ├── MetricCard.tsx
│   │   │   ├── AgentPerformanceTable.tsx
│   │   │   └── ComplianceSummary.tsx
│   │   │
│   │   ├── voc/                     # VoC components
│   │   │   ├── InsightCard.tsx
│   │   │   ├── ThemeCloud.tsx
│   │   │   ├── ActionItemList.tsx
│   │   │   └── SentimentGauge.tsx
│   │   │
│   │   └── common/                  # Common components
│   │       ├── LoadingSpinner.tsx
│   │       ├── ErrorBoundary.tsx
│   │       ├── EmptyState.tsx
│   │       ├── SearchBar.tsx
│   │       ├── FilterPanel.tsx
│   │       ├── DateRangePicker.tsx
│   │       └── NotificationToast.tsx
│   │
│   ├── lib/                         # Utilities and configurations
│   │   ├── api/
│   │   │   ├── client.ts            # Axios client configuration
│   │   │   ├── calls.ts             # Call API functions
│   │   │   ├── analytics.ts         # Analytics API functions
│   │   │   ├── voc.ts               # VoC API functions
│   │   │   ├── audit.ts             # Audit API functions
│   │   │   └── types.ts             # API response types
│   │   │
│   │   ├── hooks/                   # Custom React hooks
│   │   │   ├── useCalls.ts          # TanStack Query hooks for calls
│   │   │   ├── useAnalytics.ts      # Analytics data hooks
│   │   │   ├── useVoC.ts            # VoC data hooks
│   │   │   ├── useRealtime.ts       # SSE real-time updates hook
│   │   │   ├── useAuth.ts           # Auth state hook
│   │   │   └── useDebounce.ts       # Debounce utility hook
│   │   │
│   │   ├── stores/                  # Zustand stores
│   │   │   ├── authStore.ts         # Authentication state
│   │   │   ├── uiStore.ts           # UI state (sidebar, theme)
│   │   │   ├── filterStore.ts       # Global filter state
│   │   │   └── notificationStore.ts # Notification queue
│   │   │
│   │   ├── utils/                   # Utility functions
│   │   │   ├── formatters.ts        # Date, number, duration formatters
│   │   │   ├── validators.ts        # Form validators
│   │   │   ├── constants.ts         # App constants
│   │   │   └── helpers.ts           # Misc helper functions
│   │   │
│   │   └── schemas/                 # Zod validation schemas
│   │       ├── callSchema.ts
│   │       ├── uploadSchema.ts
│   │       └── filterSchema.ts
│   │
│   ├── types/                       # TypeScript type definitions
│   │   ├── api.ts                   # API types
│   │   ├── models.ts                # Domain models
│   │   ├── events.ts                # Kafka event types
│   │   └── next-auth.d.ts           # NextAuth type extensions
│   │
│   └── styles/
│       └── globals.css              # Global styles + Tailwind imports
│
├── tests/                           # Test files
│   ├── unit/
│   │   ├── components/
│   │   └── utils/
│   ├── integration/
│   │   └── api/
│   └── e2e/
│       └── playwright/
│
├── .env.local                       # Local environment variables
├── .env.production                  # Production env vars
├── next.config.ts                   # Next.js configuration
├── tailwind.config.ts               # Tailwind CSS config
├── tsconfig.json                    # TypeScript config
├── package.json                     # Dependencies
└── README.md                        # UI documentation
```

---

## Routing Strategy

### App Router (Next.js 15)

**Route Groups**: Use `(auth)` and `(dashboard)` to organize routes without affecting URLs

**Server vs Client Components**:

| Route | Type | Reason |
|-------|------|--------|
| `/dashboard` | Server Component | Static content, fetch initial data server-side |
| `/calls` (list) | Server Component | SEO-friendly, initial data SSR |
| `/calls/[id]` | Server Component | Dynamic metadata, SSR for details |
| `/calls/upload` | Client Component | File upload, interactive |
| `/analytics` | Client Component | Charts, real-time updates |
| `/voc` | Client Component | Interactive filters, charts |

**Dynamic Routes**:
- `/calls/[callId]` - Call details page
- `/analytics/agents/[agentId]` - Agent performance

**Parallel Routes** (Advanced):
- `/calls/[callId]/@transcription` - Show transcription sidebar
- `/calls/[callId]/@sentiment` - Show sentiment sidebar

**Intercepting Routes** (Modals):
- `/calls/(..)upload` - Open upload modal from any calls page

---

## Data Flow

### 1. Server-Side Data Fetching (Server Components)

```typescript
// app/(dashboard)/calls/page.tsx (Server Component)
import { getCallsList } from '@/lib/api/calls';

export default async function CallsPage() {
  // Fetch data at request time (or build time with static generation)
  const calls = await getCallsList({ limit: 20 });

  return <CallTable calls={calls} />;
}
```

**Benefits**:
- No client-side loading spinner
- SEO-friendly (HTML contains data)
- Reduced client-side JavaScript

---

### 2. Client-Side Data Fetching (Client Components with TanStack Query)

```typescript
// components/analytics/Dashboard.tsx (Client Component)
'use client';

import { useAnalytics } from '@/lib/hooks/useAnalytics';

export function Dashboard() {
  const { data, isLoading, error } = useAnalytics({ dateRange: '30d' });

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage error={error} />;

  return <DashboardCharts data={data} />;
}
```

**When to Use**:
- Interactive pages with filters, search
- Real-time data that updates frequently
- User-specific data (not cacheable)

---

### 3. Mutations (Create/Update/Delete)

```typescript
// hooks/useCalls.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { uploadAudioFile } from '@/lib/api/calls';

export function useUploadCall() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: uploadAudioFile,
    onSuccess: () => {
      // Invalidate call list to refetch
      queryClient.invalidateQueries({ queryKey: ['calls'] });
    },
  });
}

// Usage in component
function UploadForm() {
  const uploadMutation = useUploadCall();

  const handleSubmit = (file) => {
    uploadMutation.mutate(file);
  };
}
```

---

## Real-Time Architecture

### Server-Sent Events (SSE) for Kafka Event Streaming

**Backend**: Monitor service consumes Kafka topics and streams events
**Frontend**: EventSource API listens to SSE endpoint

#### 1. Next.js API Route (SSE Endpoint)

```typescript
// app/api/events/stream/route.ts
export async function GET(request: Request) {
  const encoder = new TextEncoder();

  const stream = new ReadableStream({
    async start(controller) {
      // Connect to Monitor Service Kafka consumer
      const eventSource = new EventSource('http://localhost:8088/api/consume/calls.received/from-beginning');

      eventSource.onmessage = (event) => {
        const data = `data: ${event.data}\n\n`;
        controller.enqueue(encoder.encode(data));
      };

      eventSource.onerror = () => {
        controller.close();
      };
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

#### 2. Custom Hook for Real-Time Updates

```typescript
// lib/hooks/useRealtime.ts
'use client';

import { useEffect, useState } from 'react';

export function useRealtimeEvents() {
  const [events, setEvents] = useState<any[]>([]);

  useEffect(() => {
    const eventSource = new EventSource('/api/events/stream');

    eventSource.onmessage = (event) => {
      const newEvent = JSON.parse(event.data);
      setEvents((prev) => [newEvent, ...prev]);

      // Trigger TanStack Query refetch for affected data
      if (newEvent.eventType === 'CallTranscribed') {
        queryClient.invalidateQueries({ queryKey: ['calls', newEvent.aggregateId] });
      }
    };

    eventSource.onerror = () => {
      console.error('SSE connection lost, retrying...');
    };

    return () => eventSource.close();
  }, []);

  return events;
}
```

#### 3. Usage in Components

```typescript
// components/calls/CallStatusBadge.tsx
'use client';

export function CallStatusBadge({ callId }: { callId: string }) {
  const events = useRealtimeEvents();

  // Filter events for this call
  const callEvents = events.filter(e => e.aggregateId === callId);
  const latestStatus = callEvents[0]?.eventType || 'Pending';

  return <Badge variant={getVariantForStatus(latestStatus)}>{latestStatus}</Badge>;
}
```

---

## Authentication Flow

### NextAuth.js Integration

#### 1. Configuration

```typescript
// app/api/auth/[...nextauth]/route.ts
import NextAuth from 'next-auth';
import CredentialsProvider from 'next-auth/providers/credentials';

const handler = NextAuth({
  providers: [
    CredentialsProvider({
      name: 'Credentials',
      credentials: {
        email: { label: 'Email', type: 'email' },
        password: { label: 'Password', type: 'password' },
      },
      async authorize(credentials) {
        // Call your backend auth endpoint
        const res = await fetch('http://localhost:8080/api/auth/login', {
          method: 'POST',
          body: JSON.stringify(credentials),
          headers: { 'Content-Type': 'application/json' },
        });

        const user = await res.json();

        if (res.ok && user) {
          return user;
        }
        return null;
      },
    }),
  ],
  pages: {
    signIn: '/login',
  },
  callbacks: {
    async jwt({ token, user }) {
      if (user) {
        token.accessToken = user.accessToken;
      }
      return token;
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken;
      return session;
    },
  },
});

export { handler as GET, handler as POST };
```

#### 2. Protected Routes (Middleware)

```typescript
// middleware.ts
import { withAuth } from 'next-auth/middleware';

export default withAuth({
  pages: {
    signIn: '/login',
  },
});

export const config = {
  matcher: ['/dashboard/:path*', '/calls/:path*', '/analytics/:path*'],
};
```

#### 3. Client-Side Auth Check

```typescript
// components/layout/Header.tsx
'use client';

import { useSession, signOut } from 'next-auth/react';

export function Header() {
  const { data: session } = useSession();

  return (
    <header>
      <p>Welcome, {session?.user?.name}</p>
      <button onClick={() => signOut()}>Logout</button>
    </header>
  );
}
```

---

## Error Handling

### 1. Global Error Boundary (Next.js)

```typescript
// app/error.tsx
'use client';

export default function Error({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div>
      <h2>Something went wrong!</h2>
      <button onClick={() => reset()}>Try again</button>
    </div>
  );
}
```

### 2. API Error Handling (Axios Interceptor)

```typescript
// lib/api/client.ts
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Redirect to login
      window.location.href = '/login';
    } else if (error.response?.status === 500) {
      // Show toast notification
      toast.error('Server error. Please try again later.');
    }
    return Promise.reject(error);
  }
);
```

### 3. TanStack Query Error Handling

```typescript
// app/providers.tsx
<QueryClientProvider client={queryClient}>
  <QueryErrorResetBoundary>
    {({ reset }) => (
      <ErrorBoundary onReset={reset} fallbackRender={ErrorFallback}>
        {children}
      </ErrorBoundary>
    )}
  </QueryErrorResetBoundary>
</QueryClientProvider>
```

---

## Performance Considerations

### 1. Code Splitting
- **Route-based**: Automatic with Next.js App Router
- **Component-based**: Use `dynamic()` for heavy components

```typescript
import dynamic from 'next/dynamic';

const RechartsChart = dynamic(() => import('@/components/charts/CallVolumeChart'), {
  ssr: false,
  loading: () => <LoadingSpinner />,
});
```

### 2. Image Optimization

```typescript
import Image from 'next/image';

<Image
  src="/logo.png"
  alt="Logo"
  width={200}
  height={50}
  priority // LCP image
/>
```

### 3. Data Prefetching

```typescript
// Prefetch next page of data
const { data } = useCallsList({ page: 1 });
queryClient.prefetchQuery({
  queryKey: ['calls', { page: 2 }],
  queryFn: () => fetchCalls({ page: 2 })
});
```

### 4. Memoization

```typescript
import { useMemo } from 'react';

const sortedCalls = useMemo(() => {
  return calls.sort((a, b) => b.timestamp - a.timestamp);
}, [calls]);
```

---

## Summary

This architecture provides:

✅ **Scalable Structure**: Clear separation of concerns, modular design
✅ **Type Safety**: End-to-end TypeScript from UI to API
✅ **Real-Time**: SSE for live Kafka event updates
✅ **Performance**: Server components, code splitting, caching
✅ **Developer Experience**: Hot reload, IntelliSense, great debugging tools
✅ **Accessibility**: Radix UI primitives, semantic HTML
✅ **Security**: NextAuth.js, API route protection, CSRF tokens

**Next Document**: `03_COMPONENT_STRUCTURE.md` for detailed component design
