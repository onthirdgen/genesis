# Issue: MinIO-Dependent Test Failures in Call Ingestion Service

**Date Identified**: December 31, 2025
**Severity**: HIGH (Blocks CI/CD pipeline)
**Affected Service**: `call-ingestion-service`
**Impact**: 7 out of 54 tests failing (13% failure rate)

---

## Problem Summary

When running `./mvnw test` in the call-ingestion-service, **7 tests fail** due to attempting to connect to a MinIO service that is not available during test execution.

### Test Execution Results

```
[ERROR] Tests run: 54, Failures: 7, Errors: 0, Skipped: 0
```

**Status**: ❌ **FAILING** (7 failures, 47 passed)

---

## Failed Tests

All failures are related to MinIO connectivity:

1. **CallIngestionServiceTest.testStoreAudioFile**
   - Error: "Unable to reach MinIO service at http://localhost:9000"

2. **CallIngestionServiceTest.testStoreAudioFileWithIOException**
   - Error: MinIO connection failed

3. **CallIngestionServiceTest.testStoreAudioFileWithInvalidExtension**
   - Error: MinIO bucket check failed

4. **CallIngestionServiceTest.testStoreAudioFileWithLargeFile**
   - Error: MinIO connectivity issue

5. **CallIngestionControllerTest.testUploadCallSuccess**
   - Error: MinIO service unavailable at http://localhost:9000

6. **CallIngestionControllerTest.testUploadCallWithLargeFile**
   - Error: "Failed to ensure bucket exists"

7. **CallIngestionControllerTest.testUploadCallWithMissingFields**
   - Error: "Unable to check bucket existence"

---

## Root Cause Analysis

### Why Tests Are Failing

The tests are configured as **integration tests** using `@SpringBootTest`, which:

1. Starts a full Spring application context
2. Loads the `StorageService` with real MinIO client configuration
3. Attempts to connect to MinIO at `http://localhost:9000`
4. Fails because MinIO is not running during test execution

### Current Test Configuration

```java
@SpringBootTest
@ActiveProfiles("test")
class CallIngestionControllerTest {

    @Autowired
    private CallIngestionService callIngestionService;

    // StorageService is NOT mocked - uses real MinIO client
    // This causes connection failures during tests
}
```

### Configuration in `application-test.yml`

```yaml
minio:
  endpoint: http://localhost:9000  # MinIO not running during tests
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: calls
```

The tests attempt to use the real configuration, but MinIO is not available in the test environment.

---

## Impact on CI/CD Pipeline

### Will This Fail in Azure DevOps?

**YES - Tests will fail in Azure DevOps CI/CD pipeline** for the same reason.

### Why CI/CD Will Fail

In Azure DevOps (or any CI/CD environment):

```yaml
# Azure DevOps Pipeline
steps:
  - task: Maven@3
    inputs:
      goals: 'test'
    # ❌ This will fail with 7 test failures
```

**Failure Scenario**:
1. Build agent runs `mvn test`
2. Tests start in isolated environment
3. No MinIO service running at `localhost:9000`
4. Tests fail with connection errors
5. Pipeline fails ❌
6. Build marked as failed
7. Deployment blocked

### Consequences

- ❌ **Pull requests blocked** - Cannot merge code with failing tests
- ❌ **CI/CD pipeline fails** - No deployments possible
- ❌ **Build status: RED** - Team velocity impacted
- ❌ **Developer frustration** - Tests fail locally and in CI

---

## Solutions

### Option 1: Mock StorageService (Recommended for Unit Tests)

**Approach**: Mock the `StorageService` dependency in controller and service tests.

**Implementation**:

```java
@SpringBootTest
@ActiveProfiles("test")
class CallIngestionControllerTest {

    @MockBean  // Mock the StorageService
    private StorageService storageService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testUploadCallSuccess() throws Exception {
        // Given
        when(storageService.uploadFile(any(), any(), any()))
            .thenReturn("minio://calls/2025/01/test-call.wav");

        // When/Then
        mockMvc.perform(multipart("/api/calls/upload")
                .file("file", audioBytes)
                .param("callerId", "+1234567890")
                .param("agentId", "agent-001"))
            .andExpect(status().isOk());
    }
}
```

**Pros**:
- ✅ Fast execution (~10-20 seconds)
- ✅ No external dependencies
- ✅ Works in ANY CI/CD environment
- ✅ Tests business logic, not infrastructure
- ✅ Reliable and deterministic

**Cons**:
- ⚠️ Doesn't test actual MinIO integration
- ⚠️ Mocked behavior may differ from real MinIO

**Best For**: Unit tests, fast feedback loops, CI/CD pipelines

---

### Option 2: Use Testcontainers (Best for Integration Tests)

**Approach**: Automatically spin up MinIO in Docker during test execution.

**Implementation**:

Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>minio</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

Create test configuration:
```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CallIngestionIntegrationTest {

    @Container
    static MinIOContainer minioContainer = new MinIOContainer("minio/minio:latest")
        .withUserName("minioadmin")
        .withPassword("minioadmin");

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint", minioContainer::getS3URL);
        registry.add("minio.access-key", minioContainer::getUserName);
        registry.add("minio.secret-key", minioContainer::getPassword);
    }

    @Test
    void testRealMinIOIntegration() {
        // Tests run against real MinIO container
    }
}
```

**Pros**:
- ✅ Real integration testing with actual MinIO
- ✅ Automatic container lifecycle management
- ✅ Isolated test environment
- ✅ Tests actual MinIO behavior
- ✅ Works in CI/CD with Docker support

**Cons**:
- ⚠️ Slower execution (~1-2 minutes)
- ⚠️ Requires Docker on build agent
- ⚠️ More complex setup
- ⚠️ Requires Docker-in-Docker in CI/CD

**Best For**: Integration tests, verifying actual MinIO behavior, comprehensive testing

---

### Option 3: Start Infrastructure in CI/CD Pipeline

**Approach**: Run `docker-compose up` before tests in the pipeline.

**Implementation**:

```yaml
# azure-pipelines.yml
stages:
  - stage: Test
    jobs:
      - job: RunTests
        pool:
          vmImage: 'ubuntu-latest'
        steps:
          - task: DockerCompose@0
            displayName: 'Start Infrastructure'
            inputs:
              action: 'Run services'
              dockerComposeFile: 'docker-compose.yml'
              dockerComposeCommand: 'up -d minio kafka postgres'

          - task: Bash@3
            displayName: 'Wait for services to be healthy'
            inputs:
              targetType: 'inline'
              script: |
                sleep 30
                curl -f http://localhost:9000/minio/health/live || exit 1

          - task: Maven@3
            displayName: 'Run Tests'
            inputs:
              goals: 'test'

          - task: DockerCompose@0
            displayName: 'Stop Infrastructure'
            condition: always()
            inputs:
              action: 'Run services'
              dockerComposeCommand: 'down'
```

**Pros**:
- ✅ Tests against real infrastructure
- ✅ Most comprehensive testing
- ✅ Matches production environment

**Cons**:
- ❌ Slowest execution (~2-5 minutes)
- ❌ Complex pipeline configuration
- ❌ Requires Docker support in CI/CD
- ❌ Resource-intensive (CPU, memory, disk)
- ❌ Can be flaky (timing issues, port conflicts)

**Best For**: End-to-end tests, pre-deployment validation, nightly builds

---

## Recommendation

### Primary Solution: **Option 1 (Mock StorageService)**

**Rationale**:
1. **Fast CI/CD builds** - Critical for developer productivity
2. **No infrastructure dependencies** - Works everywhere
3. **Reliable** - No flaky tests due to Docker/timing issues
4. **Tests business logic** - Not testing MinIO's functionality

### Secondary Solution: **Option 2 (Testcontainers)**

**Use Case**: Create a **separate integration test suite** for MinIO:
- Name tests `*IntegrationTest.java` instead of `*Test.java`
- Run separately: `mvn verify` (not during `mvn test`)
- Optional in CI/CD (run nightly or pre-release)

### Hybrid Approach (Recommended)

```
Unit Tests (Option 1)          Integration Tests (Option 2)
├── Fast (~20 seconds)         ├── Comprehensive (~2 minutes)
├── Mocked dependencies        ├── Real MinIO via Testcontainers
├── Run on every commit        ├── Run before merge/deploy
└── *Test.java                 └── *IntegrationTest.java
```

**Maven Configuration**:
```xml
<!-- Unit tests: mvn test -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/*IntegrationTest.java</exclude>
        </excludes>
    </configuration>
</plugin>

<!-- Integration tests: mvn verify -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*IntegrationTest.java</include>
        </includes>
    </configuration>
</plugin>
```

---

## Action Items

### Immediate (Fix CI/CD)
1. ✅ Add `@MockBean` for `StorageService` in controller tests
2. ✅ Update test setup to mock MinIO interactions
3. ✅ Verify all tests pass: `mvn clean test`
4. ✅ Update CI/CD pipeline to run `mvn test`

### Short-term (Improve Test Coverage)
1. Create `CallIngestionIntegrationTest.java` with Testcontainers
2. Add to `mvn verify` phase (not `mvn test`)
3. Run integration tests before releases

### Long-term (Best Practices)
1. Document testing strategy in `TESTING.md`
2. Add integration test suite for all services
3. Configure CI/CD with separate unit/integration test stages
4. Add test coverage reporting (JaCoCo)

---

## References

### Related Documentation
- Spring Boot 4.0.0 Testing Guide: `/SPRING_BOOT_4_TESTING_GUIDE.md`
- Testcontainers Documentation: https://testcontainers.com/

### Related Issues
- Spring Boot 4.0.0 removed `@MockBean` - use `@Primary` beans or constructor injection
- See `SPRING_BOOT_4_TESTING_GUIDE.md` for Spring Boot 4 testing patterns

---

## Status

**Current**: ❌ **FAILING** (7 of 54 tests)
**Required**: ✅ **ALL TESTS PASSING** for CI/CD
**Timeline**: Fix within 1-2 hours (Option 1)
**Priority**: **HIGH** - Blocking CI/CD pipeline
