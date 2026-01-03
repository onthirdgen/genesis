# State Management Strategy - Call Auditing Platform UI

**Status**: Planning Phase
**Last Updated**: 2025-12-31
**Stack**: Zustand + TanStack Query + React Context

---

## State Management Philosophy

We use a **hybrid approach** to state management, using the right tool for the right job:

| State Type | Tool | Why |
|------------|------|-----|
| **Server State** | TanStack Query | Caching, refetching, background sync |
| **Global Client State** | Zustand | Lightweight, performant, simple API |
| **Local Component State** | useState/useReducer | Co-located with component, no overhead |
| **Form State** | React Hook Form | Uncontrolled forms, minimal re-renders |
| **URL State** | Next.js searchParams | Shareable, bookmarkable filters |

**Anti-Pattern**: Don't put server data in Zustand/Redux - it leads to cache invalidation nightmares.

---

## 1. Server State (TanStack Query)

### Purpose
Manage data fetched from backend APIs: calls, analytics, VoC insights, audit results.

### Implementation

#### Query Configuration

```typescript
// lib/api/query-client.ts
import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      gcTime: 1000 * 60 * 10,    // 10 minutes (formerly cacheTime)
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});
```

#### Provider Setup

```typescript
// app/providers.tsx
'use client';

import { QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { queryClient } from '@/lib/api/query-client';

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
```

### Example: Calls List Hook

```typescript
// lib/hooks/useCalls.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchCalls, uploadCall, fetchCallDetails } from '@/lib/api/calls';

export function useCallsList(filters?: CallFilters) {
  return useQuery({
    queryKey: ['calls', filters],
    queryFn: () => fetchCalls(filters),
    staleTime: 1000 * 60 * 2, // 2 minutes
  });
}

export function useCallDetails(callId: string) {
  return useQuery({
    queryKey: ['calls', callId],
    queryFn: () => fetchCallDetails(callId),
    enabled: !!callId,
  });
}

export function useUploadCall() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: uploadCall,
    onMutate: async (newCall) => {
      // Optimistic update
      await queryClient.cancelQueries({ queryKey: ['calls'] });

      const previousCalls = queryClient.getQueryData(['calls']);

      queryClient.setQueryData(['calls'], (old: any) => ({
        ...old,
        calls: [newCall, ...(old?.calls || [])],
      }));

      return { previousCalls };
    },
    onError: (err, newCall, context) => {
      // Rollback on error
      queryClient.setQueryData(['calls'], context?.previousCalls);
    },
    onSettled: () => {
      // Always refetch after mutation
      queryClient.invalidateQueries({ queryKey: ['calls'] });
    },
  });
}
```

### Usage in Components

```typescript
// app/(dashboard)/calls/page.tsx
'use client';

import { useCallsList } from '@/lib/hooks/useCalls';

export default function CallsPage() {
  const { data, isLoading, error, refetch } = useCallsList();

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage error={error} />;

  return <CallTable calls={data.calls} onRefresh={refetch} />;
}
```

---

## 2. Global Client State (Zustand)

### Purpose
Manage global UI state that doesn't come from the server: theme, sidebar state, filters, notifications.

### Store Structure

#### Authentication Store

```typescript
// lib/stores/authStore.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  user: User | null;
  token: string | null;
  setUser: (user: User | null) => void;
  setToken: (token: string | null) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      setUser: (user) => set({ user }),
      setToken: (token) => set({ token }),
      logout: () => set({ user: null, token: null }),
    }),
    {
      name: 'auth-storage',
    }
  )
);
```

#### UI State Store

```typescript
// lib/stores/uiStore.ts
import { create } from 'zustand';

interface UIState {
  sidebarOpen: boolean;
  theme: 'light' | 'dark' | 'system';
  toggleSidebar: () => void;
  setTheme: (theme: 'light' | 'dark' | 'system') => void;
}

export const useUIStore = create<UIState>((set) => ({
  sidebarOpen: true,
  theme: 'system',
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setTheme: (theme) => set({ theme }),
}));
```

#### Filter State Store

```typescript
// lib/stores/filterStore.ts
import { create } from 'zustand';

interface FilterState {
  dateRange: DateRange;
  agentIds: string[];
  sentimentTypes: string[];
  setDateRange: (range: DateRange) => void;
  setAgentIds: (ids: string[]) => void;
  setSentimentTypes: (types: string[]) => void;
  resetFilters: () => void;
}

const defaultFilters = {
  dateRange: { from: subDays(new Date(), 30), to: new Date() },
  agentIds: [],
  sentimentTypes: [],
};

export const useFilterStore = create<FilterState>((set) => ({
  ...defaultFilters,
  setDateRange: (range) => set({ dateRange: range }),
  setAgentIds: (ids) => set({ agentIds: ids }),
  setSentimentTypes: (types) => set({ sentimentTypes: types }),
  resetFilters: () => set(defaultFilters),
}));
```

#### Notification Store

```typescript
// lib/stores/notificationStore.ts
import { create } from 'zustand';

interface Notification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
}

interface NotificationState {
  notifications: Notification[];
  addNotification: (notification: Omit<Notification, 'id'>) => void;
  removeNotification: (id: string) => void;
}

export const useNotificationStore = create<NotificationState>((set) => ({
  notifications: [],
  addNotification: (notification) =>
    set((state) => ({
      notifications: [
        ...state.notifications,
        { ...notification, id: Math.random().toString(36) },
      ],
    })),
  removeNotification: (id) =>
    set((state) => ({
      notifications: state.notifications.filter((n) => n.id !== id),
    })),
}));
```

### Usage in Components

```typescript
// components/layout/Sidebar.tsx
'use client';

import { useUIStore } from '@/lib/stores/uiStore';

export function Sidebar() {
  const { sidebarOpen, toggleSidebar } = useUIStore();

  return (
    <aside className={cn('sidebar', { open: sidebarOpen })}>
      <button onClick={toggleSidebar}>Toggle</button>
      {/* Sidebar content */}
    </aside>
  );
}
```

```typescript
// components/common/NotificationToast.tsx
'use client';

import { useNotificationStore } from '@/lib/stores/notificationStore';

export function NotificationToast() {
  const { notifications, removeNotification } = useNotificationStore();

  return (
    <div className="toast-container">
      {notifications.map((notification) => (
        <Toast
          key={notification.id}
          {...notification}
          onClose={() => removeNotification(notification.id)}
        />
      ))}
    </div>
  );
}
```

---

## 3. URL State (Next.js searchParams)

### Purpose
Store filters, pagination, and search queries in the URL for shareable, bookmarkable state.

### Implementation

```typescript
// app/(dashboard)/calls/page.tsx
'use client';

import { useSearchParams, useRouter } from 'next/navigation';

export default function CallsPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  // Read from URL
  const page = parseInt(searchParams.get('page') || '1');
  const search = searchParams.get('search') || '';
  const status = searchParams.get('status') || 'all';

  // Update URL
  const updateFilters = (key: string, value: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set(key, value);
    router.push(`/calls?${params.toString()}`);
  };

  return (
    <div>
      <SearchBar
        value={search}
        onChange={(value) => updateFilters('search', value)}
      />
      <CallTable
        page={page}
        status={status}
        onPageChange={(p) => updateFilters('page', p.toString())}
      />
    </div>
  );
}
```

### Custom Hook for URL Filters

```typescript
// lib/hooks/useUrlFilters.ts
import { useSearchParams, useRouter } from 'next/navigation';

export function useUrlFilters() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const getFilter = (key: string, defaultValue?: string) => {
    return searchParams.get(key) || defaultValue || '';
  };

  const setFilter = (key: string, value: string) => {
    const params = new URLSearchParams(searchParams.toString());
    if (value) {
      params.set(key, value);
    } else {
      params.delete(key);
    }
    router.push(`?${params.toString()}`);
  };

  const setFilters = (filters: Record<string, string>) => {
    const params = new URLSearchParams(searchParams.toString());
    Object.entries(filters).forEach(([key, value]) => {
      if (value) {
        params.set(key, value);
      } else {
        params.delete(key);
      }
    });
    router.push(`?${params.toString()}`);
  };

  return { getFilter, setFilter, setFilters };
}
```

---

## 4. Form State (React Hook Form)

### Purpose
Manage complex form state with validation, without causing unnecessary re-renders.

### Implementation

```typescript
// components/calls/UploadForm.tsx
'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { uploadSchema } from '@/lib/schemas/uploadSchema';

export function UploadForm() {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(uploadSchema),
  });

  const onSubmit = async (data) => {
    await uploadCall(data);
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <input {...register('file')} type="file" />
      {errors.file && <span>{errors.file.message}</span>}

      <input {...register('callerId')} placeholder="Caller ID" />
      {errors.callerId && <span>{errors.callerId.message}</span>}

      <button type="submit" disabled={isSubmitting}>
        Upload
      </button>
    </form>
  );
}
```

### Zod Schema

```typescript
// lib/schemas/uploadSchema.ts
import { z } from 'zod';

export const uploadSchema = z.object({
  file: z
    .instanceof(File)
    .refine((file) => file.size <= 100 * 1024 * 1024, 'Max file size is 100MB')
    .refine(
      (file) => ['audio/wav', 'audio/mpeg', 'audio/mp4'].includes(file.type),
      'Only .wav, .mp3, and .m4a files are allowed'
    ),
  callerId: z.string().min(1, 'Caller ID is required'),
  agentId: z.string().min(1, 'Agent ID is required'),
  channel: z.enum(['inbound', 'outbound']),
});

export type UploadFormData = z.infer<typeof uploadSchema>;
```

---

## 5. Real-Time State Updates

### Integration with TanStack Query

When SSE events arrive, invalidate affected queries to trigger refetches.

```typescript
// lib/hooks/useRealtime.ts
'use client';

import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';

export function useRealtimeSync() {
  const queryClient = useQueryClient();

  useEffect(() => {
    const eventSource = new EventSource('/api/events/stream');

    eventSource.onmessage = (event) => {
      const kafkaEvent = JSON.parse(event.data);

      // Invalidate queries based on event type
      switch (kafkaEvent.eventType) {
        case 'CallReceived':
          queryClient.invalidateQueries({ queryKey: ['calls'] });
          break;

        case 'CallTranscribed':
          queryClient.invalidateQueries({
            queryKey: ['calls', kafkaEvent.aggregateId],
          });
          queryClient.invalidateQueries({
            queryKey: ['transcription', kafkaEvent.aggregateId],
          });
          break;

        case 'SentimentAnalyzed':
          queryClient.invalidateQueries({
            queryKey: ['sentiment', kafkaEvent.aggregateId],
          });
          break;

        case 'CallAudited':
          queryClient.invalidateQueries({ queryKey: ['audit'] });
          break;
      }

      // Show notification
      useNotificationStore.getState().addNotification({
        type: 'info',
        title: 'Call Update',
        message: `${kafkaEvent.eventType} for call ${kafkaEvent.aggregateId}`,
      });
    };

    return () => eventSource.close();
  }, [queryClient]);
}
```

### Usage in Root Layout

```typescript
// app/(dashboard)/layout.tsx
'use client';

import { useRealtimeSync } from '@/lib/hooks/useRealtime';

export default function DashboardLayout({ children }) {
  useRealtimeSync(); // Subscribe to real-time events

  return <div>{children}</div>;
}
```

---

## State Management Decision Tree

```
Is it data from the server?
├─ YES → Use TanStack Query
│
Is it global UI state (theme, sidebar)?
├─ YES → Use Zustand
│
Is it filter/search state that should be shareable?
├─ YES → Use URL searchParams
│
Is it complex form state?
├─ YES → Use React Hook Form
│
Is it simple component state?
└─ YES → Use useState/useReducer
```

---

## Best Practices

### 1. Don't Duplicate Server State
❌ **Wrong**: Fetching data with TanStack Query and storing it in Zustand
```typescript
const { data } = useCallsList();
const setCalls = useCallStore((state) => state.setCalls);
setCalls(data); // Don't do this!
```

✅ **Right**: Use TanStack Query directly
```typescript
const { data } = useCallsList();
// Use data directly in component
```

### 2. Use URL State for Shareable Filters
✅ **Right**: Filters in URL
```
/calls?status=completed&agent=123&date=2025-01-01
```

❌ **Wrong**: Filters in Zustand (not shareable)

### 3. Optimistic Updates for Better UX
```typescript
const uploadMutation = useMutation({
  mutationFn: uploadCall,
  onMutate: async (newCall) => {
    // Show immediately in UI
    queryClient.setQueryData(['calls'], (old) => [newCall, ...old]);
  },
  onError: (_, __, context) => {
    // Rollback on error
    queryClient.setQueryData(['calls'], context.previousCalls);
  },
});
```

### 4. Persist Important State
```typescript
// Auth state should persist across page refreshes
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({ /* ... */ }),
    { name: 'auth-storage' }
  )
);
```

---

## Summary

This hybrid approach gives us:

✅ **Best Performance**: Each state type uses the optimal tool
✅ **Type Safety**: Full TypeScript support across all state management
✅ **Developer Experience**: Simple APIs, great DevTools
✅ **Real-Time Updates**: SSE integration with automatic cache invalidation
✅ **Shareable State**: URL-based filters for bookmarking
✅ **Minimal Boilerplate**: No Redux actions/reducers

**Next Document**: `04_API_INTEGRATION.md` for detailed backend integration patterns
