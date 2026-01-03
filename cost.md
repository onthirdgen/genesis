
## Cost Analysis by Development Phase

### Phase 1: Prototyping (Lowest Cost - **$0/month**)

**Recommended Stack:**

| Component | Solution | Cost | Justification |
|---|---|---|---|
| **CI/CD Platform** | Azure DevOps Free Tier | $0 | Supports exactly 5 users free, 1,800 CI/CD minutes/month [1][2] |
| **Version Control** | Azure Repos (included) | $0 | Unlimited private Git repos included [3] |
| **Kubernetes** | k3d (K3s in Docker) locally | $0 | Lightweight (512MB RAM), fast spin-up, production-realistic [4][5][6] |
| **Kafka** | Single-node Kafka on k3d | $0 | Local development cluster |
| **PostgreSQL** | PostgreSQL container on k3d | $0 | Local containerized instance |
| **Grafana/OTEL** | Community editions on k3d | $0 | Self-hosted observability stack |
| **MinIO** | Local MinIO on k3d | $0 | S3-compatible storage, no cloud egress |
| **Speech-to-Text** | Distil-Whisper (CPU) | $0 | Open-source, runs on CPU without GPU costs [7][8] |
| **Hosting** | Developer laptops only | $0 | No cloud infrastructure needed |

**Total Monthly Cost: $0**

**Prototyping Workflow:**
- All 5 team members work on local k3d clusters with full infrastructure stack
- Azure DevOps handles code reviews, CI builds (1,800 minutes = ~30 hours/month)
- Use self-hosted runners on developer machines for unlimited build minutes [3]
- Shared dev environment on a single spare machine running k3s if needed

**Limitations:**
- No external access for stakeholder demos
- Limited to 1,800 CI/CD minutes on Microsoft-hosted agents
- Manual infrastructure management

***

### Phase 2: MVP Testing (**~$20-30/month**)

**Recommended Stack:**

| Component | Solution | Monthly Cost |
|---|---|---|---|
| **CI/CD** | Azure DevOps Free Tier + 1 self-hosted agent | $0 (use own VM) |
| **Cloud Hosting** | Azure App Service Free (F1) tier | $0 |
| **OR Lightweight VM** | Azure B1s (1 vCPU, 1GB RAM) running k3s | ~$10-15 |
| **PostgreSQL** | Azure Database for PostgreSQL (1 vCore Basic) | ~$15-20 |
| **Storage (MinIO)** | Azure Blob Storage (5GB free tier) | $0-5 (first 5GB free) [9] |
| **Kafka/Grafana/OTEL** | Self-hosted on B1s VM | Included in VM cost |
| **Speech-to-Text** | Distil-Whisper on VM | $0 (open-source) |

**Total: $20-30/month**

**When to choose this:**
- Need external URL for stakeholder demos
- QA requires stable test environment
- Team wants to validate deployment automation before production

***

### Phase 3: Pre-Production (**~$150-200/month**)

**Recommended Stack:**

| Component | Solution | Monthly Cost |
|---|---|---|---|
| **CI/CD** | Azure DevOps Free Tier (still under 5 users) | $0 |
| **Kubernetes** | AKS Free tier (control plane) | $0 |
| **Worker Nodes** | 2x B2s VMs (2 vCPU, 4GB each) | ~$70-80 |
| **PostgreSQL** | Azure Database for PostgreSQL Flexible (2 vCore) | ~$60-80 [10] |
| **Kafka** | Strimzi on AKS (3-node cluster) | Included in node costs |
| **Load Balancer** | Azure Standard LB | ~$20-25 |
| **Storage (MinIO)** | 50GB Azure Blob Storage | ~$5-10 |
| **Observability** | Grafana + OTEL on AKS | Included in node costs |
| **Speech-to-Text** | Whisper Large V3 Turbo (GPU optional) | $0 (open-source) |

**Total: $150-200/month**

**Key Features:**
- Production-like architecture with HA capabilities
- Separate staging environment on same cluster (namespace isolation)
- Auto-scaling support for load testing
- Managed PostgreSQL with automated backups

***

