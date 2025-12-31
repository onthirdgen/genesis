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
└────────────────────────────┬────────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
    ┌────▼─────┐      ┌─────▼──────┐     ┌─────▼──────┐
    │  Call    │      │   Audit    │     │    VoC     │
    │ Ingestion│      │  Service   │     │  Service   │
    │ Service  │      │            │     │            │
    └────┬─────┘      └─────┬──────┘     └─────┬──────┘
         │                  │                   │
         │                  │                   │
         └──────────────────┼───────────────────┘
                            │
                    ┌───────▼────────┐
                    │  Event Store   │
                    │   (Kafka)      │
                    └───────┬────────┘
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
    ┌────▼────────┐   ┌─────▼──────┐    ┌─────▼──────┐
    │Transcription│   │ Analytics  │    │ Sentiment  │
    │  Service    │   │  Service   │    │  Service   │
    └────┬────────┘   └─────┬──────┘    └─────┬──────┘
         │                  │                  │
         └──────────────────┼──────────────────┘
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

### Core Tables

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

### TimescaleDB Hypertables (Time-Series)

| Table | Purpose |
|-------|---------|
| `agent_performance` | Agent metrics over time |
| `compliance_metrics` | Daily compliance rates |
| `sentiment_trends` | Sentiment trends |

## Monitoring URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Grafana | http://localhost:3000 | admin/admin |
| Jaeger | http://localhost:16686 | - |
| Prometheus | http://localhost:9090 | - |
| OpenSearch Dashboards | http://localhost:5601 | - |
| MinIO Console | http://localhost:9001 | minioadmin/minioadmin |
