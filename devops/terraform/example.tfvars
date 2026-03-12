# ─────────────────────────────────────────────────────────────────────────────
# example.tfvars — SAFE TO COMMIT
# Copy this file to dev.tfvars / staging.tfvars / prod.tfvars and fill in
# real values.  Never commit those files.
#
# Usage:
#   terraform init -backend-config=envs/dev/backend.hcl
#   terraform plan  -var-file=dev.tfvars
#   terraform apply -var-file=dev.tfvars
#
# Sensitive variables (jwt_secret) must be supplied via environment variables:
#   export TF_VAR_jwt_secret="your-secret-here"
# ─────────────────────────────────────────────────────────────────────────────

# ── Global ────────────────────────────────────────────────
project_name   = "logstream"
environment    = "dev"             # dev | staging | prod
aws_region     = "eu-west-1"
aws_account_id = "YOUR_AWS_ACCOUNT_ID"

tags = {
  Team       = "NSP-25-26-Team-13"
  CostCenter = "training"
}

# ── VPC / Networking ──────────────────────────────────────
vpc_cidr             = "10.0.0.0/16"
availability_zones   = ["eu-west-1a", "eu-west-1b"]
public_subnet_cidrs  = ["10.0.1.0/24", "10.0.2.0/24"]
private_subnet_cidrs = ["10.0.11.0/24", "10.0.12.0/24"]

# One NAT Gateway shared across AZs (cost-optimised for non-prod)
single_nat_gateway = true

# ── RDS (PostgreSQL 16) ───────────────────────────────────
db_name                  = "logstream_db"
db_username              = "logstream_user"
db_instance_class        = "db.t3.micro"
db_allocated_storage     = 20
db_max_allocated_storage = 100
db_multi_az              = false
db_backup_retention_days = 7
db_deletion_protection   = false

# ── ECR ───────────────────────────────────────────────────
image_tag                = "latest"
ecr_image_tag_mutability = "MUTABLE"
ecr_scan_on_push         = true

# ── ECS — Backend (Spring Boot) ───────────────────────────
backend_cpu           = 512
backend_memory        = 1024
backend_desired_count = 1

# ── ECS — Data Engineering (Python cron ETL) ─────────────
data_engineering_cpu    = 256
data_engineering_memory = 512

# ── ECS — Metabase ────────────────────────────────────────
metabase_cpu           = 512
metabase_memory        = 1024
metabase_desired_count = 1

# ── GitHub Actions OIDC ───────────────────────────────────
github_org  = "YOUR_GITHUB_ORG"
github_repo = "NSP_25_26_Team_13"

# ── Sensitive (supply via env var, NOT here) ──────────────
# export TF_VAR_jwt_secret="your-jwt-secret"
