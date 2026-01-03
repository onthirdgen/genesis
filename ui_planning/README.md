# UI Planning Documentation - Call Auditing Platform

**Project**: Call Auditing Platform with Voice of the Customer (VoC)
**UI Framework**: Next.js 15 (React 19) + TypeScript
**Status**: Phase 1.1 Complete - Foundation & Authentication ✅
**Last Updated**: 2026-01-01

---

## Overview

This directory contains comprehensive planning documentation for the **frontend web application** of the Call Auditing Platform. The UI will provide a modern, real-time interface for viewing call analytics, transcriptions, sentiment analysis, Voice of Customer insights, and compliance audits.

**Key Highlights**:
- ✅ **Modern Stack**: Next.js 15, TypeScript 5, Tailwind CSS 4, Shadcn/ui
- ✅ **Real-Time**: Server-Sent Events (SSE) for live Kafka event updates
- ✅ **Type-Safe**: End-to-end TypeScript from UI to API
- ✅ **Accessible**: WCAG 2.1 AA compliant, keyboard navigation
- ✅ **Fast**: Server components, code splitting, optimized bundles
- ✅ **Production-Ready**: Comprehensive testing, monitoring, deployment strategies

---

## Documentation Structure

This planning suite consists of **7 comprehensive documents** covering all aspects of the UI implementation:

### 📘 [01_TECHNOLOGY_STACK.md](./01_TECHNOLOGY_STACK.md)
**What**: Technology choices and rationale
**Covers**:
- Next.js 15 (App Router), TypeScript 5.x
- Shadcn/ui + Radix UI component library
- Tailwind CSS 4 for styling
- Zustand (global state) + TanStack Query (server state)
- React Hook Form + Zod for forms
- Recharts for data visualization
- Axios for API communication
- NextAuth.js for authentication
- Vitest + Playwright for testing

**Read this first** to understand the modern tech stack.

---

### 🏗️ [02_ARCHITECTURE_OVERVIEW.md](./02_ARCHITECTURE_OVERVIEW.md)
**What**: Application architecture and structure
**Covers**:
- High-level architecture diagram
- Next.js 15 App Router directory structure
- Routing strategy (route groups, dynamic routes)
- Server vs Client Components
- Data flow patterns (SSR, client-side fetching)
- Real-time architecture (SSE)
- Authentication flow
- Error handling
- Performance considerations

**Read this second** to understand how the app is organized.

---

### 🔄 [03_STATE_MANAGEMENT.md](./03_STATE_MANAGEMENT.md)
**What**: State management strategy
**Covers**:
- Hybrid state management approach
- TanStack Query for server state (API data)
- Zustand for global client state (UI state, auth)
- URL state for shareable filters
- React Hook Form for form state
- Real-time state updates via SSE
- Best practices and anti-patterns

**Read this third** to understand how data flows through the app.

---

### 🔌 [04_API_INTEGRATION.md](./04_API_INTEGRATION.md)
**What**: Backend API integration patterns
**Covers**:
- Axios client configuration with interceptors
- API function structure for all services
  - Call Ingestion API
  - Analytics API
  - VoC API
  - Audit API
  - Sentiment API
- TypeScript types for API responses
- TanStack Query hooks for data fetching
- Real-time updates via SSE
- Error handling patterns
- Environment variable configuration

**Read this fourth** to understand how to integrate with the backend.

---

### 🎨 [05_DESIGN_SYSTEM.md](./05_DESIGN_SYSTEM.md)
**What**: UI/UX design guidelines
**Covers**:
- Design philosophy (modern, professional, data-dense)
- Color palette (primary, accent, semantic, sentiment colors)
- Typography (Inter, JetBrains Mono, type scale)
- Spacing system (4px-based)
- Component patterns (cards, badges, tables, loading states)
- Data visualization (chart colors, Recharts examples)
- Layout patterns (dashboard grids, sidebar layout)
- Iconography (Lucide React)
- Accessibility guidelines (WCAG 2.1 AA)
- Responsive design (mobile-first)
- Animation & transitions

**Read this fifth** to understand the design language.

---

### 🗺️ [06_IMPLEMENTATION_ROADMAP.md](./06_IMPLEMENTATION_ROADMAP.md)
**What**: Phased implementation plan
**Covers**:
- **Phase 1** (Weeks 1-3): Foundation, auth, call management
- **Phase 2** (Weeks 4-5): Transcription & sentiment visualization
- **Phase 3** (Weeks 6-8): VoC insights & analytics dashboards
- **Phase 4** (Weeks 9-10): Compliance, real-time updates, polish
- Testing strategy (unit, integration, E2E)
- Deployment options (Vercel vs Docker)
- Success metrics and risk mitigation
- Post-launch roadmap (Phase 5+)

**Read this last** to understand the implementation timeline.

---

### 🎯 [07_UI_EXPERT_RECOMMENDATIONS.md](./07_UI_EXPERT_RECOMMENDATIONS.md)
**What**: UI/UX expert review and enhancement recommendations
**Covers**:
- Comprehensive review verdict (9.5/10 - Production-Ready)
- Critical recommendations (virtual scrolling, focus-visible, Sentry)
- Important recommendations (ARIA live regions, SSE heartbeat, optimistic UI)
- Enhancement recommendations (Storybook, visual regression, env validation)
- Implementation checklist and priority levels
- Files requiring updates based on recommendations

**Read this** after the main documents to understand the expert-recommended enhancements.

---

## Quick Reference

### Technology Stack Summary

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Next.js | 15.x |
| Language | TypeScript | 5.x |
| UI Components | Shadcn/ui + Radix | Latest |
| Styling | Tailwind CSS | 4.x |
| Global State | Zustand | 5.x |
| Server State | TanStack Query | 5.x |
| Real-Time | SSE (EventSource) | Native |
| Charts | Recharts | 2.x |
| Forms | React Hook Form + Zod | 7.x + 3.x |
| HTTP Client | Axios | 1.x |
| Auth | NextAuth.js | 5.x |
| Testing | Vitest + Playwright | 2.x + 1.x |
| Linting | ESLint + Prettier | 9.x + 3.x |

---

## Key Features

### Phase 1.1: Foundation & Authentication (COMPLETED ✅)
- ✅ Project setup (Next.js 15, TypeScript 5.7, Tailwind CSS 3.x)
- ✅ Shadcn/ui component library (7 components installed)
- ✅ Authentication system (login/logout, protected routes)
- ✅ Dashboard layout (sidebar navigation, header)
- ✅ State management (TanStack Query + Zustand)
- ✅ API integration layer (Axios client + hooks)
- ✅ Toast notifications
- ✅ TypeScript type definitions
- ✅ Production build verified

### Phase 1.2: Call Management (IN PROGRESS 🚧)
- 🚧 Audio file upload with drag-and-drop
- 🚧 Call list with search and filters
- 🚧 Call detail view with audio player
- 🚧 Real-time status updates (SSE)

### Phase 2: Analysis Features (PLANNED 📋)
- 📋 Transcription viewer (speaker-segmented)
- 📋 Sentiment analysis visualization
- 📋 Emotion detection and escalation alerts

### Phase 3: Insights & Analytics (PLANNED 📋)
- 📋 Voice of Customer insights dashboard
- 📋 Trending themes and topics
- 📋 Actionable insights prioritization
- 📋 Analytics dashboard with KPIs
- 📋 Agent performance tracking

### Phase 4: Compliance & Polish (PLANNED 📋)
- 📋 Compliance audit results
- 📋 Violations tracking
- 📋 Audit rules configuration
- 📋 Real-time notifications
- 📋 User settings and preferences
- 📋 Performance optimization

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Next.js 15 Frontend (Port 3000)               │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ App Router                                                   ││
│  │  ├── (auth) - Login                                          ││
│  │  ├── (dashboard)                                             ││
│  │  │   ├── /dashboard - Overview                              ││
│  │  │   ├── /calls - Call management                           ││
│  │  │   ├── /analytics - Dashboards & KPIs                     ││
│  │  │   ├── /voc - Voice of Customer insights                  ││
│  │  │   ├── /audit - Compliance & auditing                     ││
│  │  │   └── /settings - User preferences                       ││
│  │  └── /api                                                    ││
│  │      ├── /auth/[...nextauth] - NextAuth.js                  ││
│  │      └── /events/stream - SSE for real-time updates         ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  State Management:                                              │
│  ├── Zustand (auth, UI state)                                   │
│  ├── TanStack Query (API data, caching)                         │
│  └── URL params (filters, pagination)                           │
│                                                                  │
│  API Communication:                                              │
│  └── Axios → API Gateway (localhost:8080)                       │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼ HTTP/SSE
┌─────────────────────────────────────────────────────────────────┐
│           Spring Cloud API Gateway (Port 8080)                   │
│  Routes to:                                                      │
│  ├── Call Ingestion (8081) - Upload, status                     │
│  ├── Transcription (8082) - Speech-to-text                      │
│  ├── Sentiment (8083) - Emotion analysis                        │
│  ├── VoC (8084) - Insights extraction                           │
│  ├── Audit (8085) - Compliance checking                         │
│  ├── Analytics (8086) - Metrics & KPIs                          │
│  ├── Notification (8087) - Alerts                               │
│  └── Monitor (8088) - Kafka event streaming                     │
└─────────────────────────────────────────────────────────────────┘
                       │
                       ▼ Kafka Events (via Monitor Service)
┌─────────────────────────────────────────────────────────────────┐
│                  Kafka Event Stream (KRaft)                      │
│  Topics:                                                         │
│  ├── calls.received                                             │
│  ├── calls.transcribed                                          │
│  ├── calls.sentiment-analyzed                                   │
│  ├── calls.voc-analyzed                                         │
│  └── calls.audited                                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Getting Started

### Prerequisites
- Node.js 22.x LTS
- pnpm 10.x (package manager)
- Backend services running (API Gateway on port 8080)

### Project Initialization

```bash
# Create Next.js 15 app
npx create-next-app@latest call-auditing-ui \
  --typescript \
  --tailwind \
  --app \
  --src-dir \
  --import-alias "@/*"

cd call-auditing-ui

# Install dependencies
pnpm add zustand @tanstack/react-query axios react-hook-form zod
pnpm add recharts react-dropzone next-auth
pnpm add -D vitest @testing-library/react happy-dom playwright

# Initialize Shadcn/ui
npx shadcn@latest init

# Install base components
npx shadcn@latest add button card dialog table badge input
```

### Environment Setup

```bash
# .env.local
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=your-secret-key-here
```

### Run Development Server

```bash
pnpm dev
# Open http://localhost:3000
```

---

## Project Structure (Recommended)

```
call-auditing-ui/
├── public/
├── src/
│   ├── app/                        # Next.js 15 App Router
│   │   ├── (auth)/login/          # Authentication pages
│   │   ├── (dashboard)/           # Main application
│   │   │   ├── dashboard/
│   │   │   ├── calls/
│   │   │   ├── analytics/
│   │   │   ├── voc/
│   │   │   ├── audit/
│   │   │   └── settings/
│   │   └── api/                   # API routes
│   │       ├── auth/[...nextauth]/
│   │       └── events/stream/
│   │
│   ├── components/                # React components
│   │   ├── ui/                    # Shadcn/ui components
│   │   ├── layout/                # Layout components
│   │   ├── calls/                 # Call-specific
│   │   ├── analytics/             # Analytics
│   │   ├── voc/                   # VoC
│   │   ├── charts/                # Charts
│   │   └── common/                # Shared
│   │
│   ├── lib/                       # Utilities
│   │   ├── api/                   # API clients
│   │   ├── hooks/                 # Custom hooks
│   │   ├── stores/                # Zustand stores
│   │   ├── utils/                 # Helper functions
│   │   └── schemas/               # Zod schemas
│   │
│   └── types/                     # TypeScript types
│
├── tests/
│   ├── unit/
│   ├── integration/
│   └── e2e/
│
├── .env.local
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── package.json
```

---

## Development Workflow

### 1. Design Phase
- Review design system (`05_DESIGN_SYSTEM.md`)
- Create wireframes/mockups (Figma recommended)
- Get user feedback on designs

### 2. Implementation Phase
- Follow roadmap (`06_IMPLEMENTATION_ROADMAP.md`)
- Start with Phase 1 (foundation)
- Implement features iteratively
- Write tests alongside features

### 3. Testing Phase
- Unit tests for utilities and hooks
- Integration tests for components
- E2E tests for critical user flows
- Accessibility audits

### 4. Deployment Phase
- Deploy to Vercel (staging)
- User acceptance testing (UAT)
- Performance optimization
- Production deployment

---

## Key Design Decisions

### Why Next.js 15 over Vite/CRA?
- Server-side rendering (SSR) for better performance
- Built-in API routes for SSE endpoint
- Server components reduce client JS bundle
- Production-ready with excellent DX

### Why Shadcn/ui over Material UI?
- Copy-paste components (no package lock-in)
- Built on Radix UI (accessible primitives)
- Tailwind-native (consistent styling)
- Lightweight and customizable

### Why Zustand over Redux?
- Simpler API, less boilerplate
- Better TypeScript inference
- Smaller bundle size (~1KB vs 10KB+)
- Sufficient for this app's state needs

### Why TanStack Query over custom state?
- Built-in caching, background refetching
- Optimistic updates, automatic retries
- Eliminates manual cache invalidation
- Industry standard for server state

### Why SSE over WebSockets?
- Simpler (unidirectional server→client)
- Works over HTTP/2 (easier deployment)
- Auto-reconnect built-in
- Sufficient for Kafka event streaming

---

## Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Lighthouse Performance | 90+ | Measured on production build |
| Lighthouse Accessibility | 90+ | WCAG 2.1 AA compliance |
| First Contentful Paint | < 1.5s | Critical user perception |
| Time to Interactive | < 3s | Fully interactive state |
| Bundle Size (gzipped) | < 300KB | Initial JS bundle |
| API Response Time | < 1s | p95 for all endpoints |

---

## Accessibility Requirements

✅ **WCAG 2.1 Level AA Compliance**:
- Color contrast: 4.5:1 for normal text, 3:1 for large text
- Keyboard navigation: All interactive elements accessible
- Screen reader support: Semantic HTML, ARIA labels
- Focus indicators: Visible focus states on all focusable elements
- Alt text: All images have descriptive alt text

**Tools**:
- Radix UI (accessible primitives)
- React Aria (accessibility hooks)
- eslint-plugin-jsx-a11y (linting)
- Axe DevTools (testing)

---

## Security Considerations

- **Authentication**: NextAuth.js with JWT tokens
- **Authorization**: Role-based access control (RBAC)
- **CSRF Protection**: Built into NextAuth.js
- **XSS Prevention**: React escapes by default, CSP headers
- **API Security**: Bearer tokens, HTTPS in production
- **Data Validation**: Zod schemas for all user inputs

---

## Support & Maintenance

### Documentation Updates
This planning documentation should be updated when:
- New features are added
- Technology stack changes
- Architecture evolves
- Design system expands

### Version Control
- Use semantic versioning (e.g., v1.0.0)
- Maintain CHANGELOG.md
- Tag releases in Git

### Future Enhancements
See Phase 5+ in `06_IMPLEMENTATION_ROADMAP.md` for planned features:
- Advanced search with Elasticsearch
- Custom dashboards
- Mobile app (React Native)
- AI chat assistant
- Team collaboration features

---

## Contact & Feedback

For questions or suggestions about this UI planning documentation:
- **Project Lead**: [Your Name]
- **Email**: [your.email@company.com]
- **Repository**: [GitHub URL]
- **Slack**: [#call-auditing-ui]

---

## License

[Your License Here]

---

**Document Metadata**:
- **Created**: 2025-12-31
- **Version**: 1.0.0
- **Authors**: Planning Team
- **Review Status**: ✅ Complete
- **Next Review**: Before Phase 1 implementation start

---

## Appendix: Useful Links

### Official Documentation
- [Next.js 15 Docs](https://nextjs.org/docs)
- [React 19 Docs](https://react.dev/)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)
- [Tailwind CSS](https://tailwindcss.com/docs)
- [Shadcn/ui](https://ui.shadcn.com/)
- [TanStack Query](https://tanstack.com/query)
- [Zustand](https://zustand-demo.pmnd.rs/)

### Tools & Resources
- [Can I Use](https://caniuse.com/) - Browser compatibility
- [Contrast Checker](https://webaim.org/resources/contrastchecker/) - Accessibility
- [Lighthouse](https://developers.google.com/web/tools/lighthouse) - Performance
- [TypeScript Playground](https://www.typescriptlang.org/play) - Test TS code

---

**End of UI Planning Documentation**

Ready to start implementation? Begin with Phase 1 in `06_IMPLEMENTATION_ROADMAP.md`! 🚀
