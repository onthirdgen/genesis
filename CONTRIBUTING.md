# Contributing to Call Auditing Platform

Thank you for your interest in contributing!

## Development Setup

1. Install Docker Desktop
2. Clone the repository
3. Copy `.env.example` to `.env` and customize if needed
4. Run `docker compose up -d`

## Project Structure

Implemented microservices:
- `call-ingestion-service/` - Spring Boot service (audio upload)
- `transcription-service/` - Python/FastAPI service (speech-to-text)
- `monitor-service/` - Spring Boot service (Kafka inspection)

## Development Workflow

### Spring Boot Services

1. Navigate to service directory
2. Implement your changes
3. Build: `./mvnw clean package`
4. Test: `./mvnw test`
5. Run locally: `./mvnw spring-boot:run`

### Python Services

1. Navigate to service directory
2. Create virtual environment: `python -m venv venv`
3. Activate: `source venv/bin/activate` (Linux/Mac) or `venv\Scripts\activate` (Windows)
4. Install dependencies: `pip install -r requirements.txt`
5. Run: `uvicorn main:app --reload`

## Code Standards

### Java/Spring Boot
- Use Java 21 features
- Follow Spring Boot best practices
- Include unit tests (JUnit 5)
- Use Lombok to reduce boilerplate
- Enable actuator endpoints for monitoring

### Python
- Use Python 3.12+
- Follow PEP 8 style guide
- Type hints required
- Include tests (pytest)
- Use FastAPI best practices

## Testing

```bash
# Spring Boot
./mvnw test

# Python
pytest

# Integration tests (when available)
docker compose -f docker-compose.test.yml up
```

## Commit Messages

Follow conventional commits:
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation changes
- `test:` Test changes
- `refactor:` Code refactoring
- `chore:` Maintenance tasks

Example: `feat: add speaker diarization to transcription service`

## Pull Request Process

1. Fork the repository
2. Create a feature branch: `git checkout -b feat/my-feature`
3. Make your changes
4. Run tests
5. Commit with conventional commit message
6. Push to your fork
7. Create Pull Request

## Questions?

Open an issue for questions or discussions!
