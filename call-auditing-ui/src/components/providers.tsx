'use client';

import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { queryClient as defaultQueryClient } from '@/lib/api/query-client';
import { Toaster } from '@/components/ui/toaster';

interface ProvidersProps {
  children: React.ReactNode;
}

/**
 * Root providers wrapper for the application.
 *
 * Includes:
 * - TanStack Query (React Query) for server state management
 * - Zustand for client-side state (authentication via auth-store)
 * - Toast notifications (shadcn/ui)
 *
 * Must be a client component since it uses React hooks and context.
 */
export function Providers({ children }: ProvidersProps) {
  // Create query client instance once per app (not on every render)
  const [queryClient] = useState(() => defaultQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <Toaster />
    </QueryClientProvider>
  );
}
