# DevOps and CI/CD Platform Options

**Date**: 2025-12-31
**Project**: Call Auditing Platform with Voice of the Customer (VoC)
**Purpose**: Evaluate software development workflow platforms with full DevOps and CI/CD capabilities

## Executive Summary

This document evaluates DevOps and CI/CD platform options for our event-driven microservices architecture built with Spring Boot (Java 21), Python FastAPI, Docker, and Kafka. The platform must support multi-language builds, container orchestration, and integration with our observability stack (OpenTelemetry, Prometheus, Grafana, Jaeger).

---

## Cloud-Native Platforms

### 1. GitHub Actions + GitHub Container Registry

**Best fit if**: Already using GitHub for source control

**Strengths**:
- Native Docker/container support
- Extensive marketplace with pre-built actions
- Free tier for public repositories
- Excellent caching for Maven and pip dependencies

**Technology Support**:
- **Java/Maven**: Built-in actions, dependency caching
- **Python**: Native support for pip, pytest, virtual environments
- **Docker**: Multi-stage builds, push to GHCR or DockerHub
- **Secrets**: GitHub Secrets with environment-based access control

**Cost**:
- Free for public repos
- ~$4/month per user for private repos
- 2,000 CI/CD minutes/month on free tier

**Integration with Our Stack**:
- Can build all 8 services (6 Spring Boot + 2 Python)
- Supports Testcontainers for Kafka/PostgreSQL integration tests
- Matrix builds for parallel service deployment

---

### 2. GitLab (gitlab.com or self-hosted)

**Best fit if**: Want all-in-one solution (Git + CI/CD + Registry + Kubernetes)

**Strengths**:
- Complete DevOps platform in single tool
- Built-in container registry (no DockerHub needed)
- Excellent Kubernetes integration with Helm chart deployments
- Auto DevOps feature detects Spring Boot/Python and generates pipelines

**Technology Support**:
- **Java/Maven**: Native support with caching
- **Python**: Built-in Python image templates
- **Docker**: Integrated registry, dependency proxy
- **Observability**: Native Prometheus/Grafana integration

**Cost**:
- Free tier available (5GB storage, 400 CI/CD minutes/month)
- Premium: $19/user/month
- Self-hosted: Free (requires own infrastructure)

**Integration with Our Stack**:
- Can deploy to Kubernetes with built-in CD
- Environment-based deployments (dev/staging/prod)
- Supports DAG pipelines for event-driven testing

---

### 3. Azure DevOps

**Best fit if**: Microsoft ecosystem, enterprise compliance needs

**Strengths**:
- Mature pipeline system (YAML + classic)
- Azure Artifacts for Maven/PyPI package management
- Works with any cloud provider (not Azure-locked)
- Strong Windows + Linux support

**Technology Support**:
- **Java/Maven**: First-class support
- **Python**: Native tasks for pip, pytest
- **Docker**: Azure Container Registry integration
- **Kafka**: Can run Docker Compose in pipelines

**Cost**:
- Free for up to 5 users with 1 parallel job
- Basic: $6/user/month
- 1,800 minutes/month on free tier

**Integration with Our Stack**:
- Can orchestrate multi-stage builds (build → test → deploy)
- Supports service connections for MinIO, Kafka, PostgreSQL
- Release pipelines for progressive deployment

---

### 4. Jenkins X (Cloud-Native Jenkins)

**Best fit if**: Kubernetes-first approach, need GitOps

**Strengths**:
- Built specifically for microservices on Kubernetes
- Tekton pipelines (cloud-native execution)
- Preview environments for PR testing
- GitOps with automatic syncing

**Technology Support**:
- **Java/Maven**: Buildpacks support
- **Python**: Cloud Native Buildpacks
- **Docker**: Native image building
- **Kubernetes**: First-class citizen

**Drawbacks**:
- Steeper learning curve than traditional Jenkins
- Requires Kubernetes cluster for CI/CD execution
- Smaller community than Jenkins classic

**Cost**: Free (self-hosted on Kubernetes)

**Integration with Our Stack**:
- Perfect for event-sourcing architecture testing
- Can spin up ephemeral Kafka clusters per PR
- Automatic rollback on failures

---

## Self-Hosted Options

### 5. Jenkins (Traditional)

**Best fit if**: Maximum customization, existing Jenkins expertise

**Strengths**:
- Massive plugin ecosystem (1,800+ plugins)
- Complete control over build environment
- Can run anywhere (VM, Docker, Kubernetes)
- Shared libraries for reusable pipeline code

**Technology Support**:
- **Java/Maven**: Maven Integration Plugin, JaCoCo coverage
- **Python**: Python plugin, pytest integration
- **Docker**: Docker Pipeline plugin, Kubernetes plugin
- **Kafka**: Custom stages for Kafka topic setup/teardown

**Drawbacks**:
- Requires significant maintenance (plugin updates, security)
- Can become complex with many jobs
- UI/UX less modern than newer platforms

**Cost**: Free (open source)

**Integration with Our Stack**:
- Can orchestrate complex test scenarios (spin up Kafka, run tests, teardown)
- Supports parallel builds for all 8 services
- Integration with OpenTelemetry for pipeline observability

---

### 6. Drone CI

**Best fit if**: Want lightweight, container-native CI

**Strengths**:
- Everything runs in Docker containers (isolated builds)
- Simple YAML configuration (.drone.yml)
- Minimal resource footprint vs Jenkins
- Plugin ecosystem for common tasks

**Technology Support**:
- **Java/Maven**: Docker Maven image
- **Python**: Docker Python image
- **Docker**: Native Docker-in-Docker support
- **Multi-stage**: Can chain services for testing

**Cost**:
- Free (open source version)
- Drone Cloud: Managed service available

**Integration with Our Stack**:
- Perfect for Docker Compose testing workflows
- Can test full stack (Kafka + services) in pipeline
- Secrets management for MinIO/database credentials

---

### 7. Gitea + Drone/Woodpecker CI

**Best fit if**: Want fully self-hosted GitHub alternative

**Strengths**:
- Lightweight Git server (Gitea) + CI/CD (Woodpecker)
- No external dependencies or SaaS lock-in
- Minimal resource usage (~200MB RAM for Gitea)
- GitHub-compatible API

**Technology Support**:
- **Java/Maven**: Via Docker containers
- **Python**: Via Docker containers
- **Docker**: Native support
- **Webhooks**: Trigger on push/PR

**Cost**: Free (both open source)

**Integration with Our Stack**:
- Can run entirely on-premise
- Integrates with existing Docker Compose setup
- Suitable for air-gapped environments

---

## Kubernetes-Focused Platforms

### 8. ArgoCD + Tekton Pipelines

**Best fit if**: Deploying to Kubernetes, want GitOps

**Strengths**:
- **ArgoCD**: Declarative GitOps continuous delivery
- **Tekton**: Cloud-native CI pipeline execution
- Both are CNCF projects (vendor-neutral)
- Automatic drift detection and remediation

**Technology Support**:
- **Java/Maven**: Tekton tasks for Maven builds
- **Python**: Tekton Python tasks
- **Docker**: Kaniko for in-cluster image builds
- **Helm**: ArgoCD native Helm support

**Cost**: Free (CNCF open source projects)

**Integration with Our Stack**:
- Each microservice deployed independently via ArgoCD
- Tekton pipelines for event-driven testing (Kafka → service → assertions)
- Progressive delivery (canary deployments for services)

---

### 9. Flux CD + GitHub Actions

**Best fit if**: GitOps with simpler setup than ArgoCD

**Strengths**:
- Simpler architecture than ArgoCD
- Automatic image updates from registry
- Native support for Helm, Kustomize, plain YAML
- Notification system (Slack, Teams)

**Technology Support**:
- **CI (GitHub Actions)**: Build images, push to registry
- **CD (Flux)**: Monitor registry, deploy to Kubernetes
- **Secrets**: Sealed Secrets or SOPS encryption

**Cost**: Free (GitHub Actions minutes + Flux open source)

**Integration with Our Stack**:
- GitHub Actions builds services → pushes to GHCR
- Flux detects new images → updates Kubernetes deployments
- GitOps workflow for infrastructure changes

---

## Hybrid/Specialized Options

### 10. CircleCI

**Best fit if**: Want fast builds, Docker-first workflows

**Strengths**:
- Docker Layer Caching (DLC) for fast rebuilds
- Orbs (reusable configuration packages)
- Excellent parallelism support
- Can run Docker Compose in pipelines

**Technology Support**:
- **Java/Maven**: Maven orb with dependency caching
- **Python**: Python orb with pip caching
- **Docker**: Native Docker executor
- **Multi-service**: Docker Compose support

**Cost**:
- Free tier: 6,000 build minutes/month
- Performance plan: $15/month (25,000 credits)
- Pay-as-you-go available

**Integration with Our Stack**:
- Can run full integration tests (Kafka + services)
- Workflows for complex build/test/deploy pipelines
- Test splitting for faster feedback

---

### 11. JetBrains TeamCity

**Best fit if**: Using IntelliJ/JetBrains IDEs, complex build chains

**Strengths**:
- Excellent Java/Kotlin support (JetBrains product)
- Advanced build caching and artifact dependencies
- Build chains (dependencies between builds)
- IDE integration for debugging pipelines

**Technology Support**:
- **Java/Maven**: First-class support, smart caching
- **Python**: Python runner
- **Docker**: Docker Compose runner
- **Kotlin DSL**: Type-safe pipeline configuration

**Cost**:
- Free for up to 3 build agents and 100 build configurations
- Professional server: $1,999/year (perpetual fallback license)

**Integration with Our Stack**:
- Can model service dependencies as build chains
- Test history tracking across microservices
- Parallel builds for all services

---

## Recommendations Based on Our Project

### For Getting Started Quickly
**→ GitHub Actions**

**Rationale**: If your code is already on GitHub, this is the fastest path to CI/CD. Minimal configuration, excellent documentation, and free tier is sufficient for most projects.

**Sample Workflow**:
```yaml
# .github/workflows/build.yml
name: Build and Test
on: [push, pull_request]
jobs:
  build-java-services:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [call-ingestion, voc, audit, analytics, notification, monitor]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
      - name: Build ${{ matrix.service }}-service
        run: |
          cd ${{ matrix.service }}-service
          ./mvnw clean package
```

---

### For Complete Self-Hosted Solution
**→ GitLab (self-hosted)**

**Rationale**: All-in-one platform eliminates need for separate tools (Git, CI/CD, container registry, Kubernetes integration). Single maintenance point.

**Benefits for Our Stack**:
- Built-in container registry (no DockerHub limits)
- Auto DevOps can detect Spring Boot services
- Integrated with Kubernetes (kubectl, Helm)
- LDAP/SAML integration for enterprise auth

**Deployment**:
```bash
# docker-compose.yml (add to existing)
gitlab:
  image: gitlab/gitlab-ce:latest
  ports:
    - "80:80"
    - "443:443"
    - "2222:22"
  volumes:
    - gitlab-config:/etc/gitlab
    - gitlab-logs:/var/log/gitlab
    - gitlab-data:/var/opt/gitlab
```

---

### For Kubernetes Production Deployment
**→ GitHub Actions (build) + ArgoCD (deploy)**

**Rationale**: Industry standard GitOps approach. GitHub Actions handles CI (build/test), ArgoCD handles CD (deployment). Clear separation of concerns.

**Architecture**:
1. **CI (GitHub Actions)**:
   - Build Docker images for 8 services
   - Run unit tests, integration tests
   - Push images to GitHub Container Registry (GHCR)
   - Update Helm chart values with new image tags

2. **CD (ArgoCD)**:
   - Monitor Git repository for Helm chart changes
   - Automatically sync to Kubernetes cluster
   - Health checks for each service
   - Rollback on failures

**Benefits for Event-Driven Architecture**:
- Can deploy services independently (e.g., only update sentiment-service)
- Kafka topics/configuration managed as Kubernetes resources
- Observability stack (Prometheus, Grafana, Jaeger) deployed via GitOps

---

### For Maximum Flexibility
**→ Jenkins + Docker + Kubernetes**

**Rationale**: Most customizable but requires DevOps expertise. Ideal if you have complex requirements (e.g., custom Kafka testing frameworks, proprietary deployment logic).

**Use Cases**:
- Custom integration test orchestration (spin up Kafka, seed data, run tests, collect metrics)
- Multi-cloud deployments (deploy to AWS + Azure simultaneously)
- Complex approval workflows (QA → staging → production with manual gates)

**Sample Jenkinsfile**:
```groovy
pipeline {
  agent any
  stages {
    stage('Build Services') {
      parallel {
        stage('Java Services') {
          steps {
            sh './build-java-services.sh'
          }
        }
        stage('Python Services') {
          steps {
            sh './build-python-services.sh'
          }
        }
      }
    }
    stage('Integration Tests') {
      steps {
        sh 'docker compose -f docker-compose.test.yml up --abort-on-container-exit'
      }
    }
    stage('Deploy to Kubernetes') {
      steps {
        kubernetesDeploy(configs: 'k8s/*.yml', kubeconfigId: 'kubeconfig')
      }
    }
  }
}
```

---

## Key Considerations for Our Stack

### 1. Multi-Language Support
**Requirement**: Platform must handle Java (Maven) + Python (pip) builds in same pipeline

**Solutions**:
- **GitHub Actions**: Matrix builds with different runners
- **GitLab**: Multi-stage pipelines with different Docker images
- **Jenkins**: Multi-agent builds (Maven agent, Python agent)

### 2. Docker Registry
**Requirement**: Need storage for 8 service images (6 Java + 2 Python)

**Options**:
- **DockerHub**: Free tier limited to 6 months retention, rate limits
- **GitHub Container Registry (GHCR)**: Free for public repos, unlimited private storage with paid plan
- **GitLab Registry**: Included with self-hosted GitLab
- **Self-hosted Harbor**: Open source registry with vulnerability scanning

**Recommendation**: GHCR for GitHub users, GitLab Registry for self-hosted

### 3. Integration Testing
**Requirement**: Must support Testcontainers for Kafka/PostgreSQL tests

**Challenges**:
- Requires Docker-in-Docker (DinD) or Docker socket mounting
- Testcontainers needs to pull images during tests

**Solutions**:
- **GitHub Actions**: Use `docker` service container
- **GitLab**: Enable Docker-in-Docker with `docker:dind` service
- **Jenkins**: Use Docker agents with mounted Docker socket

**Example (GitLab)**:
```yaml
test:
  image: maven:3.9-eclipse-temurin-21
  services:
    - docker:24-dind
  variables:
    DOCKER_HOST: tcp://docker:2375
  script:
    - mvn test
```

### 4. Kafka Deployment
**Requirement**: Consider how to deploy/upgrade Kafka in production

**Options**:
- **Strimzi Operator** (Kubernetes): Manage Kafka clusters as CRDs
- **Confluent for Kubernetes**: Commercial Kafka operator
- **Helm Chart**: Bitnami Kafka chart for manual management

**CI/CD Integration**:
- **GitOps (ArgoCD/Flux)**: Manage Kafka cluster configuration in Git
- **Terraform**: Provision Kafka infrastructure, deploy via CI/CD
- **Ansible**: Configuration management for Kafka brokers

### 5. Secrets Management
**Requirement**: Securely store MinIO keys, database passwords, OpenAI API keys

**Solutions**:
- **GitHub Actions**: GitHub Secrets (encrypted, environment-scoped)
- **GitLab**: CI/CD Variables (protected, masked)
- **Kubernetes**: Sealed Secrets or External Secrets Operator (ESO)
- **HashiCorp Vault**: Enterprise secret management (integrates with all platforms)

**Best Practice**:
```yaml
# .github/workflows/deploy.yml
env:
  MINIO_ACCESS_KEY: ${{ secrets.MINIO_ACCESS_KEY }}
  MINIO_SECRET_KEY: ${{ secrets.MINIO_SECRET_KEY }}
  POSTGRES_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}
  OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
```

### 6. Observability Integration
**Requirement**: Platform should work with OpenTelemetry/Prometheus stack

**Integrations**:
- **Pipeline Metrics**: Export build duration, success rate to Prometheus
- **Deployment Events**: Send deployment events to Jaeger as traces
- **Alerting**: Integrate with Grafana for CI/CD dashboards

**Example (Prometheus metric export)**:
```bash
# Post build metrics to Pushgateway
echo "build_duration_seconds{service=\"call-ingestion\"} $DURATION" | \
  curl --data-binary @- http://pushgateway:9091/metrics/job/cicd
```

---

## Decision Matrix

| Platform | Setup Complexity | Maintenance | Multi-Language | K8s Support | Cost (Small Team) | Best For |
|----------|------------------|-------------|----------------|-------------|-------------------|----------|
| GitHub Actions | ⭐⭐ Easy | ⭐⭐ Low | ✅ Excellent | ⭐⭐ Good | Free - $20/mo | Quick start, GitHub users |
| GitLab | ⭐⭐⭐ Moderate | ⭐⭐⭐ Medium | ✅ Excellent | ✅ Excellent | Free - $95/mo | All-in-one, self-hosted |
| Jenkins | ⭐⭐⭐⭐ Complex | ⭐⭐⭐⭐ High | ✅ Excellent | ⭐⭐⭐ Good | Free | Maximum control |
| ArgoCD + Tekton | ⭐⭐⭐⭐ Complex | ⭐⭐⭐ Medium | ⭐⭐⭐ Good | ✅ Excellent | Free | K8s-native GitOps |
| CircleCI | ⭐⭐ Easy | ⭐ Low | ✅ Excellent | ⭐⭐ Good | Free - $15/mo | Docker-first builds |
| Azure DevOps | ⭐⭐ Easy | ⭐⭐ Low | ✅ Excellent | ⭐⭐⭐ Good | Free - $30/mo | Enterprise, compliance |

**Legend**: ⭐ = Complexity/Effort level (more stars = more complex)

---

## Migration Path Recommendations

### Phase 1: Basic CI (Weeks 1-2)
**Goal**: Automated builds and tests on every commit

**Implementation**:
1. Choose platform: GitHub Actions (recommended for speed)
2. Create workflow files for:
   - Java services: `./mvnw clean package`
   - Python services: `pip install -r requirements.txt && pytest`
3. Add Docker build steps
4. Set up code quality gates (test coverage, linting)

**Deliverables**:
- All 8 services building automatically
- Unit tests running on every PR
- Docker images built (not yet deployed)

### Phase 2: Integration Testing (Weeks 3-4)
**Goal**: Test services together with Kafka, PostgreSQL, MinIO

**Implementation**:
1. Add Testcontainers tests to each service
2. Configure Docker-in-Docker for CI environment
3. Create integration test stage in pipeline
4. Add test data fixtures (sample audio files, events)

**Deliverables**:
- End-to-end event flow tests (CallReceived → CallTranscribed → SentimentAnalyzed)
- Kafka message validation tests
- Performance benchmarks (transcription time, event latency)

### Phase 3: Container Registry & Artifacts (Week 5)
**Goal**: Store and version Docker images

**Implementation**:
1. Set up container registry (GHCR, GitLab Registry, or Harbor)
2. Tag images with commit SHA and semantic version
3. Implement image scanning (Trivy, Grype)
4. Configure retention policies

**Deliverables**:
- All images pushed to registry on main branch
- Image vulnerability reports
- Automated cleanup of old images

### Phase 4: Continuous Deployment (Weeks 6-8)
**Goal**: Automatically deploy to staging/production

**Implementation**:
1. Set up Kubernetes cluster (or continue with Docker Compose for staging)
2. Implement deployment automation:
   - **GitOps**: ArgoCD monitors Git, deploys automatically
   - **Push-based**: CI pipeline runs kubectl apply
3. Add deployment validation (health checks, smoke tests)
4. Implement rollback mechanism

**Deliverables**:
- Automated deployment to staging on main branch merge
- Manual approval gate for production
- Rollback capability

### Phase 5: Advanced Features (Ongoing)
**Goal**: Optimize and enhance pipeline

**Features**:
- **Caching**: Speed up Maven/pip dependency downloads
- **Parallelization**: Run independent service tests concurrently
- **Preview Environments**: Spin up full stack for each PR
- **Performance Testing**: Load tests with JMeter/k6
- **Security Scanning**: SAST (SonarQube), DAST, secrets detection
- **Notifications**: Slack/Teams alerts for build failures

---

## Recommended Starter Configuration

### GitHub Actions (Recommended for Quick Start)

**File Structure**:
```
.github/
└── workflows/
    ├── build-java-services.yml    # Build all Spring Boot services
    ├── build-python-services.yml  # Build FastAPI services
    ├── integration-tests.yml      # End-to-end tests with Kafka
    └── deploy.yml                 # Deploy to staging/production
```

**build-java-services.yml**:
```yaml
name: Build Java Services

on:
  push:
    branches: [main, develop]
  pull_request:
    paths:
      - '*-service/**'
      - '!transcription-service/**'
      - '!sentiment-service/**'

jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - call-ingestion-service
          - voc-service
          - audit-service
          - analytics-service
          - notification-service
          - monitor-service

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Build with Maven
        working-directory: ./${{ matrix.service }}
        run: ./mvnw clean package -DskipTests

      - name: Run Unit Tests
        working-directory: ./${{ matrix.service }}
        run: ./mvnw test

      - name: Build Docker Image
        working-directory: ./${{ matrix.service }}
        run: |
          docker build -t ghcr.io/${{ github.repository }}/${{ matrix.service }}:${{ github.sha }} .

      - name: Push to GHCR
        if: github.ref == 'refs/heads/main'
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          docker push ghcr.io/${{ github.repository }}/${{ matrix.service }}:${{ github.sha }}
```

**integration-tests.yml**:
```yaml
name: Integration Tests

on:
  pull_request:
  push:
    branches: [main]

jobs:
  test-event-flow:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Start Infrastructure
        run: |
          docker compose up -d kafka postgres minio valkey
          sleep 30  # Wait for Kafka to be ready

      - name: Run Integration Tests
        run: |
          cd call-ingestion-service
          ./mvnw verify -Pintegration-tests

      - name: Cleanup
        if: always()
        run: docker compose down -v
```

---

## Cost Comparison (5-person team, private repos)

| Platform | Monthly Cost | Included | Additional Costs |
|----------|--------------|----------|------------------|
| GitHub Actions | $20 | 3,000 minutes | $0.008/min overage |
| GitLab SaaS (Premium) | $95 | 10,000 CI/CD minutes | $0.50/min overage |
| GitLab Self-Hosted | $0* | Unlimited | *Infrastructure costs |
| CircleCI | $15 | 25,000 credits | Pay-as-you-go |
| Azure DevOps | $30 | Unlimited minutes (1 parallel job) | $40/parallel job |
| Jenkins (self-hosted) | $0* | Unlimited | *Infrastructure + maintenance |

**Notes**:
- GitHub Actions overage unlikely for small projects (3,000 min ≈ 100 builds/day @ 30 min each)
- Self-hosted options require server costs (e.g., $40-100/mo for 4 vCPU, 16GB RAM VM)
- Enterprise pricing available for all platforms (volume discounts)

---

## Next Steps

1. **Decision**: Choose platform based on:
   - Current Git hosting (GitHub → GitHub Actions, GitLab → GitLab CI)
   - Kubernetes requirement (yes → ArgoCD + Tekton, no → GitHub Actions/GitLab)
   - Self-hosted requirement (yes → GitLab/Jenkins, no → GitHub Actions)

2. **Proof of Concept**: Implement Phase 1 (Basic CI) for 1-2 services
   - Validate build times, Docker image sizes
   - Test Testcontainers compatibility
   - Measure CI/CD pipeline execution time

3. **Rollout**: Incrementally add remaining services
   - Monitor resource usage (build minutes, storage)
   - Gather team feedback on developer experience
   - Optimize caching and parallelization

4. **Production Hardening**: Add security, monitoring, compliance
   - Secrets management (Vault, Sealed Secrets)
   - Audit logging (who deployed what, when)
   - Compliance gates (security scans, approval workflows)

---

## Appendix: Event-Driven Testing Strategy

Given our event-sourcing architecture, the CI/CD pipeline should validate:

1. **Event Schema Validation**:
   - JSON Schema validation for all events
   - Backward compatibility checks (Confluent Schema Registry)

2. **Event Flow Testing**:
   ```
   Publish CallReceived → Assert CallTranscribed emitted → Assert SentimentAnalyzed emitted
   ```

3. **Saga Pattern Testing**:
   - Test compensating transactions (e.g., transcription failure → rollback)

4. **Idempotency Testing**:
   - Publish duplicate event → Assert no duplicate processing

5. **Event Replay Testing**:
   - Replay historical events → Assert system state matches expected

**Implementation**:
- Use Testcontainers for Kafka
- Create test fixtures (JSON event files in `test/resources/events/`)
- Write assertions using Awaitility for async event processing

**Example Test**:
```java
@SpringBootTest
@ActiveProfiles("test")
class CallIngestionEventFlowTest {

    @Test
    void whenCallReceived_thenTranscriptionEventEmitted() {
        // Given
        String callId = UUID.randomUUID().toString();

        // When
        callIngestionService.uploadCall(callId, audioData);

        // Then
        await().atMost(30, SECONDS).untilAsserted(() -> {
            ConsumerRecord<String, String> record = kafkaTestConsumer.poll("calls.received");
            assertThat(record.key()).isEqualTo(callId);
            assertThat(record.value()).contains("CallReceived");
        });
    }
}
```

---

**Document Version**: 1.0
**Last Updated**: 2025-12-31
**Owner**: Infrastructure Team
**Review Date**: Q2 2026
