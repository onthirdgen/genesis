# Helper Scripts

This directory contains utility scripts for managing the Call Auditing Platform.

## Scripts

### 1. quick-reset.sh

**Purpose**: Complete data reset with fresh start

Performs a full system reset by:
- Stopping all services and removing volumes
- Clearing local logs
- Reinitializing database schema
- Recreating MinIO buckets
- Recreating Kafka topics
- Starting all services from scratch

**Usage**:
```bash
./HELPER_SCRIPTS/quick-reset.sh
```

**When to use**:
- Testing from a clean slate
- Resolving data corruption issues
- Clearing all test data
- Resetting after major schema changes

**⚠️ Warning**: This deletes ALL data (database, Kafka messages, stored files, etc.)

---

### 2. rebuild-and-deploy.sh

**Purpose**: Rebuild and deploy services without data loss (by default)

Rebuilds Docker images and deploys services with flexible options:
- Rebuild all services or specific subsets
- Optional Docker cache bypass
- Optional data preservation
- Automatic health verification

**Usage**:

```bash
# Rebuild and deploy everything (preserves data by default)
./HELPER_SCRIPTS/rebuild-and-deploy.sh --all

# Rebuild only backend services
./HELPER_SCRIPTS/rebuild-and-deploy.sh --backend

# Rebuild only frontend services
./HELPER_SCRIPTS/rebuild-and-deploy.sh --frontend

# Rebuild without Docker cache (clean build)
./HELPER_SCRIPTS/rebuild-and-deploy.sh --all --no-cache

# Rebuild and clear all data
./HELPER_SCRIPTS/rebuild-and-deploy.sh --all

# Rebuild and keep existing data
./HELPER_SCRIPTS/rebuild-and-deploy.sh --all --keep-data
```

**Options**:
- `--all` - Rebuild all services (backend + frontend)
- `--backend` - Rebuild only backend services
- `--frontend` - Rebuild only frontend services
- `--no-cache` - Build without using Docker cache
- `--keep-data` - Keep data volumes (don't use -v flag on docker compose down)
- `--help` - Show help message

**Backend Services**:
- api-gateway
- call-ingestion-service
- transcription-service
- sentiment-service
- voc-service
- audit-service
- analytics-service
- notification-service
- monitor-service

**Frontend Services**:
- call-auditing-ui

**When to use**:
- After code changes
- Deploying updates
- Rebuilding specific services after bug fixes
- Testing new features with existing data

---

## Comparison

| Feature | quick-reset.sh | rebuild-and-deploy.sh |
|---------|----------------|----------------------|
| Rebuilds services | No | Yes |
| Preserves data | No | Yes (with --keep-data) |
| Initializes schema | Yes | Only if data cleared |
| Creates topics | Yes | Only if data cleared |
| Selective rebuild | No | Yes (--backend/--frontend) |
| Use case | Clean slate testing | Code updates |

## Quick Reference

```bash
# Development workflow
./HELPER_SCRIPTS/rebuild-and-deploy.sh --backend        # After backend changes
./HELPER_SCRIPTS/rebuild-and-deploy.sh --frontend       # After frontend changes
./HELPER_SCRIPTS/rebuild-and-deploy.sh --all --no-cache # Force clean rebuild

# Testing workflow
./HELPER_SCRIPTS/quick-reset.sh                         # Start fresh for testing
./HELPER_SCRIPTS/rebuild-and-deploy.sh --all            # Deploy with test data intact
```

## Notes

- Both scripts require confirmation before execution
- Scripts should be run from the project root directory
- All scripts provide health check verification
- Check logs if services fail: `docker compose logs [service-name]`
