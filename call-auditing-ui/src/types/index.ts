// Re-export common types
export type { User } from '@/lib/stores/auth-store';

// Call-related types
export interface Call {
  id: string;
  fileName: string;
  uploadedAt: string;
  duration: number;
  channel: 'PHONE' | 'VIDEO' | 'CHAT';
  status: 'UPLOADED' | 'TRANSCRIBING' | 'COMPLETED' | 'FAILED';
  agentId?: string;
  customerId?: string;
  metadata?: CallMetadata;
}

export interface CallMetadata {
  callDate?: string;
  agentName?: string;
  customerName?: string;
  department?: string;
  tags?: string[];
}

// Transcription types
export interface Transcription {
  id: string;
  callId: string;
  text: string;
  language: string;
  confidence: number;
  segments: TranscriptionSegment[];
  createdAt: string;
}

export interface TranscriptionSegment {
  id: string;
  text: string;
  startTime: number;
  endTime: number;
  speaker: 'AGENT' | 'CUSTOMER' | 'UNKNOWN';
  confidence: number;
}

// Sentiment types
export interface SentimentAnalysis {
  id: string;
  callId: string;
  overallSentiment: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';
  sentimentScore: number; // -1 to 1
  segments: SentimentSegment[];
  emotions: EmotionScore[];
  createdAt: string;
}

export interface SentimentSegment {
  startTime: number;
  endTime: number;
  sentiment: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';
  score: number;
  text: string;
}

export interface EmotionScore {
  emotion: string;
  score: number; // 0 to 1
}

// VoC (Voice of Customer) types
export interface VoCInsight {
  id: string;
  callId: string;
  themes: Theme[];
  keywords: Keyword[];
  actionItems: string[];
  customerPainPoints: string[];
  opportunities: string[];
  createdAt: string;
}

export interface Theme {
  name: string;
  confidence: number;
  mentions: number;
}

export interface Keyword {
  word: string;
  frequency: number;
  relevance: number;
}

// Audit/Compliance types
export interface AuditResult {
  id: string;
  callId: string;
  status: 'PASSED' | 'FAILED' | 'NEEDS_REVIEW';
  score: number; // 0 to 100
  violations: ComplianceViolation[];
  checkedItems: ComplianceCheck[];
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt: string;
}

export interface ComplianceViolation {
  rule: string;
  description: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  timestamp: number;
  context: string;
}

export interface ComplianceCheck {
  rule: string;
  passed: boolean;
  description: string;
}

// Analytics types
export interface CallAnalytics {
  totalCalls: number;
  averageDuration: number;
  sentimentDistribution: {
    positive: number;
    neutral: number;
    negative: number;
  };
  complianceRate: number;
  topThemes: Theme[];
  callVolumeByHour: { hour: number; count: number }[];
  callVolumeByDay: { date: string; count: number }[];
}

// API Response types
export interface ApiResponse<T> {
  data: T;
  message?: string;
  timestamp: string;
}

export interface PaginatedResponse<T> {
  data: T[];
  pagination: {
    page: number;
    size: number;
    total: number;
    totalPages: number;
  };
}

// Error types
export interface ApiError {
  message: string;
  code?: string;
  details?: Record<string, unknown>;
}
