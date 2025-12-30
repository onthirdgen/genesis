-- Call Auditing Platform Database Schema
-- PostgreSQL 16 with TimescaleDB Extension

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enum types
-- Note: call_status and call_channel are stored as VARCHAR in the calls table
-- to match JPA @Enumerated(EnumType.STRING) mapping. Other enum types below
-- are still used as PostgreSQL custom types where appropriate.
-- CREATE TYPE call_status AS ENUM ('pending', 'transcribing', 'analyzing', 'completed', 'failed');
-- CREATE TYPE call_channel AS ENUM ('inbound', 'outbound', 'internal');
CREATE TYPE sentiment_type AS ENUM ('positive', 'negative', 'neutral');
CREATE TYPE speaker_type AS ENUM ('agent', 'customer', 'unknown');
CREATE TYPE compliance_status AS ENUM ('passed', 'failed', 'review_required');
CREATE TYPE violation_severity AS ENUM ('critical', 'high', 'medium', 'low');
CREATE TYPE satisfaction_level AS ENUM ('low', 'medium', 'high');
CREATE TYPE intent_type AS ENUM ('inquiry', 'complaint', 'compliment', 'request', 'other');

-- ============================================
-- Core Tables
-- ============================================

-- Calls table - stores call metadata
-- Note: channel and status use VARCHAR to match JPA @Enumerated(EnumType.STRING)
CREATE TABLE calls (
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

CREATE INDEX idx_calls_caller_id ON calls(caller_id);
CREATE INDEX idx_calls_agent_id ON calls(agent_id);
CREATE INDEX idx_calls_status ON calls(status);
CREATE INDEX idx_calls_start_time ON calls(start_time);
CREATE INDEX idx_calls_correlation_id ON calls(correlation_id);

-- ============================================
-- Transcription Tables
-- ============================================

-- Transcriptions table - stores full transcription
CREATE TABLE transcriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
    full_text TEXT NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'en-US',
    confidence DECIMAL(5,4) CHECK (confidence >= 0 AND confidence <= 1),
    word_count INTEGER,
    processing_time_ms INTEGER,
    model_version VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_transcriptions_call_id ON transcriptions(call_id);

-- Segments table - speaker-separated segments
CREATE TABLE segments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transcription_id UUID NOT NULL REFERENCES transcriptions(id) ON DELETE CASCADE,
    speaker speaker_type NOT NULL,
    start_time DECIMAL(10,3) NOT NULL, -- Start time in seconds
    end_time DECIMAL(10,3) NOT NULL,   -- End time in seconds
    text TEXT NOT NULL,
    confidence DECIMAL(5,4),
    word_count INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_segments_transcription_id ON segments(transcription_id);
CREATE INDEX idx_segments_speaker ON segments(speaker);

-- ============================================
-- Sentiment Analysis Tables
-- ============================================

-- Sentiment results - overall call sentiment
CREATE TABLE sentiment_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
    overall_sentiment sentiment_type NOT NULL,
    sentiment_score DECIMAL(5,4) CHECK (sentiment_score >= -1 AND sentiment_score <= 1),
    escalation_detected BOOLEAN NOT NULL DEFAULT FALSE,
    model_version VARCHAR(50),
    processing_time_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_sentiment_results_call_id ON sentiment_results(call_id);

-- Segment sentiments - sentiment per segment
CREATE TABLE segment_sentiments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sentiment_result_id UUID NOT NULL REFERENCES sentiment_results(id) ON DELETE CASCADE,
    segment_id UUID REFERENCES segments(id) ON DELETE SET NULL,
    start_time DECIMAL(10,3) NOT NULL,
    end_time DECIMAL(10,3) NOT NULL,
    sentiment sentiment_type NOT NULL,
    score DECIMAL(5,4),
    emotions TEXT[], -- Array of detected emotions
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_segment_sentiments_result_id ON segment_sentiments(sentiment_result_id);

-- ============================================
-- Voice of Customer (VoC) Tables
-- ============================================

-- VoC Insights - extracted insights per call
CREATE TABLE voc_insights (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
    primary_intent intent_type NOT NULL DEFAULT 'other',
    topics TEXT[] NOT NULL DEFAULT '{}',
    keywords TEXT[] NOT NULL DEFAULT '{}',
    customer_satisfaction satisfaction_level,
    predicted_churn_risk DECIMAL(5,4) CHECK (predicted_churn_risk >= 0 AND predicted_churn_risk <= 1),
    actionable_items JSONB DEFAULT '[]'::jsonb,
    root_cause TEXT,
    summary TEXT,
    processing_time_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_voc_insights_call_id ON voc_insights(call_id);
CREATE INDEX idx_voc_insights_intent ON voc_insights(primary_intent);
CREATE INDEX idx_voc_insights_satisfaction ON voc_insights(customer_satisfaction);
CREATE INDEX idx_voc_insights_churn_risk ON voc_insights(predicted_churn_risk);
CREATE INDEX idx_voc_insights_topics ON voc_insights USING GIN(topics);
CREATE INDEX idx_voc_insights_keywords ON voc_insights USING GIN(keywords);

-- ============================================
-- Audit Tables
-- ============================================

-- Audit results - compliance evaluation per call
CREATE TABLE audit_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
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

CREATE UNIQUE INDEX idx_audit_results_call_id ON audit_results(call_id);
CREATE INDEX idx_audit_results_status ON audit_results(compliance_status);
CREATE INDEX idx_audit_results_score ON audit_results(overall_score);
CREATE INDEX idx_audit_results_flags ON audit_results(flags_for_review);

-- Compliance violations - specific violations detected
CREATE TABLE compliance_violations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    audit_result_id UUID NOT NULL REFERENCES audit_results(id) ON DELETE CASCADE,
    rule_id VARCHAR(100) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    severity violation_severity NOT NULL,
    description TEXT NOT NULL,
    segment_id UUID REFERENCES segments(id) ON DELETE SET NULL,
    timestamp_in_call DECIMAL(10,3), -- When in the call the violation occurred
    evidence TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_violations_audit_result ON compliance_violations(audit_result_id);
CREATE INDEX idx_violations_severity ON compliance_violations(severity);
CREATE INDEX idx_violations_rule_id ON compliance_violations(rule_id);

-- Compliance rules - configurable audit rules
CREATE TABLE compliance_rules (
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
-- Analytics Tables (TimescaleDB Hypertables)
-- ============================================

-- Agent performance metrics - time-series data
CREATE TABLE agent_performance (
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
SELECT create_hypertable('agent_performance', 'time', if_not_exists => TRUE);

CREATE INDEX idx_agent_performance_agent ON agent_performance(agent_id, time DESC);

-- Daily compliance metrics
CREATE TABLE compliance_metrics (
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

SELECT create_hypertable('compliance_metrics', 'time', if_not_exists => TRUE);

-- Sentiment trends
CREATE TABLE sentiment_trends (
    time TIMESTAMP WITH TIME ZONE NOT NULL,
    agent_id VARCHAR(255),
    total_calls INTEGER NOT NULL DEFAULT 0,
    positive_calls INTEGER DEFAULT 0,
    negative_calls INTEGER DEFAULT 0,
    neutral_calls INTEGER DEFAULT 0,
    avg_sentiment_score DECIMAL(5,4),
    escalation_count INTEGER DEFAULT 0
);

SELECT create_hypertable('sentiment_trends', 'time', if_not_exists => TRUE);

CREATE INDEX idx_sentiment_trends_agent ON sentiment_trends(agent_id, time DESC);

-- ============================================
-- Event Store Table (for event sourcing audit trail)
-- ============================================

CREATE TABLE event_store (
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

CREATE INDEX idx_event_store_aggregate ON event_store(aggregate_id, version);
CREATE INDEX idx_event_store_type ON event_store(event_type);
CREATE INDEX idx_event_store_correlation ON event_store(correlation_id);
CREATE INDEX idx_event_store_created ON event_store(created_at);

-- ============================================
-- Notifications Table
-- ============================================

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    call_id UUID REFERENCES calls(id) ON DELETE SET NULL,
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

CREATE INDEX idx_notifications_call_id ON notifications(call_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_type ON notifications(notification_type);

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
    BEFORE UPDATE ON calls
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Apply trigger to compliance_rules table
CREATE TRIGGER update_compliance_rules_updated_at
    BEFORE UPDATE ON compliance_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- Sample Compliance Rules
-- ============================================

INSERT INTO compliance_rules (id, name, description, category, severity, rule_definition) VALUES
('GREETING_REQUIRED', 'Greeting Required', 'Agent must greet the customer within first 10 seconds', 'greeting', 'medium',
 '{"type": "keyword_check", "keywords": ["hello", "hi", "good morning", "good afternoon", "welcome"], "time_window": {"start": 0, "end": 10}, "speaker": "agent"}'::jsonb),

('DISCLOSURE_REQUIRED', 'Recording Disclosure', 'Agent must inform customer about call recording', 'compliance', 'critical',
 '{"type": "keyword_check", "keywords": ["call may be recorded", "call is being recorded", "recording this call"], "time_window": {"start": 0, "end": 30}, "speaker": "agent"}'::jsonb),

('NO_PROFANITY', 'No Profanity', 'Agent must not use profane language', 'conduct', 'high',
 '{"type": "prohibited_words", "words": ["damn", "hell", "crap"], "speaker": "agent"}'::jsonb),

('EMPATHY_CHECK', 'Empathy Expression', 'Agent should express empathy when customer is frustrated', 'quality', 'low',
 '{"type": "sentiment_response", "trigger_sentiment": "negative", "required_keywords": ["understand", "sorry", "apologize", "help"], "speaker": "agent"}'::jsonb),

('CLOSING_REQUIRED', 'Proper Closing', 'Agent must properly close the call', 'greeting', 'medium',
 '{"type": "keyword_check", "keywords": ["thank you for calling", "have a great day", "is there anything else"], "time_window": {"start": -30, "end": 0}, "speaker": "agent"}'::jsonb);

-- ============================================
-- Views for Common Queries
-- ============================================

-- View: Call summary with all analysis results
CREATE VIEW call_summary AS
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
FROM calls c
LEFT JOIN transcriptions t ON c.id = t.call_id
LEFT JOIN sentiment_results sr ON c.id = sr.call_id
LEFT JOIN voc_insights vi ON c.id = vi.call_id
LEFT JOIN audit_results ar ON c.id = ar.call_id;

-- View: Agent performance summary
CREATE VIEW agent_summary AS
SELECT
    agent_id,
    COUNT(*) as total_calls,
    AVG(CASE WHEN ar.compliance_status = 'passed' THEN 1 ELSE 0 END) as compliance_rate,
    AVG(ar.overall_score) as avg_audit_score,
    AVG(sr.sentiment_score) as avg_sentiment,
    AVG(vi.predicted_churn_risk) as avg_churn_risk,
    SUM(CASE WHEN sr.escalation_detected THEN 1 ELSE 0 END) as escalation_count
FROM calls c
LEFT JOIN audit_results ar ON c.id = ar.call_id
LEFT JOIN sentiment_results sr ON c.id = sr.call_id
LEFT JOIN voc_insights vi ON c.id = vi.call_id
WHERE c.status = 'completed'
GROUP BY agent_id;

-- Grant permissions (adjust as needed for your setup)
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO your_app_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO your_app_user;
