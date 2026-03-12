# LogStream — DevOps Documentation

> Author: DevOps Engineer — NSP 25/26 Team 13  
> AWS Account: `205930639565` | Region: `eu-west-1`  
> Last updated: 2026

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Repository Structure](#2-repository-structure)
3. [Terraform State Backend](#3-terraform-state-backend)
4. [Terraform Workflow](#4-terraform-workflow)
5. [Module Deep-Dive](#5-module-deep-dive)
   - 5.1 [VPC & Networking](#51-vpc--networking)
   - 5.2 [ECR](#52-ecr)
   - 5.3 [Secrets Manager](#53-secrets-manager)
   - 5.4 [RDS (PostgreSQL 16)](#54-rds-postgresql-16)
   - 5.5 [IAM](#55-iam)
   - 5.6 [ALB](#56-alb)
   - 5.7 [ECS](#57-ecs)
   - 5.8 [Bastion Host](#58-bastion-host)
   - 5.9 [CodeDeploy](#59-codedeploy)
   - 5.10 [EventBridge Scheduler](#510-eventbridge-scheduler)
6. [CI/CD Pipeline](#6-cicd-pipeline)
7. [Environment Differences (Dev vs Prod)](#7-environment-differences-dev-vs-prod)
8. [Operational Runbooks](#8-operational-runbooks)
   - 8.1 [Connect to RDS via SSH Tunnel](#81-connect-to-rds-via-ssh-tunnel)
   - 8.2 [Push Docker Images to ECR Manually](#82-push-docker-images-to-ecr-manually)
   - 8.3 [Trigger a CodeDeploy Deployment Manually](#83-trigger-a-codedeploy-deployment-manually)
   - 8.4 [Trigger an EventBridge Task Manually](#84-trigger-an-eventbridge-task-manually)
   - 8.5 [Check ECS Task Logs](#85-check-ecs-task-logs)
   - 8.6 [Run Terraform for dev](#86-run-terraform-for-dev)
   - 8.7 [Run Terraform for prod](#87-run-terraform-for-prod)
9. [Technical Decisions & Fixes](#9-technical-decisions--fixes)
10. [Database Partitioning](#10-database-partitioning)
11. [Known Issues & Pending Work](#11-known-issues--pending-work)

---

## 1. Architecture Overview

```
Internet
   │
   ▼
┌────────────────────────────────────────────┐
│  ALB (public)                              │
│  :80  → Backend Blue/Green TG             │
│  :8080 → Backend Green TG (CodeDeploy)     │
│  :3000 → Metabase TG                      │
└────────────────────────────────────────────┘
   │                        │
   ▼                        ▼
┌──────────────┐    ┌───────────────────┐
│ Backend ECS  │    │  Metabase ECS     │
│ (private)    │    │  (private)        │
│ Spring Boot  │    │  metabase/metabase│
│ :8080        │    │  :3000            │
└──────┬───────┘    └────────┬──────────┘
       │                     │
       ▼                     ▼
┌─────────────────────────────────────┐
│  RDS PostgreSQL 16 (private)        │
│  logstream_db + metabase DB         │
└─────────────────────────────────────┘

EventBridge Scheduler
   │
   ▼
┌───────────────────────────────────────────────────────┐
│  Data-Engineering ECS Task (on-demand, no service)    │
│  Fargate: 256 CPU / 512 MB                            │
│  Runs: etl_pipeline.py | push_logs_to_api.py          │
│         | retention_policy.py                         │
└───────────────────────────────────────────────────────┘

GitHub Actions (CI/CD)
   │  OIDC (no long-lived AWS keys)
   ▼
ECR ──→ CodeDeploy (Blue/Green) ──→ Backend ECS Service
```

**Key design principles:**
- All ECS tasks run in **private subnets**. Outbound traffic goes via NAT Gateway.
- The bastion is the **only** public-facing EC2 instance.
- No long-lived AWS credentials in GitHub — OIDC federation only.
- All secrets in **AWS Secrets Manager**, injected at ECS container startup.
- Data-engineering has **no persistent ECS service** — EventBridge launches a task per schedule and it exits when done.

---

## 2. Repository Structure

```
devops/
├── DEVOPS.md                  ← this file
├── db/
│   └── ensure_metabase_db.sh  ← one-time: creates the metabase database on RDS
├── scripts/
│   └── deploy.sh              ← local docker-compose deploy helper
└── terraform/
    ├── main.tf                ← root: wires all 10 modules together
    ├── variables.tf           ← all input variable declarations
    ├── dev.tfvars             ← dev environment values (no secrets)
    ├── prod.tfvars            ← prod environment values (no secrets)
    ├── example.tfvars         ← template for new environments
    ├── outputs.tf             ← exposes key resource outputs
    ├── versions.tf            ← required_providers + terraform version lock
    ├── backend.tf             ← S3 backend placeholder (configured via -backend-config)
    ├── envs/
    │   ├── dev/backend.hcl    ← dev state: bucket + key + lock table
    │   └── prod/backend.hcl   ← prod state: separate key
    ├── bootstrap/
    │   └── main.tf            ← creates S3 bucket + DynamoDB for state (run once)
    └── modules/
        ├── vpc/               ← VPC, subnets, NAT GW, security groups
        ├── ecr/               ← ECR repositories (3 repos per env)
        ├── secrets/           ← Secrets Manager + RDS password generation
        ├── rds/               ← RDS PostgreSQL 16 instance
        ├── iam/               ← All IAM roles (ECS, CodeDeploy, Scheduler, GitHub OIDC)
        ├── alb/               ← ALB + target groups + listeners
        ├── ecs/               ← ECS cluster + 3 task definitions + 2 services
        ├── bastion/           ← EC2 bastion + key pair + Elastic IP
        ├── codedeploy/        ← CodeDeploy app + deployment group
        └── eventbridge/       ← 5 EventBridge Scheduler schedules
```

---

## 3. Terraform State Backend

State is stored remotely in S3 with DynamoDB locking. This was set up **once** using the bootstrap module.

| Resource | Name |
|----------|------|
| S3 bucket | `logstream-terraform-state-205930639565` |
| DynamoDB table | `terraform-state-lock-205930639565` |
| Dev state key | `logstream/dev/terraform.tfstate` |
| Prod state key | `logstream/prod/terraform.tfstate` |

### Bootstrap (run only once, ever)

```bash
cd devops/terraform/bootstrap
terraform init
terraform apply
```

This creates the S3 bucket and DynamoDB table. After that, all subsequent `terraform init` calls use the remote backend.

---

## 4. Terraform Workflow

### First time (or after deleting `.terraform/`)

```bash
cd devops/terraform

# Dev
terraform init -backend-config=envs/dev/backend.hcl

# Prod
terraform init -reconfigure -backend-config=envs/prod/backend.hcl
```

### Sensitive variables (never store in tfvars)

```bash
# JWT secret
export TF_VAR_jwt_secret="logstream-secret-key-amalitech-2024-secure"

# Bastion SSH public key
export TF_VAR_bastion_public_key="$(cat ~/.ssh/logstream-bastion.pub)"
```

### Daily workflow

```bash
# Plan (dry run — review before applying)
terraform plan -var-file=dev.tfvars

# Apply
terraform apply -var-file=dev.tfvars

# Destroy (be careful)
terraform destroy -var-file=dev.tfvars
```

### Switching between dev and prod

```bash
terraform init -reconfigure -backend-config=envs/prod/backend.hcl
terraform plan  -var-file=prod.tfvars
terraform apply -var-file=prod.tfvars
```

> **Important:** `-reconfigure` is required when switching environments because the S3 state key changes.

---

## 5. Module Deep-Dive

### 5.1 VPC & Networking

**Module:** `modules/vpc/`

**What it creates:**
- 1 VPC: `10.0.0.0/16` with DNS hostnames + DNS support enabled
- 2 public subnets: `10.0.1.0/24`, `10.0.2.0/24` (one per AZ)
- 2 private subnets: `10.0.11.0/24`, `10.0.12.0/24` (one per AZ)
- 1 Internet Gateway
- 1 NAT Gateway (dev: shared; prod: one per AZ for HA)
- Elastic IPs for NAT Gateway(s)
- Public and private route tables
- **S3 Gateway VPC Endpoint** — free; avoids NAT charges for ECR Docker layer pulls from S3

**Security Groups:**

| SG | Inbound | Outbound |
|----|---------|----------|
| `sg-alb` | TCP 80 + 3000 from `0.0.0.0/0` | All |
| `sg-backend` | TCP 8080 from `sg-alb` | All |
| `sg-metabase` | TCP 3000 from `sg-alb` | All |
| `sg-data-engineering` | None | All |
| `sg-bastion` | TCP 22 from `allowed_ssh_cidrs` | All |
| `sg-rds` | TCP 5432 from backend + de + metabase SGs | All |

**Bastion→RDS ingress** is managed as a separate `aws_vpc_security_group_ingress_rule` resource. The RDS SG uses `lifecycle { ignore_changes = [ingress] }` so Terraform does not delete this rule during plan/apply. See [Technical Decisions](#9-technical-decisions--fixes) for why.

### 5.2 ECR

**Module:** `modules/ecr/`

**Repositories per environment:**

| Repository | Dev | Prod |
|------------|-----|------|
| Backend | `logstream-dev-backend` | `logstream-prod-backend` |
| Data Engineering | `logstream-dev-data-engineering` | `logstream-prod-data-engineering` |
| Metabase | `logstream-dev-metabase` | `logstream-prod-metabase` |

- Tags: MUTABLE (so `latest` can be overwritten)
- Scan on push: enabled
- The naming pattern `${project_name}-${environment}-${service}` was chosen so dev and prod repos coexist in the same AWS account.

### 5.3 Secrets Manager

**Module:** `modules/secrets/`

**Secrets created:**

| Secret Name | Keys | Used by |
|-------------|------|---------|
| `logstream/{env}/db` | `username`, `password` | Backend, Data-Engineering, Metabase ECS tasks |
| `logstream/{env}/jwt` | `jwt_secret` | Backend ECS task |

- Database password is randomly generated by Terraform (`random_password`)
- Password `lifecycle { ignore_changes }` prevents Terraform from regenerating it on subsequent runs
- Secrets are injected via ECS `secrets` block (not environment variables) — they never appear in the ECS console or task definition JSON in plaintext

### 5.4 RDS (PostgreSQL 16)

**Module:** `modules/rds/`

| Setting | Dev | Prod |
|---------|-----|------|
| Instance | `db.t3.micro` | `db.t3.micro` |
| Storage | 20 GB gp3 (autoscales to 100 GB) | same |
| Encryption | Enabled (gp3) | Enabled |
| Multi-AZ | `false` | `true` |
| Backup retention | 7 days | 7 days |
| Backup window | 03:00–04:00 UTC | 03:00–04:00 UTC |
| Deletion protection | `false` | `true` |
| Final snapshot | Skipped | Created |
| Performance Insights | Enabled | Enabled |

**Parameter group** (`logstream-{env}-pg16`, family `postgres16`):
- `shared_preload_libraries = pg_stat_statements` (requires reboot to activate)
- `log_connections = 1`
- `log_min_duration_statement = 1000` (log queries slower than 1 second)

**Dev endpoint:** `logstream-dev-postgres.c7mykcceef2y.eu-west-1.rds.amazonaws.com:5432`

**Database:** `logstream_db` | **User:** `logstream_user`

### 5.5 IAM

**Module:** `modules/iam/`

| Role | Principal | Permissions |
|------|-----------|-------------|
| `ecs-execution-role` | `ecs-tasks.amazonaws.com` | `AmazonECSTaskExecutionRolePolicy` + Secrets Manager read |
| `backend-task-role` | `ecs-tasks.amazonaws.com` | Secrets Manager + CloudWatch Logs + PutMetricData |
| `de-task-role` | `ecs-tasks.amazonaws.com` | Secrets Manager + CloudWatch Logs |
| `metabase-task-role` | `ecs-tasks.amazonaws.com` | CloudWatch Logs only |
| `codedeploy-role` | `codedeploy.amazonaws.com` | `AWSCodeDeployRoleForECS` |
| `scheduler-role` | `scheduler.amazonaws.com` | `ecs:RunTask` + `iam:PassRole` for data-engineering |
| `github-actions-role` | OIDC: `token.actions.githubusercontent.com` | ECR push + CodeDeploy deployment |

**OIDC trust policy** for GitHub Actions:
- Audience: `sts.amazonaws.com`
- Subject: `repo:KofiAckah/NSP_25_26_Team_13:*`
- No long-lived AWS access keys anywhere in the repo

### 5.6 ALB

**Module:** `modules/alb/`

**DNS:** `logstream-dev-alb-78023209.eu-west-1.elb.amazonaws.com`

| Listener | Port | Default target | Purpose |
|----------|------|----------------|---------|
| HTTP | 80 | `backend-blue` TG | Live production traffic |
| Test | 8080 | `backend-green` TG | CodeDeploy test traffic during deployment |
| Metabase | 3000 | `metabase` TG | Metabase dashboard |

**Target Groups:**
- `backend-blue` — health check: `GET /actuator/health` → `200` (2 healthy / 3 unhealthy threshold)
- `backend-green` — same health check; CodeDeploy shifts traffic blue→green after validation
- `metabase` — health check: `GET /api/health` → `200`

**Key design decisions:**
- HTTP only for now (no ACM certificate or Route53). To add HTTPS: add `aws_lb_listener "https"` with an ACM cert ARN.
- Listeners use `lifecycle { ignore_changes = [default_action] }` so Terraform does not override CodeDeploy's live traffic routing after a deployment.
- `enable_deletion_protection = true` in prod only.

### 5.7 ECS

**Module:** `modules/ecs/`

**Cluster:** `logstream-dev` with Container Insights enabled.

#### Backend Service (Spring Boot)

| Setting | Value |
|---------|-------|
| CPU | 512 (0.5 vCPU) |
| Memory | 1024 MB |
| Desired count | 1 |
| Launch type | FARGATE |
| Deployment | `CODE_DEPLOY` (Blue/Green) |
| Subnets | Private |
| Public IP | None (uses NAT) |

Environment variables (runtime):
- `SPRING_PROFILES_ACTIVE` = `dev`
- `SERVER_PORT` = `8080`
- `SPRING_DATASOURCE_URL` = `jdbc:postgresql://<rds-host>:5432/logstream_db`

Secrets injected:
- `SPRING_DATASOURCE_USERNAME` ← `logstream/{env}/db:username`
- `SPRING_DATASOURCE_PASSWORD` ← `logstream/{env}/db:password`
- `JWT_SECRET` ← `logstream/{env}/jwt:jwt_secret`

CloudWatch logs: `/ecs/logstream/dev/backend` (30-day retention)

**Note:** The ECS service uses `lifecycle { ignore_changes = [task_definition, desired_count, load_balancer] }` because CodeDeploy manages the actual task definition revisions and traffic routing after the initial creation.

#### Data-Engineering (ETL / Python)

| Setting | Value |
|---------|-------|
| CPU | 256 (0.25 vCPU) |
| Memory | 512 MB |
| Service | None — EventBridge launches individual tasks |

Environment variables:
- `DB_HOST`, `DB_PORT=5432`, `DB_NAME=logstream_db`
- `PYTHONUNBUFFERED=1`
- `API_URL_BATCH` = `http://logstream-dev-alb-78023209.eu-west-1.elb.amazonaws.com/api/logs/batch`

Secrets injected:
- `DB_USER`, `DB_PASSWORD` ← `logstream/{env}/db`

CloudWatch logs: `/ecs/logstream/dev/data-engineering` (14-day retention)

#### Metabase

| Setting | Value |
|---------|-------|
| CPU | 512 |
| Memory | 1024 MB |
| Desired count | 1 |
| Port | 3000 |

**Important:** Metabase uses its own database `metabase` on the same RDS instance — NOT `logstream_db`. The `ensure_metabase_db.sh` script creates this database once.

CloudWatch logs: `/ecs/logstream/dev/metabase` (30-day retention)

### 5.8 Bastion Host

**Module:** `modules/bastion/`

| Setting | Value |
|---------|-------|
| Instance ID | `i-0f6a522dc9499cec2` |
| Instance type | `t3.micro` (free-tier eligible) |
| AMI | Latest Amazon Linux 2023 (x86_64) |
| Elastic IP | `18.200.108.251` |
| Key name | `logstream-dev-bastion-key` |
| Subnet | Public subnet (eu-west-1a) |

SSH hardening (user-data):
- `PasswordAuthentication no`
- `PermitRootLogin no`
- Root volume: 30 GB gp3, encrypted

**Why a bastion?** RDS is in a private subnet. The only way to connect from outside AWS (local machine, DBeaver, TablePlus) is via an SSH tunnel through the bastion.

For connection instructions see [Runbook 8.1](#81-connect-to-rds-via-ssh-tunnel).

**Key note:** `lifecycle { ignore_changes = [ami] }` prevents Terraform from replacing the instance every time AWS releases a new AL2023 AMI. Without this, every `terraform apply` would show the bastion as needing replacement.

### 5.9 CodeDeploy

**Module:** `modules/codedeploy/`

| Setting | Value |
|---------|-------|
| App name | `logstream-dev-backend` |
| Deployment group | `logstream-dev-backend-dg` |
| Config | `CodeDeployDefault.ECSAllAtOnce` |
| Type | Blue/Green with traffic control |

**Deployment flow:**
1. GitHub Actions builds a new image, pushes to ECR, creates a CodeDeploy deployment
2. CodeDeploy launches a new "green" task set using the new image
3. Green tasks are registered against the green target group on port 8080 (test listener)
4. Health checks on `/actuator/health` must pass
5. CodeDeploy shifts production traffic (port 80 listener) from blue → green immediately (`CONTINUE_DEPLOYMENT`)
6. Blue tasks are terminated after 5 minutes

**Auto-rollback:** Triggered on `DEPLOYMENT_FAILURE` or `DEPLOYMENT_STOP_ON_ALARM`.

### 5.10 EventBridge Scheduler

**Module:** `modules/eventbridge/`

All schedules launch the same `data-engineering` ECS task definition but override the container command.

| Schedule name | Expression | Command |
|---------------|------------|---------|
| `etl-15min` | `rate(15 minutes)` | `etl_pipeline.py --mode standard` |
| `aggregate-hourly` | `cron(5 * * * ? *)` | `etl_pipeline.py --mode hourly` |
| `push-logs-to-api` | `cron(0 * * * ? *)` | `push_logs_to_api.py` |
| `aggregate-daily` | `cron(0 1 * * ? *)` | `etl_pipeline.py --mode daily` |
| `retention-policy` | `cron(0 2 * * ? *)` | `retention_policy.py` |

**How the command override works:** Each schedule sends an `input` JSON to EventBridge with `containerOverrides.containers[0].command`. This overrides the `CMD` from the Docker image at runtime without needing separate task definitions per script.

**scheduler-role** has `ecs:RunTask` on the data-engineering task definition family and `iam:PassRole` for both the ECS execution role and the data-engineering task role.

---

## 6. CI/CD Pipeline

**File:** `.github/workflows/ci.yml`

### Workflow triggers
- Push to `main` / `dev` / `feature/*`
- Pull requests to `main`

### Jobs overview

```
build-and-test  ──→  docker-build  ──→  deploy-dev   (on dev/main push)
                                   └──→ deploy-prod  (on main push, manual approval)
```

### Authentication
GitHub Actions authenticates to AWS using OIDC — no AWS access keys stored in GitHub Secrets. The workflow uses `aws-actions/configure-aws-credentials` with `role-to-assume: arn:aws:iam::205930639565:role/logstream-dev-github-actions-role`.

### Image tagging
Images are tagged with the **Git SHA** (`GITHUB_SHA`) for traceability. `latest` is also updated on main branch pushes.

### Deployment
1. Login to ECR
2. Build + push image to `logstream-dev-backend`
3. Create a new CodeDeploy deployment pointing to the new image tag
4. CodeDeploy handles the actual Blue/Green swap on ECS

---

## 7. Environment Differences (Dev vs Prod)

| Setting | Dev | Prod |
|---------|-----|------|
| `environment` | `dev` | `prod` |
| `single_nat_gateway` | `true` (1 NAT, cheaper) | `false` (1 per AZ, HA) |
| `db_multi_az` | `false` | `true` |
| `db_deletion_protection` | `false` | `true` |
| Final RDS snapshot | Skipped | Created (`logstream-prod-final-snapshot`) |
| ALB deletion protection | `false` | `true` |
| ECR repo names | `logstream-dev-*` | `logstream-prod-*` |
| Terraform state key | `logstream/dev/terraform.tfstate` | `logstream/prod/terraform.tfstate` |
| Backend config | `envs/dev/backend.hcl` | `envs/prod/backend.hcl` |
| `allowed_ssh_cidrs` | `["0.0.0.0/0"]` | Should be restricted to team IPs |

---

## 8. Operational Runbooks

### 8.1 Connect to RDS via SSH Tunnel

**Prerequisites:**
- SSH key at `~/.ssh/logstream-bastion`
- Bastion Elastic IP: `18.200.108.251`
- RDS endpoint: `logstream-dev-postgres.c7mykcceef2y.eu-west-1.rds.amazonaws.com`

**Step 1: Open the tunnel**

```bash
ssh -i ~/.ssh/logstream-bastion \
    -L 5433:logstream-dev-postgres.c7mykcceef2y.eu-west-1.rds.amazonaws.com:5432 \
    -N -f \
    ec2-user@18.200.108.251
```

- `-L 5433:<rds>:5432` — forwards localhost:5433 → RDS:5432 through the bastion
- `-N` — no shell, just the tunnel
- `-f` — background the process

**Step 2: Connect**

```bash
psql -h localhost -p 5433 -U logstream_user -d logstream_db
```

Or use DBeaver / TablePlus with:
- Host: `localhost`
- Port: `5433`
- Database: `logstream_db`
- User: `logstream_user`

**Step 3: Close the tunnel when done**

```bash
# Find and kill the tunnel
pkill -f "ssh.*5433"
```

**Verify the tunnel is already open (avoid starting it twice):**

```bash
lsof -i :5433
```

### 8.2 Push Docker Images to ECR Manually

```bash
# Set variables
AWS_ACCOUNT=205930639565
AWS_REGION=eu-west-1
ENV=dev
SERVICE=backend   # or data-engineering, metabase

# Authenticate Docker to ECR
aws ecr get-login-password --region $AWS_REGION \
  | docker login --username AWS --password-stdin \
    $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com

# Build and tag
docker build -t logstream-$ENV-$SERVICE ./backend

docker tag logstream-$ENV-$SERVICE:latest \
  $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/logstream-$ENV-$SERVICE:latest

# Push
docker push $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/logstream-$ENV-$SERVICE:latest
```

### 8.3 Trigger a CodeDeploy Deployment Manually

```bash
# Get the current task definition ARN
TASK_DEF=$(aws ecs describe-task-definition \
  --task-definition logstream-dev-backend \
  --query 'taskDefinition.taskDefinitionArn' \
  --output text)

# Create a CodeDeploy deployment
aws deploy create-deployment \
  --application-name logstream-dev-backend \
  --deployment-group-name logstream-dev-backend-dg \
  --deployment-config-name CodeDeployDefault.ECSAllAtOnce \
  --description "Manual deployment" \
  --revision '{
    "revisionType": "AppSpecContent",
    "appSpecContent": {
      "content": "{\"version\":0,\"Resources\":[{\"TargetService\":{\"Type\":\"AWS::ECS::Service\",\"Properties\":{\"TaskDefinition\":\"'$TASK_DEF'\",\"LoadBalancerInfo\":{\"ContainerName\":\"backend\",\"ContainerPort\":8080}}}}]}"
    }
  }'
```

### 8.4 Trigger an EventBridge Task Manually

To run a data-engineering script immediately without waiting for the schedule:

```bash
# Get the latest task definition ARN
TASK_DEF=$(aws ecs describe-task-definition \
  --task-definition logstream-dev-data-engineering \
  --query 'taskDefinition.taskDefinitionArn' \
  --output text)

# Get network config from Terraform outputs or use known values
SUBNET_ID=<private-subnet-id>
SG_ID=<data-engineering-sg-id>

# Run ETL standard mode manually
aws ecs run-task \
  --cluster logstream-dev \
  --task-definition $TASK_DEF \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_ID],securityGroups=[$SG_ID],assignPublicIp=DISABLED}" \
  --overrides '{
    "containerOverrides": [{
      "name": "data-engineering",
      "command": ["python", "/app/scripts/etl_pipeline.py", "--mode", "standard"]
    }]
  }'
```

### 8.5 Check ECS Task Logs

```bash
# Backend logs (last 100 lines)
aws logs tail /ecs/logstream/dev/backend --follow

# Data-engineering logs
aws logs tail /ecs/logstream/dev/data-engineering --follow

# Metabase logs
aws logs tail /ecs/logstream/dev/metabase --follow

# Filter for errors
aws logs filter-log-events \
  --log-group-name /ecs/logstream/dev/data-engineering \
  --filter-pattern "ERROR"
```

### 8.6 Run Terraform for dev

```bash
cd devops/terraform

# Export sensitive vars
export TF_VAR_jwt_secret="logstream-secret-key-amalitech-2024-secure"
export TF_VAR_bastion_public_key="$(cat ~/.ssh/logstream-bastion.pub)"

# Init (only needed once or after deleting .terraform/)
terraform init -backend-config=envs/dev/backend.hcl

# Plan — always review before apply
terraform plan -var-file=dev.tfvars

# Apply
terraform apply -var-file=dev.tfvars

# Show current outputs
terraform output
```

### 8.7 Run Terraform for prod

```bash
cd devops/terraform

export TF_VAR_jwt_secret="<prod-jwt-secret>"
export TF_VAR_bastion_public_key="$(cat ~/.ssh/logstream-bastion.pub)"

# -reconfigure switches the backend to the prod state key
terraform init -reconfigure -backend-config=envs/prod/backend.hcl

terraform plan  -var-file=prod.tfvars
terraform apply -var-file=prod.tfvars
```

---

## 9. Technical Decisions & Fixes

### 9.1 Bastion AMI lifecycle ignore

**Problem:** Every `terraform plan` showed the bastion EC2 as needing replacement because AWS regularly releases newer AL2023 AMIs. The `data.aws_ami.al2023` data source always resolves to the latest AMI, so the AMI ID changed between runs.

**Fix:** `lifecycle { ignore_changes = [ami] }` on `aws_instance.bastion`.

**Effect:** Terraform creates the bastion with whatever AMI was current at creation time and then ignores AMI changes from that point forward. The bastion continues running without disruption. To update the AMI intentionally: `terraform taint module.bastion.aws_instance.bastion && terraform apply`.

### 9.2 RDS security group ingress lifecycle ignore

**Problem:** The RDS security group was created with inline `ingress` rules for backend, data-engineering, and metabase. The bastion→RDS ingress was added as a separate `aws_vpc_security_group_ingress_rule` resource. On the next `terraform plan`, Terraform tried to delete this bastion rule because it was not in the inline `ingress` block.

**Root cause:** Mixing inline ingress rules with standalone `aws_vpc_security_group_ingress_rule` resources causes Terraform to consider the standalone rules as "drift" and remove them.

**Fix:**
1. `lifecycle { ignore_changes = [ingress] }` on the `aws_security_group.rds` resource.
2. Kept the bastion ingress as a standalone `aws_vpc_security_group_ingress_rule.rds_from_bastion`.

**Effect:** Terraform manages the RDS SG as a whole but ignores changes to the `ingress` attribute, so the standalone bastion ingress rule is preserved.

### 9.3 ECR environment prefix in repository names

**Problem:** The ECR module originally created repos named `logstream-backend`, `logstream-data-engineering`, `logstream-metabase`. With both dev and prod in the same AWS account, the repos conflicted and the prod `terraform apply` would fail trying to create already-existing repos.

**Fix:** Changed the ECR `name` to `${var.project_name}-${var.environment}-${each.key}` so repos are `logstream-dev-backend` and `logstream-prod-backend`.

**Migration:** Used Terraform `import {}` blocks to import existing ECR repos under the new names without recreation.

### 9.4 ECS service lifecycle ignores (CodeDeploy)

**Problem:** `terraform apply` was reverting CodeDeploy's live traffic routing. After a deployment, CodeDeploy moves the port-80 listener from blue to green. The next `terraform apply` would reset it back to blue (the value in the Terraform state), causing a rollback.

**Fix:**
- `aws_ecs_service.backend`: `lifecycle { ignore_changes = [task_definition, desired_count, load_balancer] }`
- `aws_lb_listener.http` + `aws_lb_listener.test`: `lifecycle { ignore_changes = [default_action] }`

### 9.5 S3 Gateway VPC Endpoint

**Added:** `aws_vpc_endpoint.s3` (Gateway type, free).

**Why:** ECS Fargate pulls Docker image layers from S3 (ECR stores layers in S3). Without the VPC endpoint, this traffic goes through the NAT Gateway, which costs $0.045/GB. The S3 Gateway endpoint is free and routes this traffic directly within AWS, significantly reducing NAT costs for large image pulls.

### 9.6 api_url_batch environment variable

**Problem:** The data-engineering `push_logs_to_api.py` script needs to know the ALB URL to POST logs to the backend's `/api/logs/batch` endpoint. The URL was hardcoded directly in the script or missing entirely.

**Fix:** Added `API_URL_BATCH` as an environment variable in the ECS task definition:
```
API_URL_BATCH = "http://logstream-dev-alb-78023209.eu-west-1.elb.amazonaws.com/api/logs/batch"
```
Also threaded it through `variables.tf`, `dev.tfvars`, root `main.tf`, and `modules/ecs/variables.tf`.

---

## 10. Database Partitioning

### Background

`log_entries` is the main fact table — it stores every log line ingested by the backend. The schema (`data-engineering/db/ddl/schema_ddl.sql`) declares it as `PARTITION BY RANGE (timestamp)`. However, the backend uses Hibernate with `ddl-auto: update`, which does **not** support creating partitioned tables — it silently creates a regular (unpartitioned) table instead.

### The Problem

The ETL pipeline's `manage_partitions()` function calls `_create_partition()` to create daily partitions (e.g., `log_entries_y2026_03_12`). This was failing silently because the `log_entries` table was a regular table, not a partitioned one. The outer `try/except` block was swallowing the error.

### The Migration (already applied to dev)

A one-time migration script was run to:
1. Capture the 20 analytics views (`vw_*`) that depended on `log_entries`
2. Back up all 22,614 rows from `log_entries` to a temp table
3. `DROP TABLE log_entries CASCADE` (drops the views automatically)
4. Recreate `log_entries` as `PARTITION BY RANGE (timestamp)`
5. Create `log_entries_default` DEFAULT partition
6. Recreate 7 indexes on the partitioned table
7. Recreate all 20 views
8. Restore all rows from the backup

**Current state (dev):**
- `log_entries` is a properly partitioned table
- `log_entries_default` — catch-all for rows not in any specific partition
- `log_entries_y2026_03_12`, `log_entries_y2026_03_13` — created by the ETL

### The ETL Fix

`data-engineering/scripts/etl_pipeline.py` — `_create_partition()` was updated to:

1. **Idempotency check:** Query `pg_class` to see if the partition table already exists. If yes, return early. This prevents duplicate partition errors.

2. **Default partition conflict handling:** When a partition for date range `[start, end)` is created, PostgreSQL raises an error if `log_entries_default` already contains rows that fall in that range. The fix uses a temp-table pattern:
   ```
   CREATE TEMP TABLE _conflict_rows AS
     SELECT * FROM log_entries_default WHERE timestamp >= start AND timestamp < end;
   
   DELETE FROM log_entries_default WHERE timestamp >= start AND timestamp < end;
   
   CREATE TABLE log_entries_y2026_03_12
     PARTITION OF log_entries FOR VALUES FROM (start) TO (end);
   
   INSERT INTO log_entries_y2026_03_12 SELECT * FROM _conflict_rows;
   DROP TABLE _conflict_rows;
   ```

3. `manage_partitions()` now creates partitions for today and tomorrow on every `--mode standard` run (every 15 minutes).

---

## 11. Known Issues & Pending Work

| Issue | Status | Notes |
|-------|--------|-------|
| Prod `terraform apply` exit code 1 | **PENDING** | Not yet investigated. Run `terraform plan -var-file=prod.tfvars` to see the error. |
| HTTPS / TLS on ALB | Not implemented | HTTP only. To add: create ACM cert in `eu-west-1`, add `aws_lb_listener "https"` on port 443. |
| `allowed_ssh_cidrs` in prod | Needs tightening | Currently `0.0.0.0/0`. Should be restricted to team's static IPs: `["<ip1>/32", "<ip2>/32"]`. |
| `shared_preload_libraries` | Pending reboot | The `pg_stat_statements` parameter requires an RDS maintenance window reboot to activate. |
| Metabase admin setup | Manual | After Metabase first starts, the admin account is created through the web UI at `:3000`. |
| Auto-scaling for backend ECS | Not configured | `desired_count = 1`. Add `aws_appautoscaling_target` + `aws_appautoscaling_policy` for scale-out. |
| CloudWatch Alarms | Not configured | No alarms on ECS task failures, RDS CPU, or ALB 5xx. Should be added before production use. |
