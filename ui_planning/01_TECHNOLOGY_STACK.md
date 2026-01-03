# UI Technology Stack - Call Auditing Platform

**Status**: Planning Phase
**Last Updated**: 2025-12-31
**Target**: Modern React-based web application with real-time capabilities

---

## Overview

This document outlines the recommended technology stack for building a modern, production-ready UI for the Call Auditing Platform. The stack prioritizes **developer experience, performance, type safety, and real-time capabilities** while using the latest stable versions (as of 2025).

---

## Core Framework

### Next.js 15 (App Router)
**Version**: 15.x (latest stable)
**Why Next.js over Create React App?**

- **Server-Side Rendering (SSR)**: Better SEO and initial page load performance
- **Server Components**: Reduce client-side JavaScript bundle size
- **Built-in API Routes**: Can proxy to backend services, handle auth tokens securely
- **File-based Routing**: Intuitive, scalable routing structure
- **Image Optimization**: Automatic image optimization with next/image
- **TypeScript Support**: First-class TypeScript integration
- **Production-Ready**: Battle-tested at scale (Vercel, Netflix, Hulu)
- **App Router**: Modern React Server Components architecture

**Alternative Considered**: Vite + React Router (lighter, but less opinionated - good for SPAs)

---

## Language

### TypeScript 5.x
**Version**: 5.7+ (latest)
**Why TypeScript?**

- **Type Safety**: Catch bugs at compile time, especially with complex API responses
- **Better DX**: IntelliSense, auto-completion, refactoring tools
- **Self-Documenting**: Types serve as inline documentation
- **Reduced Runtime Errors**: Fewer undefined/null errors
- **Team Scalability**: Essential for multi-developer projects

**Configuration**: Strict mode enabled (`strict: true`)

---

## UI Component Libraries

### Shadcn/ui + Radix UI
**Why Shadcn/ui?**

- **Copy-Paste Components**: Own your components, no package dependency lock-in
- **Built on Radix UI**: Accessible, unstyled primitives (WAI-ARIA compliant)
- **Tailwind CSS Native**: Integrates perfectly with Tailwind
- **Customizable**: Modify components directly in your codebase
- **Modern Design**: Beautiful, production-ready components
- **Active Community**: Rapidly growing, well-maintained

**Key Components Needed**:
- Tables (call lists, analytics tables)
- Dialogs/Modals (upload audio, view details)
- Charts (via Recharts integration)
- Forms (search, filters, settings)
- Cards, Badges, Tooltips, Dropdowns

**Alternative Considered**: Material UI (heavier, less modern), Ant Design (good but opinionated)

---

## Styling

### Tailwind CSS 4.x
**Version**: 4.0+ (latest)
**Why Tailwind?**

- **Utility-First**: Rapid UI development
- **Design Consistency**: Enforces design system constraints
- **Smaller Bundle**: PurgeCSS built-in, only ships used styles
- **Responsive**: Mobile-first responsive design utilities
- **Dark Mode**: Built-in dark mode support
- **Customizable**: Easy to extend with custom design tokens

**Configuration**: Custom color palette matching brand, typography scale, spacing system

**CSS-in-JS Alternative**: Considered Emotion/Styled-Components but Tailwind is faster and has better DX in 2025

---

## State Management

### Zustand 5.x (Global State)
**Why Zustand over Redux?**

- **Simpler API**: Less boilerplate than Redux/Redux Toolkit
- **Better Performance**: Fine-grained reactivity, no Context re-render issues
- **TypeScript-First**: Excellent type inference
- **Smaller Bundle**: ~1KB vs 10KB+ for Redux
- **Middleware Support**: Persist, Immer, DevTools available
- **Modern**: Active development, growing adoption

**Use Cases**:
- User authentication state
- Global UI state (sidebar open/closed, theme)
- Selected filters across pages
- Notification queue

### TanStack Query (React Query) v5.x (Server State)
**Why React Query?**

- **Server State is Different**: Caching, invalidation, background refetching
- **Automatic Caching**: Reduces unnecessary API calls
- **Optimistic Updates**: Better UX for mutations
- **Background Sync**: Keep data fresh automatically
- **Pagination & Infinite Scroll**: Built-in support
- **DevTools**: Excellent debugging experience

**Use Cases**:
- Fetching call lists, analytics data
- VoC insights, audit results
- Agent performance metrics
- Real-time data synchronization

---

## Real-Time Communication

### Server-Sent Events (SSE) + EventSource API
**Why SSE over WebSockets?**

- **Simpler**: Unidirectional (server → client) is sufficient for this use case
- **HTTP/2**: Works over HTTP, easier to deploy (no special nginx config)
- **Auto Reconnect**: Built-in reconnection logic
- **Firewall-Friendly**: Works through proxies and firewalls
- **Event-Based**: Perfect for Kafka event stream consumption

**Implementation**:
- Create Next.js API route: `/api/events/stream`
- Route consumes Kafka events and streams to client
- Client uses EventSource API to listen
- Fallback to polling if SSE not supported (older browsers)

**WebSocket Alternative**: Consider if bidirectional communication needed later (e.g., chat support)

**Specific Use Cases**:
- Live call processing status updates
- Real-time compliance alerts
- Dashboard metric updates
- New VoC insights notifications

---

## Data Visualization

### Recharts 2.x
**Why Recharts?**

- **React Native**: Built for React, declarative API
- **Customizable**: Full control over chart appearance
- **Responsive**: Mobile-friendly charts
- **Composable**: Build complex charts from primitives
- **TypeScript Support**: Well-typed APIs

**Chart Types Needed**:
- Line charts (sentiment trends over time)
- Bar charts (call volume by agent, compliance scores)
- Pie/Donut charts (sentiment distribution, topic breakdown)
- Area charts (call volume trends)
- Scatter plots (sentiment vs. call duration)

**Alternative Considered**:
- Chart.js (less React-friendly)
- Victory (good but heavier)
- D3.js (too low-level, steeper learning curve)

---

## Forms & Validation

### React Hook Form 7.x + Zod
**Why React Hook Form?**

- **Performance**: Minimizes re-renders (uncontrolled components)
- **Small Bundle**: 9KB minified
- **TypeScript**: Excellent type inference
- **DevTools**: Browser extension for debugging

**Why Zod for Validation?**

- **TypeScript-First**: Schema = TypeScript type
- **Composable**: Reusable validation schemas
- **Error Messages**: Customizable, i18n-friendly
- **Runtime Safety**: Validates API responses too

**Use Cases**:
- Audio file upload form (drag & drop + validation)
- Search and filter forms
- Settings and configuration forms
- Agent performance date range selectors

---

## File Upload

### React Dropzone
**Why React Dropzone?**

- **Drag & Drop**: Modern UX for audio file uploads
- **File Validation**: Size limits, MIME type checking
- **Preview**: Show file details before upload
- **Accessibility**: Keyboard navigation, screen reader support

**Integration**:
- Accept: `.wav`, `.mp3`, `.m4a`
- Max size: 100MB (configurable)
- Progress bar using native browser APIs or axios
- Upload to API Gateway → Call Ingestion Service → MinIO

---

## Performance Optimization

### TanStack Virtual (React Virtual)
**Why Virtual Scrolling?**

- **Large Lists Performance**: Essential for rendering 500+ rows in tables
- **Smooth Scrolling**: Eliminates scroll jank by rendering only visible items
- **Memory Efficient**: DOM nodes scale with viewport size, not data size

**Use Cases**:
- Call list tables (1000+ calls)
- Agent performance tables
- VoC insights lists
- Audit violations tables

**Performance Impact**:
```
Without virtualization: 1000 rows = ~60,000 DOM nodes = 500ms+ scroll jank
With virtualization: 1000 rows = ~100 DOM nodes (visible + overscan) = <16ms smooth
```

**Installation**:
```bash
pnpm add @tanstack/react-virtual
```

**Example**:
```typescript
import { useVirtualizer } from '@tanstack/react-virtual';

const rowVirtualizer = useVirtualizer({
  count: calls.length,
  getScrollElement: () => parentRef.current,
  estimateSize: () => 60, // Row height
  overscan: 10, // Extra rows to render
});
```

---

## API Client

### Axios 1.x
**Why Axios over Fetch?**

- **Interceptors**: Centralized auth token injection, error handling
- **Automatic JSON**: No manual `.json()` calls
- **Request/Response Transformation**: Easier to modify data
- **Cancel Tokens**: Abort requests (useful for search)
- **Better Error Handling**: Clearer error objects
- **TypeScript Support**: Well-typed with generics

**Configuration**:
```typescript
// lib/api-client.ts
const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor (add auth token)
apiClient.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor (handle errors)
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Redirect to login
    }
    return Promise.reject(error);
  }
);
```

---

## Authentication

### NextAuth.js v5 (Auth.js)
**Why NextAuth.js?**

- **Next.js Native**: Seamless integration with App Router
- **Multiple Providers**: OAuth (Google, GitHub), credentials, magic links
- **JWT or Database Sessions**: Flexible session management
- **CSRF Protection**: Built-in security
- **TypeScript**: Fully typed

**Recommended Flow**:
1. Email/Password authentication (for MVP)
2. Add OAuth providers later (Google SSO for enterprise)
3. Store sessions in JWT (stateless) or PostgreSQL

**Integration**:
- Protected routes via middleware
- API route protection with `getServerSession`
- Client-side auth status with `useSession` hook

**Alternative**: Clerk (easier but paid), Auth0 (enterprise but complex)

---

## Testing

### Vitest + React Testing Library + Playwright
**Unit/Integration Tests**: Vitest 2.x
- **Why Vitest?** Vite-native, faster than Jest, better ESM support
- **Happy DOM**: Lightweight DOM implementation for tests
- **Component Testing**: React Testing Library for user-centric tests

**E2E Tests**: Playwright 1.x
- **Why Playwright?** Multi-browser, fast, reliable, great DX
- **Auto-wait**: No need for manual waits/sleeps
- **Video/Screenshots**: Built-in debugging tools
- **Parallel Execution**: Fast test runs

**Testing Strategy**:
- Unit tests: Pure functions, hooks, utilities
- Integration tests: Components with mocked APIs
- E2E tests: Critical user flows (upload call, view dashboard)

---

## Development Tools

### ESLint + Prettier + Husky
**ESLint 9.x**: Code quality, catch bugs
**Prettier 3.x**: Code formatting, consistency
**Husky + lint-staged**: Pre-commit hooks

**Configuration**:
- ESLint: `@next/eslint-config`, `eslint-config-prettier`
- Prettier: 2-space indent, single quotes, trailing commas
- Husky: Run lint + type-check before commit

### Sentry (Error Monitoring & Performance)
**Version**: @sentry/nextjs latest
**Why Include in Phase 1?**

- **Early Bug Detection**: Catch issues from day one, not after launch
- **Production Debugging**: Source maps, session replay, breadcrumbs
- **Performance Monitoring**: Track slow API calls and page loads
- **Zero Config**: Auto-instrumentation via Next.js integration

**Installation**:
```bash
pnpm add @sentry/nextjs
npx @sentry/wizard@latest -i nextjs
```

**Key Features**:
- Automatic error capture (client & server)
- Session replay for debugging user issues
- Performance monitoring (Core Web Vitals)
- Source map upload (see original TypeScript in errors)
- Slack/email alerting

**When to Enable**: Phase 1.1 (Project Setup), not post-launch

### Environment Variable Validation
**Why Validate?**

- **Fail Fast**: Catch misconfigurations at startup, not runtime
- **Type Safety**: Zod validates and provides TypeScript types
- **Clear Errors**: Developer-friendly error messages

**Implementation**:
```typescript
// lib/env.ts
import { z } from 'zod';

const envSchema = z.object({
  NEXT_PUBLIC_API_URL: z.string().url(),
  NEXTAUTH_URL: z.string().url(),
  NEXTAUTH_SECRET: z.string().min(32),
  NEXT_PUBLIC_SENTRY_DSN: z.string().url().optional(),
});

export const env = envSchema.parse({
  NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
  NEXTAUTH_URL: process.env.NEXTAUTH_URL,
  NEXTAUTH_SECRET: process.env.NEXTAUTH_SECRET,
  NEXT_PUBLIC_SENTRY_DSN: process.env.NEXT_PUBLIC_SENTRY_DSN,
});

// Usage: import { env } from '@/lib/env'
```

---

## Build & Deployment

### Vercel (Recommended) or Docker
**Option 1: Vercel** (Easiest)
- One-click deployment from GitHub
- Automatic HTTPS, CDN, preview deployments
- Edge functions for API routes
- Free tier sufficient for prototypes

**Option 2: Docker + Self-Hosted**
- Dockerfile for Next.js production build
- Deploy alongside backend services
- Use nginx for reverse proxy
- Better for on-premise deployments

**Environment Variables**:
```bash
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=ws://localhost:8080
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=your-secret-key
DATABASE_URL=postgresql://... # if using DB sessions
```

---

## Performance Optimization

### Built-in Next.js Features
- **Image Optimization**: `next/image` component
- **Font Optimization**: `next/font` for web fonts
- **Code Splitting**: Automatic route-based splitting
- **Lazy Loading**: Dynamic imports for heavy components

### Additional Tools
- **Bundle Analyzer**: `@next/bundle-analyzer` to identify large dependencies
- **Lighthouse CI**: Automated performance testing
- **React DevTools Profiler**: Identify slow components

---

## Accessibility (a11y)

### Tools & Standards
- **Radix UI**: WAI-ARIA compliant primitives
- **React Aria**: Accessibility hooks (if custom components needed)
- **eslint-plugin-jsx-a11y**: Catch accessibility issues at build time
- **Axe DevTools**: Browser extension for manual testing
- **WCAG 2.1 Level AA**: Target compliance level

**Key Requirements**:
- Keyboard navigation for all interactive elements
- Screen reader support (semantic HTML, ARIA labels)
- Color contrast ratios (4.5:1 minimum)
- Focus indicators on all focusable elements

---

## Internationalization (i18n) - Future

### next-intl
**When Needed**: If multi-language support required
- Route-based localization (`/en/dashboard`, `/es/dashboard`)
- Translation files in JSON
- Type-safe translations with TypeScript

**For MVP**: English-only, but design components to support i18n later

---

## Monitoring & Analytics

### Sentry (Error Tracking)
- **Client-Side Errors**: Uncaught exceptions, API errors
- **Performance Monitoring**: Slow pages, API calls
- **User Context**: Session replay for debugging

### Vercel Analytics (Optional)
- **Web Vitals**: LCP, FID, CLS tracking
- **Real User Monitoring**: Actual user performance data

### Mixpanel/PostHog (Product Analytics)
- Track user behavior (feature usage, conversion funnels)
- Open-source alternative: PostHog (self-hosted)

---

## Version Control & CI/CD

### GitHub Actions
**Workflow**:
```yaml
name: CI
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - Checkout code
      - Setup Node.js 22.x
      - Install dependencies
      - Run TypeScript type-check
      - Run ESLint
      - Run Vitest tests
      - Run Playwright E2E tests
      - Build Next.js app
```

**Deployment**:
- Merge to `main` → Auto-deploy to production (Vercel)
- PR creation → Deploy preview environment

---

## Development Environment

### Required Software
- **Node.js**: v22.x LTS (latest)
- **pnpm**: 10.x (faster than npm, efficient disk usage)
- **VS Code**: Recommended IDE
  - Extensions: ESLint, Prettier, Tailwind IntelliSense, TypeScript

### Project Initialization
```bash
# Create Next.js 15 app with TypeScript
npx create-next-app@latest call-auditing-ui \
  --typescript \
  --tailwind \
  --app \
  --src-dir \
  --import-alias "@/*"

# Install dependencies
cd call-auditing-ui
pnpm add zustand @tanstack/react-query axios react-hook-form zod
pnpm add recharts react-dropzone
pnpm add next-auth
pnpm add -D @types/node @types/react vitest @testing-library/react happy-dom
pnpm add -D prettier eslint-config-prettier husky lint-staged

# Initialize Shadcn/ui
npx shadcn@latest init
```

---

## Summary: Complete Tech Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| **Framework** | Next.js | 15.x | React framework with SSR/SSG |
| **Language** | TypeScript | 5.x | Type-safe development |
| **UI Components** | Shadcn/ui + Radix | Latest | Accessible, customizable components |
| **Styling** | Tailwind CSS | 4.x | Utility-first CSS framework |
| **Global State** | Zustand | 5.x | Lightweight state management |
| **Server State** | TanStack Query | 5.x | API data fetching & caching |
| **Real-Time** | SSE (EventSource) | Native | Server-sent events for live updates |
| **Charts** | Recharts | 2.x | Data visualization |
| **Forms** | React Hook Form + Zod | 7.x + 3.x | Form handling & validation |
| **File Upload** | React Dropzone | Latest | Drag-and-drop file uploads |
| **HTTP Client** | Axios | 1.x | API communication |
| **Auth** | NextAuth.js | 5.x | Authentication & session management |
| **Testing** | Vitest + Playwright | 2.x + 1.x | Unit, integration, E2E tests |
| **Linting** | ESLint + Prettier | 9.x + 3.x | Code quality & formatting |
| **Package Manager** | pnpm | 10.x | Fast, efficient dependency management |
| **Runtime** | Node.js | 22.x | JavaScript runtime |

---

## Why This Stack? (Decision Matrix)

| Criteria | Score (1-10) | Notes |
|----------|--------------|-------|
| **Developer Experience** | 9/10 | TypeScript, excellent tooling, hot reload |
| **Performance** | 9/10 | SSR, code splitting, optimized bundles |
| **Type Safety** | 10/10 | TypeScript + Zod end-to-end |
| **Real-Time Capable** | 8/10 | SSE for Kafka events, WebSocket if needed |
| **Scalability** | 9/10 | Server components, edge deployment |
| **Community Support** | 10/10 | All tools have active communities |
| **Learning Curve** | 7/10 | Moderate (Next.js App Router is new) |
| **Long-Term Viability** | 9/10 | Industry-standard tools, active maintenance |
| **Cost** | 10/10 | All open-source, free hosting tier available |

---

## Next Steps

1. **Set Up Project**: Initialize Next.js 15 app with TypeScript
2. **Configure Shadcn/ui**: Install base components (Button, Card, Dialog, Table)
3. **API Client**: Create Axios instance with interceptors
4. **Authentication**: Set up NextAuth.js with credentials provider
5. **Routing**: Define app structure (`/dashboard`, `/calls`, `/analytics`, etc.)
6. **First Feature**: Implement call list page with real data

See `02_ARCHITECTURE_OVERVIEW.md` for application architecture design.
