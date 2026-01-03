-- H2 test schema for voc-service
-- Uses VARCHAR instead of JSONB since H2 doesn't fully support PostgreSQL JSONB

CREATE SCHEMA IF NOT EXISTS voc;

CREATE TABLE IF NOT EXISTS voc.voc_insights (
    id VARCHAR(36) PRIMARY KEY,
    call_id VARCHAR(255) NOT NULL UNIQUE,
    primary_intent VARCHAR(50) NOT NULL,
    topics VARCHAR(4000),
    keywords VARCHAR(4000),
    customer_satisfaction VARCHAR(50) NOT NULL,
    predicted_churn_risk DOUBLE NOT NULL,
    actionable_items VARCHAR(4000),
    summary TEXT,
    created_at TIMESTAMP NOT NULL
);
