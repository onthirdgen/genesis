-- Call Auditing Platform Database Schema
-- PostgreSQL 16 with TimescaleDB Extension

-- ============================================
-- CLEANUP: Drop existing objects (safe for re-runs)
-- ============================================

-- Drop views first (they depend on tables)
DROP VIEW IF EXISTS public.agent_summary CASCADE;
DROP VIEW IF EXISTS public.call_summary CASCADE;

-- Drop schemas (CASCADE drops all contained objects)
DROP SCHEMA IF EXISTS gateway CASCADE;
DROP SCHEMA IF EXISTS analytics CASCADE;
DROP SCHEMA IF EXISTS notification CASCADE;
DROP SCHEMA IF EXISTS audit CASCADE;
DROP SCHEMA IF EXISTS voc CASCADE;
DROP SCHEMA IF EXISTS sentiment CASCADE;
DROP SCHEMA IF EXISTS transcription CASCADE;
DROP SCHEMA IF EXISTS core CASCADE;

-- Drop custom types from public schema
DROP TYPE IF EXISTS intent_type CASCADE;
DROP TYPE IF EXISTS satisfaction_level CASCADE;
DROP TYPE IF EXISTS violation_severity CASCADE;
DROP TYPE IF EXISTS compliance_status CASCADE;
DROP TYPE IF EXISTS speaker_type CASCADE;
DROP TYPE IF EXISTS sentiment_type CASCADE;

-- ============================================
-- EXTENSIONS
-- ============================================

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- SCHEMAS: One schema per microservice
-- ============================================

CREATE SCHEMA IF NOT EXISTS core;           -- Shared core entities
CREATE SCHEMA IF NOT EXISTS transcription;  -- Transcription service
CREATE SCHEMA IF NOT EXISTS sentiment;      -- Sentiment analysis service
CREATE SCHEMA IF NOT EXISTS voc;            -- Voice of Customer service
CREATE SCHEMA IF NOT EXISTS audit;          -- Audit/compliance service
CREATE SCHEMA IF NOT EXISTS notification;   -- Notification service
CREATE SCHEMA IF NOT EXISTS analytics;      -- Analytics service
CREATE SCHEMA IF NOT EXISTS gateway;        -- API Gateway (users, auth)

-- ============================================
-- Enum types (in public schema for cross-schema use)
-- ============================================
-- Note: call_status and call_channel are stored as VARCHAR in the calls table
-- to match JPA @Enumerated(EnumType.STRING) mapping. Other enum types below
-- are still used as PostgreSQL custom types where appropriate.
-- CREATE TYPE call_status AS ENUM ('pending', 'transcribing', 'analyzing', 'completed', 'failed');
-- CREATE TYPE call_channel AS ENUM ('inbound', 'outbound', 'internal');
DO $$ BEGIN
    CREATE TYPE sentiment_type AS ENUM ('positive', 'negative', 'neutral');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE speaker_type AS ENUM ('agent', 'customer', 'unknown');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE compliance_status AS ENUM ('passed', 'failed', 'review_required');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE violation_severity AS ENUM ('critical', 'high', 'medium', 'low');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE satisfaction_level AS ENUM ('low', 'medium', 'high');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE intent_type AS ENUM ('inquiry', 'complaint', 'compliment', 'request', 'other');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

-- ============================================
-- CORE SCHEMA: Shared core entities
-- ============================================

-- Calls table - stores call metadata (owned by call-ingestion-service)
-- Note: channel and status use VARCHAR to match JPA @Enumerated(EnumType.STRING)
CREATE TABLE IF NOT EXISTS core.calls (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    caller_id VARCHAR(255) NOT NULL,
    agent_id VARCHAR(255) NOT NULL,
    channel VARCHAR(255) NOT NULL, -- Values: 'INBOUND', 'OUTBOUND', 'INTERNAL'
    start_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    duration INTEGER, -- Duration in seconds
    audio_file_url TEXT NOT NULL,
    file_size_bytes BIGINT,
    file_format VARCHAR(20),
    status VARCHAR(255) NOT NULL, -- Values: 'PENDING', 'TRANSCRIBING', 'ANALYZING', 'COMPLETED', 'FAILED'
    correlation_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_calls_caller_id ON core.calls(caller_id);
CREATE INDEX IF NOT EXISTS idx_calls_agent_id ON core.calls(agent_id);
CREATE INDEX IF NOT EXISTS idx_calls_status ON core.calls(status);
CREATE INDEX IF NOT EXISTS idx_calls_start_time ON core.calls(start_time);
CREATE INDEX IF NOT EXISTS idx_calls_correlation_id ON core.calls(correlation_id);

-- Event Store - shared event sourcing log
CREATE TABLE IF NOT EXISTS core.event_store (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_event_store_aggregate ON core.event_store(aggregate_id, version);
CREATE INDEX IF NOT EXISTS idx_event_store_type ON core.event_store(event_type);
CREATE INDEX IF NOT EXISTS idx_event_store_correlation ON core.event_store(correlation_id);
CREATE INDEX IF NOT EXISTS idx_event_store_created ON core.event_store(created_at);

-- ============================================
-- TRANSCRIPTION SCHEMA: Transcription service tables
-- ============================================

-- NOTE: Transcription tables moved to analytics schema
-- The transcription-service (Python) is stateless and publishes events to Kafka
-- The analytics-service consumes these events and persists to analytics schema

-- ============================================
-- SENTIMENT SCHEMA: Sentiment analysis service tables
-- ============================================

-- Sentiment results - overall call sentiment
CREATE TABLE IF NOT EXISTS sentiment.sentiment_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID NOT NULL,  -- Reference to core.calls (no FK - event-driven consistency)
    overall_sentiment sentiment_type NOT NULL,
    sentiment_score DECIMAL(5,4) CHECK (sentiment_score >= -1 AND sentiment_score <= 1),
    escalation_detected BOOLEAN NOT NULL DEFAULT FALSE,
    model_version VARCHAR(50),
    processing_time_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sentiment_results_call_id ON sentiment.sentiment_results(call_id);

-- Segment sentiments - sentiment per segment
CREATE TABLE IF NOT EXISTS sentiment.segment_sentiments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sentiment_result_id UUID NOT NULL REFERENCES sentiment.sentiment_results(id) ON DELETE CASCADE,
    segment_id UUID,  -- Reference to analytics.segments (no FK - loose coupling)
    start_time DECIMAL(10,3) NOT NULL,
    end_time DECIMAL(10,3) NOT NULL,
    sentiment sentiment_type NOT NULL,
    score DECIMAL(5,4),
    emotions TEXT[], -- Array of detected emotions
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_segment_sentiments_result_id ON sentiment.segment_sentiments(sentiment_result_id);

-- ============================================
-- VOC SCHEMA: Voice of Customer service tables
-- ============================================

-- VoC Insights - extracted insights per call
CREATE TABLE IF NOT EXISTS voc.voc_insights (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID NOT NULL,  -- Reference to core.calls (no FK - event-driven consistency)
    primary_intent intent_type NOT NULL DEFAULT 'other',
    topics JSONB NOT NULL DEFAULT '[]'::jsonb,
    keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    customer_satisfaction satisfaction_level,
    predicted_churn_risk DECIMAL(5,4) CHECK (predicted_churn_risk >= 0 AND predicted_churn_risk <= 1),
    actionable_items JSONB DEFAULT '[]'::jsonb,
    root_cause TEXT,
    summary TEXT,
    processing_time_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_voc_insights_call_id ON voc.voc_insights(call_id);
CREATE INDEX IF NOT EXISTS idx_voc_insights_intent ON voc.voc_insights(primary_intent);
CREATE INDEX IF NOT EXISTS idx_voc_insights_satisfaction ON voc.voc_insights(customer_satisfaction);
CREATE INDEX IF NOT EXISTS idx_voc_insights_churn_risk ON voc.voc_insights(predicted_churn_risk);
CREATE INDEX IF NOT EXISTS idx_voc_insights_topics ON voc.voc_insights USING GIN(topics);
CREATE INDEX IF NOT EXISTS idx_voc_insights_keywords ON voc.voc_insights USING GIN(keywords);

-- ============================================
-- AUDIT SCHEMA: Audit/compliance service tables
-- ============================================

-- Audit results - compliance evaluation per call
CREATE TABLE IF NOT EXISTS audit.audit_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID NOT NULL,  -- Reference to core.calls (no FK - event-driven consistency)
    overall_score INTEGER CHECK (overall_score >= 0 AND overall_score <= 100),
    compliance_status compliance_status NOT NULL,
    script_adherence INTEGER CHECK (script_adherence >= 0 AND script_adherence <= 100),
    customer_service INTEGER CHECK (customer_service >= 0 AND customer_service <= 100),
    resolution_effectiveness INTEGER CHECK (resolution_effectiveness >= 0 AND resolution_effectiveness <= 100),
    flags_for_review BOOLEAN NOT NULL DEFAULT FALSE,
    review_reason TEXT,
    reviewer_id VARCHAR(255),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    processing_time_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_results_call_id ON audit.audit_results(call_id);
CREATE INDEX IF NOT EXISTS idx_audit_results_status ON audit.audit_results(compliance_status);
CREATE INDEX IF NOT EXISTS idx_audit_results_score ON audit.audit_results(overall_score);
CREATE INDEX IF NOT EXISTS idx_audit_results_flags ON audit.audit_results(flags_for_review);

-- Compliance violations - specific violations detected
CREATE TABLE IF NOT EXISTS audit.compliance_violations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    audit_result_id UUID NOT NULL REFERENCES audit.audit_results(id) ON DELETE CASCADE,
    rule_id VARCHAR(100) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    severity violation_severity NOT NULL,
    description TEXT NOT NULL,
    segment_id UUID,  -- Reference to analytics.segments (no FK - loose coupling)
    timestamp_in_call DECIMAL(10,3), -- When in the call the violation occurred
    evidence TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_violations_audit_result ON audit.compliance_violations(audit_result_id);
CREATE INDEX IF NOT EXISTS idx_violations_severity ON audit.compliance_violations(severity);
CREATE INDEX IF NOT EXISTS idx_violations_rule_id ON audit.compliance_violations(rule_id);

-- Compliance rules - configurable audit rules
CREATE TABLE IF NOT EXISTS audit.compliance_rules (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    severity violation_severity NOT NULL DEFAULT 'medium',
    rule_definition JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ============================================
-- ANALYTICS SCHEMA: Analytics service tables (TimescaleDB Hypertables)
-- ============================================

-- Agent performance metrics - time-series data
CREATE TABLE IF NOT EXISTS analytics.agent_performance (
    time TIMESTAMP WITH TIME ZONE NOT NULL,
    agent_id VARCHAR(255) NOT NULL,
    calls_processed INTEGER NOT NULL DEFAULT 0,
    avg_quality_score DECIMAL(5,2),
    avg_customer_satisfaction DECIMAL(5,2),
    compliance_pass_rate DECIMAL(5,4),
    avg_sentiment_score DECIMAL(5,4),
    avg_churn_risk DECIMAL(5,4),
    avg_call_duration INTEGER,
    total_violations INTEGER DEFAULT 0
);

-- Convert to hypertable for time-series optimization
SELECT create_hypertable('analytics.agent_performance', 'time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_agent_performance_agent ON analytics.agent_performance(agent_id, time DESC);

-- Daily compliance metrics
CREATE TABLE IF NOT EXISTS analytics.compliance_metrics (
    time TIMESTAMP WITH TIME ZONE NOT NULL,
    total_calls INTEGER NOT NULL DEFAULT 0,
    passed_calls INTEGER NOT NULL DEFAULT 0,
    failed_calls INTEGER NOT NULL DEFAULT 0,
    review_calls INTEGER NOT NULL DEFAULT 0,
    pass_rate DECIMAL(5,4),
    critical_violations INTEGER DEFAULT 0,
    high_violations INTEGER DEFAULT 0,
    medium_violations INTEGER DEFAULT 0,
    low_violations INTEGER DEFAULT 0
);

SELECT create_hypertable('analytics.compliance_metrics', 'time', if_not_exists => TRUE);

-- Sentiment trends
CREATE TABLE IF NOT EXISTS analytics.sentiment_trends (
    time TIMESTAMP WITH TIME ZONE NOT NULL,
    agent_id VARCHAR(255),
    total_calls INTEGER NOT NULL DEFAULT 0,
    positive_calls INTEGER DEFAULT 0,
    negative_calls INTEGER DEFAULT 0,
    neutral_calls INTEGER DEFAULT 0,
    avg_sentiment_score DECIMAL(5,4),
    escalation_count INTEGER DEFAULT 0
);

SELECT create_hypertable('analytics.sentiment_trends', 'time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_sentiment_trends_agent ON analytics.sentiment_trends(agent_id, time DESC);

-- Calls table - read model populated from CallReceived events
-- This is a denormalized copy for analytics queries (CQRS pattern)
CREATE TABLE IF NOT EXISTS analytics.calls (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    caller_id VARCHAR(255) NOT NULL,
    agent_id VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    duration INTEGER,
    audio_file_url TEXT NOT NULL,
    file_size_bytes BIGINT,
    file_format VARCHAR(20),
    status VARCHAR(50) NOT NULL,
    correlation_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_analytics_calls_agent_id ON analytics.calls(agent_id);
CREATE INDEX IF NOT EXISTS idx_analytics_calls_caller_id ON analytics.calls(caller_id);
CREATE INDEX IF NOT EXISTS idx_analytics_calls_status ON analytics.calls(status);
CREATE INDEX IF NOT EXISTS idx_analytics_calls_start_time ON analytics.calls(start_time);
CREATE INDEX IF NOT EXISTS idx_analytics_calls_correlation_id ON analytics.calls(correlation_id);

-- Transcriptions table - read model populated from CallTranscribed events
CREATE TABLE IF NOT EXISTS analytics.transcriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID NOT NULL,  -- Reference to core.calls (no FK - event-driven consistency)
    full_text TEXT NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'en-US',
    confidence DECIMAL(5,4) CHECK (confidence >= 0 AND confidence <= 1),
    word_count INTEGER,
    processing_time_ms INTEGER,
    model_version VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_transcriptions_call_id ON analytics.transcriptions(call_id);

-- Segments table - speaker-separated segments within transcriptions
-- Note: speaker uses VARCHAR to match JPA @Enumerated(EnumType.STRING)
CREATE TABLE IF NOT EXISTS analytics.segments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transcription_id UUID NOT NULL REFERENCES analytics.transcriptions(id) ON DELETE CASCADE,
    speaker VARCHAR(50) NOT NULL, -- Values: 'agent', 'customer', 'unknown'
    start_time DECIMAL(10,3) NOT NULL, -- Start time in seconds
    end_time DECIMAL(10,3) NOT NULL,   -- End time in seconds
    text TEXT NOT NULL,
    confidence DECIMAL(5,4),
    word_count INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_segments_transcription_id ON analytics.segments(transcription_id);
CREATE INDEX IF NOT EXISTS idx_segments_speaker ON analytics.segments(speaker);

-- ============================================
-- Event Store Table (for event sourcing audit trail)
-- Note: Event store is already created in core schema above
-- ============================================
-- Notifications Table
-- ============================================

CREATE TABLE IF NOT EXISTS notification.notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID,  -- Reference to core.calls (no FK - event-driven consistency)
    notification_type VARCHAR(100) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL, -- email, slack, webhook, etc.
    subject VARCHAR(500),
    body TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'normal',
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    sent_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_call_id ON notification.notifications(call_id);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notification.notifications(status);
CREATE INDEX IF NOT EXISTS idx_notifications_type ON notification.notifications(notification_type);

-- ============================================
-- Helper Functions
-- ============================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply trigger to calls table
CREATE TRIGGER update_calls_updated_at
    BEFORE UPDATE ON core.calls
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Apply trigger to compliance_rules table
CREATE TRIGGER update_compliance_rules_updated_at
    BEFORE UPDATE ON audit.compliance_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- Sample Compliance Rules
-- ============================================

INSERT INTO audit.compliance_rules (id, name, description, category, severity, rule_definition) VALUES
('GREETING_REQUIRED', 'Greeting Required', 'Agent must greet the customer within first 10 seconds', 'greeting', 'medium',
 '{"type": "keyword_check", "keywords": ["hello", "hi", "good morning", "good afternoon", "welcome"], "time_window": {"start": 0, "end": 10}, "speaker": "agent"}'::jsonb),

('DISCLOSURE_REQUIRED', 'Recording Disclosure', 'Agent must inform customer about call recording', 'compliance', 'critical',
 '{"type": "keyword_check", "keywords": ["call may be recorded", "call is being recorded", "recording this call"], "time_window": {"start": 0, "end": 30}, "speaker": "agent"}'::jsonb),

('NO_PROFANITY', 'No Profanity', 'Agent must not use profane language', 'conduct', 'high',
 '{"type": "prohibited_words", "words": ["damn", "hell", "crap"], "speaker": "agent"}'::jsonb),

('EMPATHY_CHECK', 'Empathy Expression', 'Agent should express empathy when customer is frustrated', 'quality', 'low',
 '{"type": "sentiment_response", "trigger_sentiment": "negative", "required_keywords": ["understand", "sorry", "apologize", "help"], "speaker": "agent"}'::jsonb),

('CLOSING_REQUIRED', 'Proper Closing', 'Agent must properly close the call', 'greeting', 'medium',
 '{"type": "keyword_check", "keywords": ["thank you for calling", "have a great day", "is there anything else"], "time_window": {"start": -30, "end": 0}, "speaker": "agent"}'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================
-- Views for Common Queries
-- ============================================

-- View: Call summary with all analysis results
CREATE OR REPLACE VIEW call_summary AS
SELECT
    c.id as call_id,
    c.caller_id,
    c.agent_id,
    c.channel,
    c.start_time,
    c.duration,
    c.status,
    t.language,
    t.confidence as transcription_confidence,
    sr.overall_sentiment,
    sr.sentiment_score,
    sr.escalation_detected,
    vi.primary_intent,
    vi.topics,
    vi.customer_satisfaction,
    vi.predicted_churn_risk,
    ar.overall_score as audit_score,
    ar.compliance_status,
    ar.flags_for_review,
    c.created_at
FROM core.calls c
LEFT JOIN analytics.transcriptions t ON c.id = t.call_id
LEFT JOIN sentiment.sentiment_results sr ON c.id = sr.call_id
LEFT JOIN voc.voc_insights vi ON c.id = vi.call_id
LEFT JOIN audit.audit_results ar ON c.id = ar.call_id;

-- View: Agent performance summary
CREATE OR REPLACE VIEW agent_summary AS
SELECT
    agent_id,
    COUNT(*) as total_calls,
    AVG(CASE WHEN ar.compliance_status = 'passed' THEN 1 ELSE 0 END) as compliance_rate,
    AVG(ar.overall_score) as avg_audit_score,
    AVG(sr.sentiment_score) as avg_sentiment,
    AVG(vi.predicted_churn_risk) as avg_churn_risk,
    SUM(CASE WHEN sr.escalation_detected THEN 1 ELSE 0 END) as escalation_count
FROM core.calls c
LEFT JOIN audit.audit_results ar ON c.id = ar.call_id
LEFT JOIN sentiment.sentiment_results sr ON c.id = sr.call_id
LEFT JOIN voc.voc_insights vi ON c.id = vi.call_id
WHERE c.status = 'completed'
GROUP BY agent_id;

-- ============================================
-- GATEWAY SCHEMA: Authentication & Users
-- ============================================

-- Create users table for authentication
CREATE TABLE IF NOT EXISTS gateway.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(50) DEFAULT 'ANALYST',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on email for faster lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON gateway.users(email);

-- Insert test users
-- Password for all test users is: password123
-- BCrypt hash generated with: new BCryptPasswordEncoder().encode("password123")
INSERT INTO gateway.users (id, email, password_hash, full_name, role, created_at, updated_at)
VALUES
    ('479d228b-018f-47a6-bbcc-ee612ca74ab0', 'analyst@example.com', '$2a$10$6N2j8dh5mXdW1GFHWrQpkuHSClt42.GvUCeqPcP8iDk6MIW46Tyiy', 'John Analyst', 'ANALYST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'admin@example.com', '$2a$10$uVAqQN63TJVJY/sS4IeYMeuM8DkTpQG..aGCufcI3ZS05TkHMGbQO', 'Admin User', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'supervisor@example.com', '$2a$10$uVAqQN63TJVJY/sS4IeYMeuM8DkTpQG..aGCufcI3ZS05TkHMGbQO', 'Jane Supervisor', 'SUPERVISOR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

-- Update function for updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger to automatically update updated_at
DROP TRIGGER IF EXISTS update_users_updated_at ON gateway.users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON gateway.users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- PERMISSIONS
-- ============================================

-- Grant permissions (adjust as needed for your setup)
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO your_app_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO your_app_user;
