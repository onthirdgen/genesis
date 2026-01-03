# Implementation Roadmap - Call Auditing Platform UI

**Status**: Planning Phase
**Last Updated**: 2025-12-31
**Timeline**: Phased approach (4 phases)

---

## Overview

This roadmap breaks down the UI implementation into **4 manageable phases**, allowing for iterative development, testing, and user feedback. Each phase builds on the previous one and delivers tangible user value.

---

## Phase 1: Foundation & Call Management (Weeks 1-3)

**Goal**: Set up project infrastructure and implement core call management features.

### 1.1 Project Setup (Week 1)

#### Tasks
- [ ] Initialize Next.js 15 project with TypeScript
- [ ] Configure Tailwind CSS 4
- [ ] Install and configure Shadcn/ui base components
- [ ] Set up folder structure (as per `02_ARCHITECTURE_OVERVIEW.md`)
- [ ] Configure ESLint, Prettier, Husky
- [ ] Set up TanStack Query and Zustand
- [ ] Create Axios API client with interceptors
- [ ] Configure environment variables

#### Deliverables
```bash
call-auditing-ui/
├── src/
│   ├── app/
│   ├── components/
│   ├── lib/
│   ├── types/
│   └── styles/
├── .env.local
├── tailwind.config.ts
├── next.config.ts
└── package.json
```

#### Acceptance Criteria
- ✅ Next.js app runs on `localhost:3000`
- ✅ Tailwind CSS styles applied correctly
- ✅ TypeScript strict mode enabled, no errors
- ✅ All linters configured and passing

---

### 1.2 Authentication (Week 1)

#### Tasks
- [ ] Install and configure NextAuth.js v5
- [ ] Create login page (`/login`)
- [ ] Implement credentials provider (email/password)
- [ ] Create auth middleware for protected routes
- [ ] Build auth store (Zustand) for client-side state
- [ ] Create logout functionality

#### Components
```typescript
// app/(auth)/login/page.tsx
export default function LoginPage() {
  return <LoginForm />;
}

// components/auth/LoginForm.tsx
// components/layout/Header.tsx (with user menu)
```

#### Acceptance Criteria
- ✅ Users can log in with email/password
- ✅ Protected routes redirect to `/login` if not authenticated
- ✅ JWT token stored securely
- ✅ Logout clears session and redirects to login

---

### 1.3 Layout & Navigation (Week 1-2)

#### Tasks
- [ ] Create dashboard layout with sidebar
- [ ] Build navigation menu with route highlighting
- [ ] Implement header with user profile dropdown
- [ ] Add breadcrumb navigation
- [ ] Create responsive mobile drawer (Sheet component)
- [ ] Implement dark mode toggle

#### Components
```typescript
// components/layout/Sidebar.tsx
// components/layout/Header.tsx
// components/layout/BreadcrumbNav.tsx
// components/layout/MobileNav.tsx
```

#### Routes
- `/dashboard` - Main dashboard
- `/calls` - Call list
- `/analytics` - Analytics overview
- `/voc` - Voice of Customer
- `/audit` - Compliance & auditing
- `/settings` - User settings

#### Acceptance Criteria
- ✅ Sidebar shows on desktop, drawer on mobile
- ✅ Active route highlighted in navigation
- ✅ Dark mode toggle works correctly
- ✅ Breadcrumbs update based on current route

---

### 1.4 Call Upload Feature (Week 2)

#### Tasks
- [ ] Create upload page (`/calls/upload`)
- [ ] Implement drag-and-drop file upload (React Dropzone)
- [ ] Build upload form with React Hook Form + Zod validation
- [ ] Add file validation (type, size)
- [ ] Implement progress bar for upload
- [ ] Show success/error notifications
- [ ] Redirect to call details on successful upload

#### Components
```typescript
// app/(dashboard)/calls/upload/page.tsx
// components/calls/UploadDropzone.tsx
// components/calls/UploadForm.tsx
// components/common/NotificationToast.tsx
```

#### API Integration
```typescript
// lib/api/calls.ts
export const callsApi = {
  uploadAudioFile: async (formData, onProgress) => { /* ... */ },
};

// lib/hooks/useCalls.ts
export function useUploadCall() { /* ... */ }
```

#### Acceptance Criteria
- ✅ Users can drag and drop `.wav`, `.mp3`, `.m4a` files
- ✅ File validation shows clear error messages
- ✅ Upload progress displays in real-time
- ✅ Success notification shown, redirects to call details
- ✅ Error handling for failed uploads

---

### 1.5 Call List Page (Week 2-3)

#### Tasks
- [ ] Create call list page (`/calls`)
- [ ] Build call table component with sorting
- [ ] Add search functionality
- [ ] Implement filters (status, agent, date range)
- [ ] Add pagination
- [ ] Show call status badges
- [ ] Link to call detail page

#### Components
```typescript
// app/(dashboard)/calls/page.tsx
// components/calls/CallTable.tsx
// components/calls/CallStatusBadge.tsx
// components/common/SearchBar.tsx
// components/common/FilterPanel.tsx
// components/common/Pagination.tsx
```

#### API Integration
```typescript
// lib/hooks/useCalls.ts
export function useCallsList(filters) {
  return useQuery({
    queryKey: ['calls', filters],
    queryFn: () => callsApi.getCallsList(filters),
  });
}
```

#### Acceptance Criteria
- ✅ Call list displays with all metadata
- ✅ Search filters calls by caller ID, agent ID
- ✅ Filters update URL parameters (shareable)
- ✅ Pagination works correctly
- ✅ Status badges show correct colors
- ✅ Clicking a row navigates to call details

---

### 1.6 Call Detail Page (Week 3)

#### Tasks
- [ ] Create call detail page (`/calls/[callId]`)
- [ ] Display call metadata (caller, agent, duration, timestamp)
- [ ] Show call status with real-time updates
- [ ] Add audio player component
- [ ] Show loading state while fetching data
- [ ] Handle error states (call not found)

#### Components
```typescript
// app/(dashboard)/calls/[callId]/page.tsx
// components/calls/CallDetailCard.tsx
// components/calls/AudioPlayer.tsx
```

#### API Integration
```typescript
// lib/hooks/useCalls.ts
export function useCallDetails(callId) {
  return useQuery({
    queryKey: ['calls', callId],
    queryFn: () => callsApi.getCallDetails(callId),
  });
}
```

#### Acceptance Criteria
- ✅ Call metadata displayed correctly
- ✅ Audio player can play/pause audio
- ✅ Status updates in real-time (SSE)
- ✅ Loading skeleton shown while fetching
- ✅ 404 page for invalid call IDs

---

### Phase 1 Deliverables

**User Stories Completed**:
1. ✅ As a user, I can log in to the application
2. ✅ As a user, I can upload audio files for processing
3. ✅ As a user, I can view a list of all uploaded calls
4. ✅ As a user, I can search and filter calls
5. ✅ As a user, I can view details of a specific call

**Technical Milestones**:
- ✅ Project infrastructure complete
- ✅ Authentication working
- ✅ Layout and navigation functional
- ✅ Core call management features implemented
- ✅ API integration with backend tested

---

## Phase 2: Transcription & Sentiment Analysis (Weeks 4-5)

**Goal**: Display transcription data and sentiment analysis results.

### 2.1 Transcription Viewer (Week 4)

#### Tasks
- [ ] Create transcription tab/page (`/calls/[callId]/transcription`)
- [ ] Build transcription viewer component
- [ ] Display speaker-segmented transcription
- [ ] Add timestamp alignment with audio player
- [ ] Implement search within transcription
- [ ] Show confidence scores

#### Components
```typescript
// app/(dashboard)/calls/[callId]/transcription/page.tsx
// components/calls/TranscriptionViewer.tsx
// components/calls/TranscriptionSegment.tsx
```

#### Features
- Speaker labels (Agent/Customer)
- Clickable timestamps to jump to audio position
- Highlighted search results
- Confidence indicator

#### Acceptance Criteria
- ✅ Transcription displayed with speaker labels
- ✅ Clicking timestamp seeks audio to that position
- ✅ Search highlights matching text
- ✅ Loading state while transcription is being generated

---

### 2.2 Sentiment Analysis Visualization (Week 4-5)

#### Tasks
- [ ] Create sentiment analysis page (`/calls/[callId]/sentiment`)
- [ ] Build sentiment timeline chart (Recharts)
- [ ] Display overall sentiment score
- [ ] Show sentiment by segment
- [ ] Add emotion tags
- [ ] Highlight escalation events

#### Components
```typescript
// app/(dashboard)/calls/[callId]/sentiment/page.tsx
// components/sentiment/SentimentTimeline.tsx
// components/sentiment/SentimentGauge.tsx
// components/sentiment/EmotionTags.tsx
```

#### API Integration
```typescript
// lib/hooks/useSentiment.ts
export function useSentiment(callId) {
  return useQuery({
    queryKey: ['sentiment', callId],
    queryFn: () => sentimentApi.getSentiment(callId),
  });
}
```

#### Acceptance Criteria
- ✅ Sentiment timeline shows score over time
- ✅ Overall sentiment score displayed prominently
- ✅ Escalation events highlighted
- ✅ Emotion tags shown per segment

---

### Phase 2 Deliverables

**User Stories Completed**:
1. ✅ As a user, I can view transcriptions of calls
2. ✅ As a user, I can see sentiment analysis results
3. ✅ As a user, I can identify emotional escalations

---

## Phase 3: VoC Insights & Analytics Dashboard (Weeks 6-8)

**Goal**: Implement Voice of Customer insights and analytics features.

### 3.1 VoC Insights Page (Week 6)

#### Tasks
- [ ] Create VoC insights page (`/voc`)
- [ ] Build trending themes visualization
- [ ] Display actionable insights list
- [ ] Show customer journey maps
- [ ] Implement theme filtering
- [ ] Add sentiment trends chart

#### Components
```typescript
// app/(dashboard)/voc/page.tsx
// components/voc/TrendingThemes.tsx
// components/voc/ActionableInsights.tsx
// components/voc/CustomerJourney.tsx
// components/voc/ThemeCloud.tsx
```

#### Acceptance Criteria
- ✅ Trending themes displayed as word cloud
- ✅ Actionable insights sorted by priority
- ✅ Filters update in real-time
- ✅ Customer journey timeline visible

---

### 3.2 Analytics Dashboard (Week 6-7)

#### Tasks
- [ ] Create analytics dashboard (`/analytics`)
- [ ] Build metric cards (total calls, avg sentiment, compliance rate)
- [ ] Add call volume trends chart
- [ ] Implement sentiment distribution pie chart
- [ ] Create agent performance table
- [ ] Add date range picker
- [ ] Enable data export (CSV)

#### Components
```typescript
// app/(dashboard)/analytics/page.tsx
// components/analytics/MetricCard.tsx
// components/charts/CallVolumeChart.tsx
// components/charts/SentimentPieChart.tsx
// components/analytics/AgentPerformanceTable.tsx
// components/common/DateRangePicker.tsx
```

#### API Integration
```typescript
// lib/hooks/useAnalytics.ts
export function useDashboard(dateRange) {
  return useQuery({
    queryKey: ['analytics', 'dashboard', dateRange],
    queryFn: () => analyticsApi.getDashboard({ dateRange }),
  });
}
```

#### Acceptance Criteria
- ✅ Metric cards show key KPIs
- ✅ Charts update based on date range
- ✅ Agent performance table sortable
- ✅ CSV export downloads data

---

### 3.3 Agent Performance Page (Week 7)

#### Tasks
- [ ] Create agent performance page (`/analytics/agents`)
- [ ] Display agent leaderboard
- [ ] Show individual agent detail view
- [ ] Add performance trends over time
- [ ] Implement comparison mode (compare 2+ agents)

#### Components
```typescript
// app/(dashboard)/analytics/agents/page.tsx
// components/analytics/AgentLeaderboard.tsx
// components/analytics/AgentDetailCard.tsx
```

#### Acceptance Criteria
- ✅ Agents ranked by performance metrics
- ✅ Individual agent details accessible
- ✅ Performance trends chart displayed

---

### Phase 3 Deliverables

**User Stories Completed**:
1. ✅ As a user, I can view VoC insights and trending themes
2. ✅ As a user, I can see actionable items prioritized by impact
3. ✅ As a user, I can view analytics dashboards with key metrics
4. ✅ As a user, I can analyze agent performance

---

## Phase 4: Compliance, Real-Time Updates & Polish (Weeks 9-10)

**Goal**: Add compliance features, real-time updates, and polish the UI.

### 4.1 Compliance & Audit Pages (Week 9)

#### Tasks
- [ ] Create audit overview page (`/audit`)
- [ ] Build compliance summary dashboard
- [ ] Display violations list with severity
- [ ] Add audit reports generator
- [ ] Implement audit rules configuration page
- [ ] Show compliance trends over time

#### Components
```typescript
// app/(dashboard)/audit/page.tsx
// components/audit/ComplianceSummary.tsx
// components/audit/ViolationsList.tsx
// components/audit/AuditRulesConfig.tsx
```

#### Acceptance Criteria
- ✅ Compliance summary shows pass/fail rates
- ✅ Violations list filterable by severity
- ✅ Audit rules configurable via UI
- ✅ Reports downloadable as PDF

---

### 4.2 Real-Time Updates (Week 9)

#### Tasks
- [ ] Implement SSE endpoint in Next.js API routes
- [ ] Create real-time hook (`useRealtimeEvents`)
- [ ] Integrate with TanStack Query cache invalidation
- [ ] Show real-time notifications for new events
- [ ] Add live status updates on call detail page
- [ ] Implement reconnection logic

#### Implementation
```typescript
// app/api/events/stream/route.ts
export async function GET() {
  // SSE endpoint
}

// lib/hooks/useRealtime.ts
export function useRealtimeEvents() {
  // Subscribe to Kafka events via SSE
}
```

#### Acceptance Criteria
- ✅ Real-time events update UI without refresh
- ✅ Notifications shown for important events
- ✅ Call status updates live
- ✅ Reconnects automatically on connection loss

---

### 4.3 Settings & User Profile (Week 10)

#### Tasks
- [ ] Create settings page (`/settings`)
- [ ] Build user profile editor
- [ ] Add notification preferences
- [ ] Implement theme customization
- [ ] Add keyboard shortcuts reference

#### Components
```typescript
// app/(dashboard)/settings/page.tsx
// components/settings/ProfileForm.tsx
// components/settings/NotificationSettings.tsx
// components/settings/ThemeSelector.tsx
```

#### Acceptance Criteria
- ✅ User can update profile information
- ✅ Notification preferences saved
- ✅ Theme changes apply immediately

---

### 4.4 Polish & Optimization (Week 10)

#### Tasks
- [ ] Add loading skeletons to all pages
- [ ] Implement error boundaries
- [ ] Optimize bundle size (code splitting)
- [ ] Add accessibility improvements
- [ ] Write E2E tests (Playwright)
- [ ] Performance audit (Lighthouse)
- [ ] SEO optimization (meta tags, Open Graph)

#### Targets
- Lighthouse Score: 90+ (Performance, Accessibility, Best Practices)
- First Contentful Paint: < 1.5s
- Time to Interactive: < 3s
- Bundle size: < 300KB (gzipped)

#### Acceptance Criteria
- ✅ All pages have loading states
- ✅ Error boundaries catch component errors
- ✅ Lighthouse score meets targets
- ✅ E2E tests cover critical user flows

---

### Phase 4 Deliverables

**User Stories Completed**:
1. ✅ As a user, I can view compliance audit results
2. ✅ As a user, I receive real-time notifications for call updates
3. ✅ As a user, I can configure my profile and preferences
4. ✅ As a user, I experience a fast, accessible, polished UI

---

## Testing Strategy

### Unit Tests (Vitest)
- Utility functions
- Custom hooks
- Form validation schemas

### Integration Tests (React Testing Library)
- Component interactions
- API mock testing
- Form submissions

### E2E Tests (Playwright)
- Login flow
- Upload audio file
- View call details
- Search and filter calls
- View analytics dashboard

**Test Coverage Target**: 80%+

---

## Deployment Strategy

### Development Environment
- **URL**: `http://localhost:3000`
- **API**: `http://localhost:8080` (API Gateway)
- **Database**: Local PostgreSQL

### Staging Environment (Optional)
- **Hosting**: Vercel preview deployment
- **API**: Staging backend URL
- **Purpose**: User acceptance testing (UAT)

### Production Environment
**Option 1: Vercel (Recommended for quick deploy)**
- One-click deployment from GitHub
- Automatic HTTPS, CDN
- Edge functions for API routes
- Preview deployments for PRs

**Option 2: Docker + Self-Hosted**
```dockerfile
# Dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public
COPY --from=builder /app/package*.json ./
RUN npm ci --production
EXPOSE 3000
CMD ["npm", "start"]
```

Deploy with Docker Compose alongside backend services.

---

## Success Metrics

### User Adoption
- Daily active users (target: 80% of team)
- Average session duration (target: 15+ minutes)
- Feature usage rates (target: 70%+ use analytics)

### Performance
- Page load time < 2s (p95)
- Time to first byte < 500ms
- API response time < 1s (p95)

### Quality
- Bug reports < 5 per week
- Lighthouse score: 90+ across all categories
- User satisfaction: 4.5/5 or higher

---

## Risk Mitigation

### Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Backend API changes breaking UI | High | Medium | Use TypeScript types, integration tests |
| SSE connection stability issues | Medium | Medium | Implement fallback polling, retry logic |
| Large bundle size affecting performance | Medium | Low | Code splitting, lazy loading, tree shaking |
| Accessibility issues | Medium | Medium | Use Radix UI, regular a11y audits |

### Timeline Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Delayed backend API completion | High | Medium | Use mock data, stub endpoints |
| Scope creep from user requests | Medium | High | Prioritize ruthlessly, defer to Phase 5 |
| Insufficient testing time | Medium | Medium | Allocate 20% of each phase for testing |

---

## Post-Launch Roadmap (Phase 5+)

### Future Features
- **Advanced Search**: Elasticsearch-powered full-text search
- **Saved Filters**: Save frequently used filter combinations
- **Custom Dashboards**: User-configurable widget-based dashboards
- **Alerts & Webhooks**: Custom alerts for compliance violations
- **Mobile App**: React Native mobile companion app
- **AI Chat Assistant**: Ask questions about call insights in natural language
- **Bulk Operations**: Upload multiple files, batch export
- **Team Collaboration**: Comments, annotations on calls
- **Multi-Tenancy**: Support for multiple organizations

---

## Summary

This roadmap delivers a production-ready UI in **10 weeks** with 4 clear phases:

1. **Phase 1** (Weeks 1-3): Foundation, auth, call upload & list
2. **Phase 2** (Weeks 4-5): Transcription & sentiment visualization
3. **Phase 3** (Weeks 6-8): VoC insights & analytics dashboards
4. **Phase 4** (Weeks 9-10): Compliance, real-time updates, polish

Each phase delivers user value and can be deployed independently. The phased approach allows for:

✅ **Early User Feedback**: Test with real users after Phase 1
✅ **Iterative Development**: Refine based on feedback
✅ **Risk Reduction**: Identify issues early, adjust course
✅ **Team Velocity**: Clear milestones, manageable sprints

**Total Estimated Effort**: 400-500 hours (1 full-time developer for 10 weeks)

**Recommended Next Step**: Begin Phase 1 with project setup and authentication.
