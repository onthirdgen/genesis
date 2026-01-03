# Design System & UI Guidelines - Call Auditing Platform

**Status**: Planning Phase
**Last Updated**: 2025-12-31
**Framework**: Tailwind CSS + Shadcn/ui

---

## Design Philosophy

**Modern, Professional, Data-Dense**

The Call Auditing Platform is a data-heavy application used by customer service teams, compliance officers, and analysts. The design should prioritize:

1. **Information Density**: Show as much relevant data as possible without overwhelming
2. **Clarity**: Clear visual hierarchy, easy to scan
3. **Accessibility**: WCAG 2.1 AA compliant
4. **Speed**: Fast interactions, minimal loading states
5. **Trust**: Professional appearance that inspires confidence

**Design Inspirations**:
- Datadog (data visualization excellence)
- Linear (clean, fast, minimalist)
- Vercel Dashboard (modern, sleek)
- Stripe Dashboard (trustworthy, professional)

---

## Color Palette

### Primary Colors

```typescript
// tailwind.config.ts
export default {
  theme: {
    extend: {
      colors: {
        // Brand Colors
        primary: {
          50: '#f0f9ff',
          100: '#e0f2fe',
          200: '#bae6fd',
          300: '#7dd3fc',
          400: '#38bdf8',
          500: '#0ea5e9', // Main brand color
          600: '#0284c7',
          700: '#0369a1',
          800: '#075985',
          900: '#0c4a6e',
        },

        // Accent Colors
        accent: {
          50: '#faf5ff',
          100: '#f3e8ff',
          200: '#e9d5ff',
          300: '#d8b4fe',
          400: '#c084fc',
          500: '#a855f7', // Secondary brand color
          600: '#9333ea',
          700: '#7e22ce',
          800: '#6b21a8',
          900: '#581c87',
        },

        // Semantic Colors
        success: {
          50: '#f0fdf4',
          500: '#22c55e',
          700: '#15803d',
        },
        warning: {
          50: '#fffbeb',
          500: '#f59e0b',
          700: '#b45309',
        },
        error: {
          50: '#fef2f2',
          500: '#ef4444',
          700: '#b91c1c',
        },
        info: {
          50: '#eff6ff',
          500: '#3b82f6',
          700: '#1d4ed8',
        },

        // Sentiment Colors (VoC-specific)
        sentiment: {
          positive: '#22c55e',
          neutral: '#64748b',
          negative: '#ef4444',
        },

        // Compliance Status Colors
        compliance: {
          passed: '#22c55e',
          failed: '#ef4444',
          needsReview: '#f59e0b',
        },

        // Background & Surface
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        card: 'hsl(var(--card))',
        'card-foreground': 'hsl(var(--card-foreground))',
        popover: 'hsl(var(--popover))',
        'popover-foreground': 'hsl(var(--popover-foreground))',

        // Borders
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
      },
    },
  },
};
```

### Dark Mode Support

```css
/* globals.css */
@layer base {
  :root {
    --background: 0 0% 100%;
    --foreground: 222.2 84% 4.9%;
    --card: 0 0% 100%;
    --card-foreground: 222.2 84% 4.9%;
    /* ... */
  }

  .dark {
    --background: 222.2 84% 4.9%;
    --foreground: 210 40% 98%;
    --card: 222.2 84% 4.9%;
    --card-foreground: 210 40% 98%;
    /* ... */
  }
}
```

---

## Typography

### Font Families

```typescript
// app/layout.tsx
import { Inter, JetBrains_Mono } from 'next/font/google';

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-sans',
  display: 'swap',
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ['latin'],
  variable: '--font-mono',
  display: 'swap',
});

export default function RootLayout({ children }) {
  return (
    <html lang="en" className={`${inter.variable} ${jetbrainsMono.variable}`}>
      <body>{children}</body>
    </html>
  );
}
```

### Type Scale

```typescript
// tailwind.config.ts
export default {
  theme: {
    extend: {
      fontSize: {
        'xs': ['0.75rem', { lineHeight: '1rem' }],
        'sm': ['0.875rem', { lineHeight: '1.25rem' }],
        'base': ['1rem', { lineHeight: '1.5rem' }],
        'lg': ['1.125rem', { lineHeight: '1.75rem' }],
        'xl': ['1.25rem', { lineHeight: '1.75rem' }],
        '2xl': ['1.5rem', { lineHeight: '2rem' }],
        '3xl': ['1.875rem', { lineHeight: '2.25rem' }],
        '4xl': ['2.25rem', { lineHeight: '2.5rem' }],
        '5xl': ['3rem', { lineHeight: '1' }],
      },
      fontFamily: {
        sans: ['var(--font-sans)', 'system-ui', 'sans-serif'],
        mono: ['var(--font-mono)', 'monospace'],
      },
    },
  },
};
```

### Text Styles

```tsx
// Usage examples
<h1 className="text-4xl font-bold tracking-tight">Dashboard</h1>
<h2 className="text-2xl font-semibold">Recent Calls</h2>
<h3 className="text-lg font-medium">Call Details</h3>
<p className="text-base text-muted-foreground">Supporting text</p>
<code className="font-mono text-sm bg-muted px-1 rounded">callId: abc123</code>
```

---

## Spacing System

Use Tailwind's default spacing scale (based on 0.25rem = 4px):

```tsx
// Common spacing patterns
<div className="p-4">       {/* 16px padding */}
<div className="mb-6">      {/* 24px margin bottom */}
<div className="gap-8">     {/* 32px gap in flex/grid */}
<div className="space-y-4"> {/* 16px vertical spacing between children */}
```

**Key Spacing Values**:
- `2` (8px) - Tight spacing (icon + text)
- `4` (16px) - Standard spacing (cards, sections)
- `6` (24px) - Comfortable spacing (between sections)
- `8` (32px) - Large spacing (major sections)
- `12` (48px) - Extra large spacing (page sections)

---

## Component Patterns

### Cards

```tsx
// components/common/MetricCard.tsx
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';

export function MetricCard({ title, value, icon, trend }: MetricCardProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
        {icon}
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        {trend && (
          <p className="text-xs text-muted-foreground">
            <span className={trend > 0 ? 'text-success-500' : 'text-error-500'}>
              {trend > 0 ? '+' : ''}{trend}%
            </span>{' '}
            from last period
          </p>
        )}
      </CardContent>
    </Card>
  );
}
```

### Badges (Status Indicators)

```tsx
// components/calls/CallStatusBadge.tsx
import { Badge } from '@/components/ui/badge';
import { CallStatus } from '@/types/models';

const statusConfig: Record<CallStatus, { label: string; variant: 'default' | 'secondary' | 'success' | 'destructive' }> = {
  uploaded: { label: 'Uploaded', variant: 'secondary' },
  processing: { label: 'Processing', variant: 'default' },
  transcribing: { label: 'Transcribing', variant: 'default' },
  analyzing: { label: 'Analyzing', variant: 'default' },
  completed: { label: 'Completed', variant: 'success' },
  failed: { label: 'Failed', variant: 'destructive' },
};

export function CallStatusBadge({ status }: { status: CallStatus }) {
  const { label, variant } = statusConfig[status];
  return <Badge variant={variant}>{label}</Badge>;
}
```

### Tables

```tsx
// components/calls/CallTable.tsx
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/components/ui/table';

export function CallTable({ calls }: { calls: Call[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Call ID</TableHead>
          <TableHead>Agent</TableHead>
          <TableHead>Caller</TableHead>
          <TableHead>Duration</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Date</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {calls.map((call) => (
          <TableRow key={call.callId}>
            <TableCell className="font-mono text-sm">{call.callId}</TableCell>
            <TableCell>{call.metadata.agentId}</TableCell>
            <TableCell>{call.metadata.callerId}</TableCell>
            <TableCell>{formatDuration(call.duration)}</TableCell>
            <TableCell>
              <CallStatusBadge status={call.status} />
            </TableCell>
            <TableCell>{formatDate(call.metadata.timestamp)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
```

### Loading States

```tsx
// components/common/LoadingSpinner.tsx
export function LoadingSpinner({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  const sizeClasses = {
    sm: 'h-4 w-4',
    md: 'h-8 w-8',
    lg: 'h-12 w-12',
  };

  return (
    <div className="flex items-center justify-center">
      <div
        className={`${sizeClasses[size]} animate-spin rounded-full border-2 border-primary-200 border-t-primary-600`}
      />
    </div>
  );
}

// Skeleton loading
import { Skeleton } from '@/components/ui/skeleton';

export function CallTableSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 5 }).map((_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}
```

### Empty States

```tsx
// components/common/EmptyState.tsx
import { FileQuestion } from 'lucide-react';

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      <FileQuestion className="h-12 w-12 text-muted-foreground mb-4" />
      <h3 className="text-lg font-semibold mb-2">{title}</h3>
      <p className="text-sm text-muted-foreground mb-4 max-w-sm">{description}</p>
      {action}
    </div>
  );
}
```

---

## Data Visualization

### Chart Color Palette

```typescript
// lib/utils/chart-colors.ts
export const chartColors = {
  primary: '#0ea5e9',
  secondary: '#a855f7',
  success: '#22c55e',
  warning: '#f59e0b',
  error: '#ef4444',
  info: '#3b82f6',

  // Multi-series colors (for comparing agents, categories, etc.)
  series: [
    '#0ea5e9',
    '#a855f7',
    '#22c55e',
    '#f59e0b',
    '#ec4899',
    '#8b5cf6',
    '#14b8a6',
    '#f97316',
  ],

  // Sentiment gradient
  sentimentGradient: ['#ef4444', '#f59e0b', '#22c55e'],
};
```

### Sentiment Line Chart Example

```tsx
// components/charts/SentimentLineChart.tsx
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { chartColors } from '@/lib/utils/chart-colors';

export function SentimentLineChart({ data }: { data: SentimentSegment[] }) {
  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
        <XAxis
          dataKey="startTime"
          tickFormatter={(value) => `${Math.floor(value / 60)}m`}
          className="text-xs"
        />
        <YAxis domain={[-1, 1]} className="text-xs" />
        <Tooltip
          contentStyle={{
            backgroundColor: 'hsl(var(--popover))',
            border: '1px solid hsl(var(--border))',
            borderRadius: '0.5rem',
          }}
        />
        <Line
          type="monotone"
          dataKey="score"
          stroke={chartColors.primary}
          strokeWidth={2}
          dot={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
```

---

## Layout Patterns

### Dashboard Grid

```tsx
// app/(dashboard)/page.tsx
export default function DashboardPage() {
  return (
    <div className="space-y-6">
      {/* Metrics Row */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <MetricCard title="Total Calls" value="1,234" />
        <MetricCard title="Avg Sentiment" value="0.72" />
        <MetricCard title="Compliance Rate" value="94%" />
        <MetricCard title="Escalations" value="12" />
      </div>

      {/* Charts Row */}
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Call Volume Trends</CardTitle>
          </CardHeader>
          <CardContent>
            <CallVolumeChart />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Sentiment Distribution</CardTitle>
          </CardHeader>
          <CardContent>
            <SentimentPieChart />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
```

### Sidebar Layout

```tsx
// app/(dashboard)/layout.tsx
export default function DashboardLayout({ children }) {
  return (
    <div className="flex h-screen">
      {/* Sidebar */}
      <aside className="w-64 border-r bg-card">
        <Sidebar />
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header */}
        <header className="border-b bg-card px-6 py-4">
          <Header />
        </header>

        {/* Content Area */}
        <main className="flex-1 overflow-auto p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
```

---

## Iconography

### Icon Library: Lucide React

```bash
pnpm add lucide-react
```

```tsx
import {
  Phone,
  Upload,
  TrendingUp,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Clock,
  BarChart,
  PieChart,
  Settings,
  User,
  LogOut,
} from 'lucide-react';

// Usage
<Phone className="h-5 w-5 text-primary-500" />
```

**Icon Sizing**:
- `h-4 w-4` (16px) - Small icons (table cells, inline)
- `h-5 w-5` (20px) - Standard icons (buttons, cards)
- `h-6 w-6` (24px) - Large icons (headers)
- `h-12 w-12` (48px) - Extra large (empty states)

---

## Accessibility Guidelines

### Color Contrast

All text must meet WCAG 2.1 AA standards:
- **Normal text**: 4.5:1 contrast ratio
- **Large text** (18px+): 3:1 contrast ratio

Use tools like [Contrast Checker](https://webaim.org/resources/contrastchecker/) to validate.

### Keyboard Navigation

Ensure all interactive elements are keyboard accessible:

```tsx
// Focus styles
<button className="focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2">
  Submit
</button>
```

### ARIA Labels

```tsx
// Icon-only buttons need labels
<button aria-label="Close dialog">
  <X className="h-4 w-4" />
</button>

// Screen reader announcements
<div role="status" aria-live="polite">
  {isLoading && <span className="sr-only">Loading...</span>}
</div>
```

### Skip Links

```tsx
// app/layout.tsx
<a
  href="#main-content"
  className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:px-4 focus:py-2 focus:bg-primary-500 focus:text-white"
>
  Skip to main content
</a>
```

---

## Responsive Design

### Breakpoints

```typescript
// tailwind.config.ts (default Tailwind breakpoints)
{
  sm: '640px',
  md: '768px',
  lg: '1024px',
  xl: '1280px',
  '2xl': '1536px',
}
```

### Mobile-First Approach

```tsx
// Stack on mobile, grid on desktop
<div className="flex flex-col md:grid md:grid-cols-2 lg:grid-cols-4 gap-4">
  <MetricCard />
  <MetricCard />
  <MetricCard />
  <MetricCard />
</div>

// Hide sidebar on mobile, show on desktop
<aside className="hidden lg:block w-64 border-r">
  <Sidebar />
</aside>

// Mobile navigation
<Sheet> {/* Shadcn/ui mobile drawer */}
  <SheetTrigger className="lg:hidden">
    <Menu className="h-6 w-6" />
  </SheetTrigger>
  <SheetContent side="left">
    <Sidebar />
  </SheetContent>
</Sheet>
```

---

## Animation & Transitions

### Subtle Animations

```tsx
// Hover effects
<button className="transition-colors hover:bg-primary-600">
  Click me
</button>

// Loading states
<div className="animate-pulse bg-muted h-4 w-32 rounded" />

// Slide-in panels
<div className="transition-transform duration-300 ease-in-out translate-x-0 data-[state=closed]:translate-x-full">
  Panel content
</div>
```

### Framer Motion (for complex animations)

```bash
pnpm add framer-motion
```

```tsx
import { motion } from 'framer-motion';

<motion.div
  initial={{ opacity: 0, y: 20 }}
  animate={{ opacity: 1, y: 0 }}
  transition={{ duration: 0.3 }}
>
  Content
</motion.div>
```

---

## Summary: Design System Checklist

✅ **Color Palette**: Primary, accent, semantic, sentiment colors defined
✅ **Typography**: Inter (sans), JetBrains Mono (code), type scale
✅ **Spacing**: Consistent 4px-based spacing system
✅ **Components**: Shadcn/ui for accessible, customizable components
✅ **Charts**: Recharts with consistent color palette
✅ **Icons**: Lucide React for modern, consistent iconography
✅ **Accessibility**: WCAG 2.1 AA compliance, keyboard navigation
✅ **Responsive**: Mobile-first, breakpoint-based layouts
✅ **Animations**: Subtle transitions, loading states
✅ **Dark Mode**: Full dark mode support

**Next Document**: `06_IMPLEMENTATION_ROADMAP.md` for phased rollout plan
