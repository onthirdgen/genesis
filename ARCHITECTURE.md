# Call Auditing Application with Voice of the Customer - Architecture Design

## Executive Summary

This document outlines the architecture for a **prototype** call auditing application with Voice of the Customer (VoC) capabilities, built using microservices and event sourcing patterns. **This architecture uses only free and open-source technologies** to minimize costs during the prototype phase. The system processes phone conversations, extracts insights, and provides comprehensive audit trails for compliance and quality assurance.

## Architecture Overview

### Core Principles

- **Microservices**: Independent, loosely-coupled services with single responsibilities
- **Event Sourcing**: Immutable event log as the source of truth
- **CQRS**: Separate read and write models for optimized performance
- **Asynchronous Processing**: Event-driven communication between services
- **Scalability**: Horizontal scaling for high-volume call processing

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          API Gateway / BFF                          │
│                    (Authentication, Rate Limiting)                  │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
         ┌────────────────────┼───────────────────┐
         │                    │                   │
    ┌────▼─────┐        ┌─────▼──────┐      ┌─────▼──────┐
    │  Call    │        │   Audit    │      │    VoC     │
    │ Ingestion│        │  Service   │      │  Service   │
    │ Service  │        │            │      │            │
    └────┬─────┘        └─────┬──────┘      └─────┬──────┘
         │                    │                   │
         │                    │                   │
         └────────────────────┼───────────────────┘
                              │
                      ┌───────▼────────┐
                      │  Event Store   │
                      │   (Kafka)      │
                      └───────┬────────┘
                              │
         ┌────────────────────┼──────────────────┐
         │                    │                  │
    ┌────▼────────┐     ┌─────▼──────┐     ┌─────▼──────┐
    │Transcription│     │ Analytics  │     │ Sentiment  │
    │  Service    │     │  Service   │     │  Service   │
    └────┬────────┘     └─────┬──────┘     └─────┬──────┘
         │                    │                  │
         └────────────────────┼──────────────────┘
                              │
                       ┌──────▼───────┐
                       │  Query Store │
                       │ (PostgreSQL/ │
                       │  OpenSearch) │
                       └──────────────┘
```

### Event Flow

```
User uploads audio
  │
  ▼
┌─────────────────────────┐
│  Call Ingestion Service │ ──► Kafka: calls.received
│  (Port 8081)            │
└─────────────────────────┘
            │
            ▼
┌─────────────────────────┐
│  Transcription Service  │ ──► Kafka: calls.transcribed
│  (Port 8082)            │
└─────────────────────────┘
            │
            ▼ (parallel processing)
     ┌──────┴──────┬────────────────┐
     │             │                │
     ▼             ▼                ▼
┌─────────┐  ┌──────────┐  ┌─────────────┐
│Sentiment│  │   VoC    │  │   Audit     │
│ Service │  │ Service  │  │  Service    │
│ (8083)  │  │ (8084)   │  │  (8085)     │
└────┬────┘  └────┬─────┘  └──────┬──────┘
     │            │               │
     ▼            ▼               ▼
calls.sentiment  calls.voc     calls.audited
  -analyzed      -analyzed
     │            │               │
     └────────────┼───────────────┘
                  ▼
        ┌─────────────────┐
        │Analytics Service│
        │    (8086)       │
        └─────────────────┘
```

## Frontend Architecture

### Technology Stack

The frontend is built with modern React technologies:

- **Next.js 15** - React framework with App Router
- **React 19** - Latest React with improved server components
- **TypeScript 5.7+** - Strict mode for type safety
- **Tailwind CSS 3.x** - Utility-first styling
- **Shadcn/ui** - Accessible component library built on Radix UI

### State Management

- **TanStack Query (React Query)** - Server state management, caching, and data fetching
- **Zustand** - Global client state (authentication, UI preferences)
- **React Hook Form + Zod** - Form handling and validation

### Key Features

1. **Authentication & Authorization**
   - Login page with form validation
   - Protected routes with auto-redirect
   - Session persistence via localStorage
   - Ready for backend JWT integration

2. **Dashboard Layout**
   - Responsive sidebar navigation
   - Header with search and notifications
   - Protected route wrapper
   - Stats overview with real-time updates

3. **Component Library**
   - 7+ pre-built UI components (Button, Card, Input, Label, Dialog, Toast)
   - Consistent design system
   - Dark mode support
   - Full accessibility (WCAG 2.1)

4. **API Integration**
   - Type-safe API client (Axios)
   - Automatic request/response interceptors
   - Error handling and retry logic
   - Optimistic UI updates

### File Structure

```
call-auditing-ui/
├── src/
│   ├── app/              # Next.js App Router pages
│   │   ├── page.tsx      # Home page
│   │   ├── login/        # Login page
│   │   └── dashboard/    # Protected dashboard
│   ├── components/
│   │   ├── ui/           # Shadcn/ui components
│   │   └── layout/       # Layout components (Sidebar, Header)
│   ├── lib/
│   │   ├── api/          # API client configuration
│   │   ├── hooks/        # Custom React hooks
│   │   └── stores/       # Zustand stores
│   └── types/            # TypeScript type definitions
├── components.json       # Shadcn/ui configuration
└── tailwind.config.ts    # Tailwind CSS configuration
```

### Routes

| Route | Type | Description |
|-------|------|-------------|
| `/` | Public | Marketing home page |
| `/login` | Public | Authentication page |
| `/dashboard` | Protected | Dashboard overview |
| `/dashboard/calls` | Protected | Call list and upload |
| `/dashboard/analytics` | Protected | Analytics dashboards |
| `/dashboard/voc` | Protected | Voice of Customer insights |
| `/dashboard/compliance` | Protected | Compliance reports |
| `/dashboard/settings` | Protected | User settings |

### API Communication

The frontend communicates with backend services through the API Gateway (port 8080):

```
Frontend (Next.js :3000)
    │
    ├─► /api/calls → API Gateway → Call Ingestion Service
    ├─► /api/analytics → API Gateway → Analytics Service
    ├─► /api/voc → API Gateway → VoC Service
    └─► /api/audit → API Gateway → Audit Service
```

### Real-Time Updates

- **Server-Sent Events (SSE)** - Planned for call processing status updates
- **Polling with TanStack Query** - Automatic refetch for dashboard stats
- **WebSockets** - Future consideration for live transcription display

### Kafka Topics

| Topic | Partitions | Producer | Consumers |
|-------|------------|----------|-----------|
| `calls.received` | 3 | Call Ingestion | Transcription, Analytics |
| `calls.transcribed` | 3 | Transcription | Sentiment, VoC, Audit, Analytics |
| `calls.sentiment-analyzed` | 3 | Sentiment | VoC, Audit, Notification, Analytics |
| `calls.voc-analyzed` | 3 | VoC | Audit, Notification, Analytics |
| `calls.audited` | 3 | Audit | Notification, Analytics |

## Database Schema

Located at: `/schema.sql`

**Migration Plan**: See `.private/database-schema-migration-plan.md` for detailed schema separation strategy.

### Schema Organization (Phase 1 - Planned)

The database will be organized into service-specific schemas with **NO cross-schema foreign key constraints**. This design ensures minimal changes when migrating to fully separated databases in the future.

**Architectural Decision**: Referential integrity is enforced at the application level and through event-driven patterns (Kafka events), NOT through database foreign keys across schemas.

| Schema | Owner Service | Tables | Purpose |
|--------|---------------|--------|---------|
| `ingestion` | call-ingestion-service | calls | Audio upload, call records |
| `transcription` | transcription-service | transcriptions, segments | Speech-to-text processing |
| `sentiment` | sentiment-service | sentiment_results, segment_sentiments | Sentiment analysis |
| `voc` | voc-service | voc_insights | Customer insights extraction |
| `audit` | audit-service | audit_results, compliance_violations, compliance_rules | Compliance auditing |
| `analytics` | analytics-service | agent_performance, compliance_metrics, sentiment_trends | Time-series metrics (TimescaleDB hypertables) |
| `notifications` | notification-service | notifications | Alert delivery |
| `gateway` | api-gateway | users | Authentication |
| `shared` | Platform team | event_store | Event sourcing audit trail |

### Core Tables (Current - Public Schema)

| Table | Purpose |
|-------|---------|
| `calls` | Call metadata |
| `transcriptions` | Full transcription text |
| `segments` | Speaker-separated segments |
| `sentiment_results` | Overall sentiment per call |
| `segment_sentiments` | Sentiment per segment |
| `voc_insights` | VoC extracted insights |
| `audit_results` | Compliance audit results |
| `compliance_violations` | Specific violations |
| `compliance_rules` | Configurable rules |
| `notifications` | Alert history |
| `event_store` | Event sourcing audit trail |
| `users` | Authentication |

### TimescaleDB Hypertables (Time-Series)

| Table | Purpose |
|-------|---------|
| `agent_performance` | Agent metrics over time |
| `compliance_metrics` | Daily compliance rates |
| `sentiment_trends` | Sentiment trends |

### Data Integrity Patterns

**Primary Method: Event-Driven Consistency**
- Services consume Kafka events containing valid IDs
- Event sourcing guarantees consistency through immutable event log
- UUIDs used as references between schemas (no FK constraints)

**Secondary Method: Application-Level Validation**
- Services validate references before persisting data
- REST API calls to other services when needed (rare)
- Idempotency ensures safe retries

**Safety Net: Orphaned Data Detection** (Future)
- Scheduled background jobs to detect inconsistencies
- Monitoring and alerting via Prometheus/Grafana
- Manual or automated cleanup procedures
- See TODO.md "Data Quality & Consistency" section

### Future Considerations

**Event Store Manager Service** (Planned)
- Dedicated service to manage `shared.event_store` persistence
- Listens to all Kafka topics and persists events
- Removes direct event_store writes from application services
- Enables migration to specialized event store database (EventStoreDB)
- See database migration plan Section 9.1 for details

**Database Separation Phases**
- **Phase 1**: Schema-per-service (shared PostgreSQL instance)
- **Phase 2**: Split high-traffic services to dedicated databases
- **Phase 3**: Full database-per-service with polyglot persistence

## Service URLs

### Frontend & User Interfaces

| Service | URL | Credentials |
|---------|-----|-------------|
| **Call Auditing UI** | http://localhost:3000 | Demo: any email + 6+ char password |
| Grafana | http://localhost:3000 | admin/admin |
| Jaeger | http://localhost:16686 | - |
| Prometheus | http://localhost:9090 | - |
| OpenSearch Dashboards | http://localhost:5601 | - |
| MinIO Console | http://localhost:9001 | minioadmin/minioadmin |

### Backend Services

| Service | Port | Health Check |
|---------|------|--------------|
| API Gateway | 8080 | http://localhost:8080/actuator/health |
| Call Ingestion | 8081 | http://localhost:8081/actuator/health |
| Transcription | 8082 | http://localhost:8082/health |
| Sentiment | 8083 | http://localhost:8083/health |
| VoC Service | 8084 | http://localhost:8084/actuator/health |
| Audit Service | 8085 | http://localhost:8085/actuator/health |
| Analytics | 8086 | http://localhost:8086/actuator/health |
| Notification | 8087 | http://localhost:8087/actuator/health |
| Monitor | 8088 | http://localhost:8088/actuator/health |
