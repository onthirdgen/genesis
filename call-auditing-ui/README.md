# Call Auditing Platform - Frontend

Modern web application for the Call Auditing Platform with Voice of the Customer (VoC) analytics. Built with Next.js 15, React 19, and TypeScript.

## Features

- ✅ **Authentication** - Login with form validation and session management
- ✅ **Dashboard** - Real-time stats, recent calls, compliance alerts
- ✅ **Protected Routes** - Automatic redirect to login for unauthenticated users
- ✅ **Toast Notifications** - User-friendly notifications for actions
- ✅ **Responsive Design** - Mobile-first design with Tailwind CSS
- ✅ **Dark Mode Support** - Built-in dark mode theming
- ✅ **Type Safety** - Full TypeScript with strict mode
- 🚧 **Call Management** - Upload, list, and view call recordings (in progress)
- 🚧 **Analytics Dashboard** - Charts and metrics visualization (planned)
- 🚧 **VoC Insights** - Customer insights and themes (planned)
- 🚧 **Compliance Reports** - Audit results and violations (planned)

## Tech Stack

### Core Framework
- **Next.js 15** - React framework with App Router
- **React 19** - Latest React with server components
- **TypeScript 5.7+** - Type-safe development

### Styling & Components
- **Tailwind CSS 3.x** - Utility-first CSS framework
- **Shadcn/ui** - Accessible component library built on Radix UI
- **Lucide React** - Icon library

### State Management
- **TanStack Query** - Server state, caching, and data fetching
- **Zustand** - Global client state (auth, UI preferences)
- **React Hook Form** - Form state management
- **Zod** - Schema validation

### API & Backend Communication
- **Axios** - HTTP client with interceptors
- **Server-Sent Events (SSE)** - Real-time updates (planned)

### Development Tools
- **ESLint 9** - Code linting
- **Prettier** - Code formatting
- **Vitest** - Unit testing
- **Playwright** - E2E testing
- **Husky** - Git hooks

## Getting Started

### Prerequisites

- Node.js 20+ (LTS)
- npm 10+ or pnpm 9+

### Installation

```bash
# Install dependencies
npm install

# Or with pnpm
pnpm install
```

### Development

```bash
# Start development server
npm run dev

# Open http://localhost:4142
```

**Note**: The UI runs on port 4142 by default (port 3000 conflicts with Grafana).
If you need a different port:
```bash
npm run dev -- -p 3001
```

### Login (Demo Mode)

The application uses mock authentication for development:

- **Email**: any valid email (e.g., `analyst@example.com`)
- **Password**: minimum 6 characters (e.g., `password123`)

This will create a demo user and redirect to the dashboard.

### Building for Production

```bash
# Type check
npm run type-check

# Build
npm run build

# Start production server
npm start
```

## Project Structure

```
call-auditing-ui/
├── src/
│   ├── app/                      # Next.js App Router
│   │   ├── layout.tsx            # Root layout
│   │   ├── page.tsx              # Home page
│   │   ├── login/                # Login page
│   │   └── dashboard/            # Protected dashboard
│   │       ├── layout.tsx        # Dashboard layout with sidebar
│   │       ├── page.tsx          # Dashboard home
│   │       ├── calls/            # Call management pages
│   │       ├── analytics/        # Analytics pages
│   │       ├── voc/              # VoC insights pages
│   │       ├── compliance/       # Compliance pages
│   │       └── settings/         # Settings pages
│   ├── components/
│   │   ├── ui/                   # Shadcn/ui components
│   │   │   ├── button.tsx
│   │   │   ├── card.tsx
│   │   │   ├── input.tsx
│   │   │   ├── label.tsx
│   │   │   ├── dialog.tsx
│   │   │   ├── toast.tsx
│   │   │   └── toaster.tsx
│   │   ├── layout/               # Layout components
│   │   │   ├── sidebar.tsx       # Navigation sidebar
│   │   │   └── header.tsx        # Page header
│   │   └── providers.tsx         # App providers wrapper
│   ├── lib/
│   │   ├── api/                  # API client
│   │   │   ├── client.ts         # Axios configuration
│   │   │   └── query-client.ts   # TanStack Query config
│   │   ├── hooks/                # Custom React hooks
│   │   │   ├── use-toast.ts      # Toast notifications
│   │   │   ├── use-calls.ts      # Call API hooks
│   │   │   ├── use-analytics.ts  # Analytics hooks
│   │   │   └── index.ts
│   │   ├── stores/               # Zustand stores
│   │   │   └── auth-store.ts     # Authentication state
│   │   ├── utils/
│   │   │   └── cn.ts             # Class name utility
│   │   └── env.ts                # Environment validation
│   ├── types/
│   │   └── index.ts              # TypeScript type definitions
│   └── styles/
│       └── globals.css           # Global styles
├── public/                       # Static assets
├── .env.local                    # Environment variables (local)
├── components.json               # Shadcn/ui configuration
├── next.config.ts                # Next.js configuration
├── tailwind.config.ts            # Tailwind CSS configuration
├── tsconfig.json                 # TypeScript configuration
└── package.json                  # Dependencies
```

## Environment Variables

Create a `.env.local` file in the root directory:

```bash
# API Configuration
NEXT_PUBLIC_API_URL=http://localhost:8080

# NextAuth Configuration
NEXTAUTH_URL=http://localhost:4142
NEXTAUTH_SECRET=your-secret-key-here

# Sentry (Error Tracking)
NEXT_PUBLIC_SENTRY_DSN=your-sentry-dsn
SENTRY_ORG=your-org
SENTRY_PROJECT=call-auditing-ui
```

## Available Scripts

```bash
# Development
npm run dev              # Start dev server
npm run build            # Build for production
npm start                # Start production server

# Code Quality
npm run lint             # Lint code
npm run type-check       # TypeScript type checking

# Testing
npm run test             # Run unit tests
npm run test:e2e         # Run E2E tests with Playwright
```

## API Integration

The frontend communicates with the backend through the API Gateway (`http://localhost:8080`).

### API Client

Located in `src/lib/api/client.ts`, the API client includes:
- Authentication token injection
- Global error handling
- Request/response interceptors
- Automatic retry logic

### TanStack Query Hooks

All API calls use TanStack Query for:
- Automatic caching
- Background refetching
- Optimistic updates
- Request deduplication

**Example**: Fetching calls
```typescript
import { useCalls } from '@/lib/hooks/use-calls';

function CallsList() {
  const { data, isLoading, error } = useCalls(0, 20);

  // ...
}
```

## Authentication Flow

1. User visits `/login`
2. Enters credentials (validated with Zod schema)
3. `authStore.login()` called (currently mock authentication)
4. User object stored in Zustand + localStorage
5. Redirect to `/dashboard`
6. Dashboard layout checks `isAuthenticated`
7. If false, redirects back to `/login`

**Future**: Will integrate with Spring Boot backend JWT authentication.

## Component Library

The application uses **Shadcn/ui** components, which are:
- Built on Radix UI primitives
- Fully accessible (ARIA compliant)
- Customizable with Tailwind CSS
- Copied into `src/components/ui/` (not npm package)

To add new components:
```bash
npx shadcn@latest add <component-name>
```

Example:
```bash
npx shadcn@latest add table
npx shadcn@latest add dropdown-menu
```

## Styling

### Tailwind CSS

The project uses Tailwind CSS 3.x with a custom design system:

**Colors**:
- `primary` - Brand blue
- `accent` - Secondary purple
- `success`, `warning`, `error`, `info` - Semantic colors
- `sentiment-positive`, `sentiment-neutral`, `sentiment-negative`
- `compliance-passed`, `compliance-failed`, `compliance-needsReview`

**Usage**:
```tsx
<button className="bg-primary text-white hover:bg-primary/90">
  Click me
</button>
```

### CSS Variables

All theme colors are defined as CSS variables in `globals.css`:

```css
:root {
  --primary: 221.2 83.2% 53.3%;
  --background: 0 0% 100%;
  /* ... */
}

.dark {
  --primary: 217.2 91.2% 59.8%;
  --background: 222.2 84% 4.9%;
  /* ... */
}
```

## Testing

### Unit Tests (Vitest)

```bash
npm run test
```

Test files are located next to components: `*.test.tsx`

### E2E Tests (Playwright)

```bash
npm run test:e2e
```

E2E tests are in `tests/e2e/`

## Deployment

### Vercel (Recommended)

The easiest way to deploy is using Vercel:

```bash
# Install Vercel CLI
npm i -g vercel

# Deploy
vercel
```

### Docker

Build Docker image:

```bash
docker build -t call-auditing-ui .
docker run -p 3000:3000 call-auditing-ui
```

## Troubleshooting

### Port Conflicts

The UI is configured to run on port 4142 by default to avoid conflicts with Grafana (port 3000).

If you need to change the port:

```bash
# Run on custom port
npm run dev -- -p 3001
```

### TypeScript Errors

```bash
# Clear Next.js cache
rm -rf .next

# Reinstall dependencies
rm -rf node_modules
npm install

# Run type check
npm run type-check
```

### Build Errors

Ensure all environment variables are set in `.env.local` before building.

## Contributing

See the main [CONTRIBUTING.md](../CONTRIBUTING.md) file in the root directory.

## Documentation

- [Main Architecture](../ARCHITECTURE.md) - Overall system architecture
- [UI Planning](../ui_planning/README.md) - Detailed UI design documents
- [Project Status](./PROJECT_STATUS.md) - Current implementation status
- [Technology Stack](../ui_planning/01_TECHNOLOGY_STACK.md) - Tech stack decisions
- [API Integration](../ui_planning/04_API_INTEGRATION.md) - API integration guide

## License

See LICENSE file in the root directory.
