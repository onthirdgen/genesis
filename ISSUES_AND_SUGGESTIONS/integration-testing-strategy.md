# Integration Testing Strategy

**Status**: ✅ IMPLEMENTED
**Date**: 2025-12-31
**Service**: call-ingestion-service

## Overview

This document describes the three-tier testing strategy implemented for the call-ingestion-service, which balances fast CI/CD execution with comprehensive local infrastructure testing.

## Three-Tier Testing Strategy

| Test Type | Infrastructure | Execution | Command | Test Count |
|-----------|---------------|-----------|---------|------------|
| **Unit Tests** | Mocks only | Always (CI/CD + local) | `mvn test` | 29 tests |
| **Integration Tests** | H2 + mocks | Always (CI/CD + local) | `mvn test` (included) | 25 tests |
| **Contract Tests** | Real Docker services | Local only (manual) | `mvn verify -Pintegration` | 3 tests (example) |

**Total tests in CI/CD**: 54 tests (all pass, ~15 seconds)
**Total tests locally**: 54 + contract tests (all pass, ~2 minutes with Docker)

## Test Categories

### Unit Tests (@Tag("unit"))
- **Purpose**: Test business logic in isolation
- **Infrastructure**: Pure Mockito mocks, no external dependencies
- **Examples**:
  - `CallIngestionServiceTest` - Service logic with mocked repository/storage
  - `StorageServiceTest` - MinIO client interactions with mocks
- **Execution**: Always run in CI/CD and local development

### Integration Tests (@Tag("integration"))
- **Purpose**: Test database integration with H2
- **Infrastructure**: H2 in-memory database, Spring Data JPA
- **Examples**:
  - `CallRepositoryTest` - JPA repository operations
  - `CallIngestionControllerManualTest` - Controller with mocked services
- **Execution**: Always run in CI/CD and local development
- **Limitations**: Cannot test PostgreSQL-specific features (JSONB, TimescaleDB)

### Contract Tests (@Tag("contract"))
- **Purpose**: Verify integration with REAL infrastructure
- **Infrastructure**: Testcontainers managing PostgreSQL, MinIO, Kafka
- **Examples**:
  - `CallIngestionContractTest` - End-to-end flow with real services
- **Execution**: ONLY run locally via `mvn verify -Pintegration`
- **Benefits**: Tests actual PostgreSQL queries, MinIO S3 compatibility, Kafka messaging

## Configuration

### Maven Plugins

**maven-surefire-plugin** (runs during `mvn test`):
```xml
<configuration>
    <excludedGroups>contract</excludedGroups>
</configuration>
```
- Runs all tests EXCEPT those tagged with `@Tag("contract")`
- Total: 54 tests (unit + integration)

**maven-failsafe-plugin** (runs during `mvn verify -Pintegration`):
```xml
<profile>
    <id>integration</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                    <groups>contract</groups>
                    <includes>
                        <include>**/*ContractTest.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```
- Runs ONLY tests tagged with `@Tag("contract")`
- Requires Docker to be running locally

### Testcontainers Configuration

**ContractTestBase.java** - Base class for all contract tests:
- Starts PostgreSQL (TimescaleDB), MinIO, Kafka containers
- Containers shared across all contract tests (fast execution)
- Auto-configures Spring Boot via `@DynamicPropertySource`
- Tests auto-skip if Docker unavailable

**application-contract.yml** - Test profile configuration:
- `spring.jpa.hibernate.ddl-auto: create-drop` - Schema from entities
- Debug logging for SQL, Kafka, Testcontainers
- Values overridden by Testcontainers dynamic properties

## Usage

### CI/CD Pipeline (Azure DevOps)

```yaml
# azure-pipelines.yml
steps:
  - task: Maven@3
    inputs:
      goals: 'clean test'  # Only runs unit + integration tests (54 tests)
    displayName: 'Run Unit Tests'
```

**Result**: 54 tests run, all mocked, no Docker required, ~15-30 seconds

### Local Development

**Fast feedback (unit + integration tests)**:
```bash
mvn test
# Runs 54 tests with mocks and H2 (~15 seconds)
```

**Full integration testing (with Docker)**:
```bash
# Ensure Docker is running
docker ps

# Run contract tests
mvn verify -Pintegration
# Runs 54 unit tests + contract tests against real infrastructure (~2 minutes)
```

## Benefits

### For Developers
- ✅ Fast unit tests (always run, ~15 seconds)
- ✅ Comprehensive contract tests (run on-demand, verify real integration)
- ✅ No manual infrastructure setup (Testcontainers handles it)
- ✅ Can verify PostgreSQL-specific features (JSONB, TimescaleDB)
- ✅ Can verify MinIO S3 compatibility
- ✅ Can verify Kafka message publishing

### For CI/CD
- ✅ No Docker requirement
- ✅ Fast pipeline execution (~15-30 seconds for tests)
- ✅ No infrastructure setup/teardown
- ✅ Reliable (no flaky network/timing issues)

### For Quality
- ✅ Unit tests verify business logic
- ✅ Integration tests verify JPA repositories with H2
- ✅ Contract tests verify actual infrastructure integration
- ✅ Can catch PostgreSQL-specific issues
- ✅ Can catch MinIO S3 compatibility issues
- ✅ Can catch Kafka connectivity issues

## Example Contract Test

```java
@Test
void testCompleteCallIngestionFlow() throws IOException {
    // Arrange
    byte[] audioContent = "fake audio content".getBytes();
    MockMultipartFile audioFile = new MockMultipartFile(
        "file", "test-call.wav", "audio/wav", audioContent
    );

    // Act - Process upload (saves to real PostgreSQL, uploads to real MinIO)
    Call result = callIngestionService.processUpload(
        audioFile, "+1234567890", "agent-001", CallChannel.INBOUND
    );

    // Assert - Verify database persistence
    assertNotNull(result.getId());

    // Verify saved to real PostgreSQL
    Call savedCall = callRepository.findById(result.getId()).orElseThrow();
    assertThat(savedCall.getAudioFileUrl()).contains("http://");

    // Verify file uploaded to real MinIO
    assertDoesNotThrow(() -> storageService.downloadFile(result.getId(), "wav"));
}
```

## Troubleshooting

### Contract tests fail with "Docker not running"
**Solution**: Start Docker Desktop
```bash
docker ps
# If this fails, Docker is not running
```

### Contract tests are slow
**Expected**: First run downloads Docker images (~500MB), subsequent runs are fast (~30 seconds)

### Contract tests run during `mvn test`
**Problem**: Tests not tagged with `@Tag("contract")`
**Solution**: Ensure contract test class extends `ContractTestBase` (which has `@Tag("contract")`)

### Contract tests don't run with `mvn verify -Pintegration`
**Problem**: Test class name doesn't match pattern
**Solution**: Name contract tests with `*ContractTest.java` suffix

## Future Enhancements

### Potential Additions:
1. **Kafka contract tests** - Verify event publishing to real Kafka
2. **TimescaleDB-specific tests** - Test hypertables, continuous aggregates
3. **Performance benchmarks** - Measure query performance with real PostgreSQL
4. **Multi-service contract tests** - Test service-to-service communication

### Alternative Approaches Considered:

**Always run with Testcontainers**:
- **Pros**: Most comprehensive testing
- **Cons**: Slow CI/CD (2-5 min), requires Docker in CI/CD
- **Decision**: Rejected - too slow for every commit

**Use environment variable to control tests**:
```java
@EnabledIf("#{environment['RUN_INTEGRATION_TESTS'] == 'true'}")
```
- **Pros**: Simple
- **Cons**: Easy to forget to set, requires env var management
- **Decision**: Rejected - Maven profiles are more explicit

**Separate integration-test source directory**:
- **Pros**: Clear separation
- **Cons**: More complex build configuration
- **Decision**: Rejected - Maven profiles simpler

## References

- Maven Surefire Plugin: https://maven.apache.org/surefire/maven-surefire-plugin/
- Maven Failsafe Plugin: https://maven.apache.org/surefire/maven-failsafe-plugin/
- Testcontainers: https://testcontainers.com/
- JUnit 5 Tagging: https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-12-31 | Initial implementation with 3-tier strategy |
