# Contract Tests Failure - Root Cause Analysis

**Status**: ✅ RESOLVED
**Date**: 2025-12-31
**Service**: call-ingestion-service
**Resolution Date**: 2025-12-31

## Executive Summary

The contract tests failed due to a **known incompatibility** between Testcontainers 1.19.3 and Docker 29.1.3. This was NOT a code issue - it was a version compatibility problem.

**Root Cause**: Testcontainers 1.19.3 uses Docker Java Client 3.3.4, which uses Docker API version 1.32. Docker 29+ requires minimum API version 1.44.

**Solution**: Upgrade to Testcontainers 2.0.3 or later.

**Resolution**: ✅ Upgraded to Testcontainers 2.0.3 - all contract tests now passing.

## Detailed Investigation

### Environment Information

| Component | Version | API Version | Status |
|-----------|---------|-------------|--------|
| Docker Desktop | 4.55.0 (213807) | - | ✅ Running |
| Docker Engine | 29.1.3 | 1.52 (min: 1.44) | ✅ Working |
| Testcontainers | 1.19.3 | - | ❌ Incompatible |
| docker-java client | 3.3.4 | 1.32 | ❌ Too Old |

### Evidence

#### 1. Docker is Working Properly

**Command**: `docker info`
```bash
Server:
 Containers: 15
  Running: 9
 Images: 19
 Server Version: 29.1.3
 API version:      1.52 (minimum version 1.44)
```

**Result**: ✅ Docker daemon is healthy and responding correctly

**Command**: `curl --unix-socket /var/run/docker.sock http://localhost/v1.52/info`
```json
{
  "ID": "659982b7-d188-4bb0-9313-62f4308044eb",
  "Containers": 15,
  "ContainersRunning": 9,
  "ServerVersion": "29.1.3",
  ...
}
```

**Result**: ✅ Docker API v1.52 endpoint works perfectly

#### 2. Testcontainers Failure

**Error**:
```
java.lang.IllegalStateException: Could not find a valid Docker environment
Caused by: BadRequestException (Status 400)
```

**Attempted Strategies**:
1. UnixSocketClientProviderStrategy - ❌ Failed with 400 Bad Request
2. DockerDesktopClientProviderStrategy - ❌ Failed with 400 Bad Request

**Response from Docker**:
```json
{
  "ID": "",
  "Containers": 0,
  "Images": 0,
  "ServerVersion": "",
  ...all fields empty or zero...
  "Labels": ["com.docker.desktop.address=unix:///Users/jon/Library/Containers/com.docker.docker/Data/docker-cli.sock"]
}
```

**Analysis**: Docker returns an almost-empty info response (only Labels field populated). This happens when the Docker API client uses an incompatible API version.

#### 3. Known Issues Confirmed

**GitHub Issues**:
- [Issue #11212](https://github.com/testcontainers/testcontainers-java/issues/11212): "Docker 29.0.0 could not find a valid Docker environment"
- [Issue #11235](https://github.com/testcontainers/testcontainers-java/issues/11235): "Docker engine 29 (docker 4.52) is no longer compatible v1.21"
- [Spring Boot Issue #48104](https://github.com/spring-projects/spring-boot/issues/48104): "Testcontainers integration fails on Docker 29.0.0"

**Findings**:
- Docker 29 introduced breaking API changes
- Minimum supported API version increased from 1.32 to 1.44
- Testcontainers 1.x versions are NOT compatible with Docker 29
- Testcontainers 2.0.2+ fixed the compatibility issue

### Root Cause Chain

```
Testcontainers 1.19.3
    └─> docker-java-api 3.3.4
        └─> Uses Docker API v1.32
            └─> Docker 29.1.3 rejects API v1.32 (min: 1.44)
                └─> Returns HTTP 400 with empty info response
                    └─> Testcontainers throws IllegalStateException
```

## Why This Happened

### Docker Version Timeline

- **December 2023**: Testcontainers 1.19.3 released
  - Compatible with Docker API v1.32
  - Works with Docker up to v28.x

- **December 2024**: Docker 29.0.0 released
  - Minimum API version increased to 1.44
  - Breaking change for older clients

- **December 2025**: Docker 29.1.3 (current)
  - API version 1.52
  - Not compatible with Testcontainers 1.x

### Docker API Version Incompatibility

**What Docker 29 expects**:
```
Minimum API version: 1.44
Current API version: 1.52
```

**What Testcontainers 1.19.3 provides**:
```
docker-java 3.3.4 API version: 1.32
```

**Result**: `1.32 < 1.44` → Docker rejects the request with HTTP 400

## The Fix

### Solution 1: Upgrade Testcontainers to 2.0.3 (RECOMMENDED)

**Why this works**: Testcontainers 2.x uses a newer docker-java client that supports Docker API v1.44+

**Implementation**:

1. Update `pom.xml` dependencies:

```xml
<!-- Replace all testcontainers dependencies -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>2.0.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>2.0.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>2.0.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <version>2.0.3</version>
    <scope>test</scope>
</dependency>
```

**Better approach using BOM**:

```xml
<dependencyManagement>
    <dependencies>
        <!-- Testcontainers BOM -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>2.0.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Then remove version tags from testcontainers dependencies -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>kafka</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Migration Notes**:
- Testcontainers 2.x has some breaking changes from 1.x
- Most code should work as-is, but verify after upgrade
- Check [Testcontainers 2.x migration guide](https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.0)

### Solution 2: Downgrade Docker to 28.x (NOT RECOMMENDED)

**Why NOT recommended**:
- Security vulnerabilities in older Docker versions
- Missing new features
- Not a long-term solution

**How to check Docker version**:
```bash
docker version
```

**How to downgrade** (macOS):
1. Download Docker Desktop 4.51 (Docker Engine 28.5.2) from Docker archives
2. Install older version
3. Disable auto-updates

### Solution 3: Pin Docker API Version (WORKAROUND)

**For Testcontainers 2.x only**:

Create `src/test/resources/testcontainers.properties`:
```properties
docker.api.version=1.44
```

This forces Testcontainers to use API version 1.44 instead of negotiating.

**Note**: This only works with Testcontainers 2.x, NOT 1.x

## Verification Steps

After upgrading to Testcontainers 2.0.3:

1. **Clean rebuild**:
   ```bash
   ./mvnw clean verify -Pintegration
   ```

2. **Expected result**:
   - Unit tests: 54 pass
   - Contract tests: 3 pass (downloads Docker images on first run)
   - Total time: ~2 minutes first run, ~30 seconds subsequent runs

3. **Verify Docker images**:
   ```bash
   docker images | grep testcontainers
   ```

## Impact Assessment

### What Still Works ✅

- `mvn test` - 54 unit/integration tests pass (no Docker needed)
- CI/CD pipeline - Unaffected, contract tests excluded by default
- All service code - No code changes required
- Docker commands - `docker ps`, `docker-compose up`, etc. all work

### What's Broken ❌

- `mvn verify -Pintegration` - Contract tests fail immediately
- Local contract testing - Cannot test with real infrastructure
- Testcontainers usage - Any test using @Testcontainers fails

### Business Impact

- **CI/CD**: ✅ No impact (contract tests are excluded)
- **Development**: ⚠️ Cannot run comprehensive local integration tests
- **Code Quality**: ⚠️ Cannot verify real infrastructure integration locally
- **Production**: ✅ No impact (Testcontainers is test-only dependency)

## Related Issues

This issue affects ANY project using:
- Testcontainers 1.x + Docker 29+
- Spring Boot 3.3.x or earlier with embedded Testcontainers support + Docker 29+

**Check your project**:
```bash
# Check Testcontainers version
grep -A 2 "testcontainers" pom.xml | grep version

# Check Docker version
docker version | grep "Server.*Version"
```

**If you see**:
- Testcontainers < 2.0.0 AND Docker >= 29.0.0
- **You have this issue!**

## Recommended Action

**Upgrade Testcontainers to 2.0.3**:

1. Update `pom.xml` with Testcontainers BOM
2. Run `./mvnw clean verify -Pintegration`
3. Fix any breaking changes (unlikely for our simple usage)
4. Verify all tests pass
5. Commit changes

**Estimated effort**: 30 minutes

**Risk**: Low (Testcontainers is test-only dependency)

## References

### Official Documentation
- [Testcontainers 2.0.3 Release](https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.3)
- [Maven Central - Testcontainers](https://mvnrepository.com/artifact/org.testcontainers/testcontainers)
- [Testcontainers Java Documentation](https://java.testcontainers.org/)

### Known Issues
- [GitHub Issue #11212: Docker 29.0.0 compatibility](https://github.com/testcontainers/testcontainers-java/issues/11212)
- [GitHub Issue #11235: Docker engine 29 incompatibility](https://github.com/testcontainers/testcontainers-java/issues/11235)
- [Spring Boot Issue #48104: Testcontainers integration fails](https://github.com/spring-projects/spring-boot/issues/48104)

### Docker Release Information
- [Docker Engine v29 Release Blog](https://www.docker.com/blog/docker-engine-version-29/)
- [Docker API Version History](https://docs.docker.com/engine/api/)

## Conclusion

**Root Cause**: Version incompatibility between Testcontainers 1.19.3 (uses Docker API v1.32) and Docker 29.1.3 (requires Docker API v1.44+)

**Solution**: Upgrade Testcontainers from 1.19.3 to 2.0.3

**Status**: ✅ **RESOLVED** (2025-12-31)

**Completed Steps**:
1. ✅ Updated `pom.xml` with Testcontainers 2.0.3
2. ✅ Verified contract tests pass with `mvn verify -Pintegration`
3. ✅ Documented upgrade in project changelog (call-ingestion-service/CHANGELOG.md)

**Results**:
- All 3 contract tests passing
- PostgreSQL, MinIO, and Kafka containers start successfully via Testcontainers
- Docker 29.1.3 compatibility confirmed
- No code changes required - only dependency upgrade
