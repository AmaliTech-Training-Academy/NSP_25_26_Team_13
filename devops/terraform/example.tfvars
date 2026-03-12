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

# ── Bastion Host (← enables SSH tunnel to private RDS) ─────────────────
#
# SETUP (one-time per developer):
#   1. Generate an SSH key pair (skip if you already have one):
#        ssh-keygen -t ed25519 -f ~/.ssh/logstream-bastion
#
#   2. Set the public key as an env var so it is never committed:
#        export TF_VAR_bastion_public_key="$(cat ~/.ssh/logstream-bastion.pub)"
#
#   3. After terraform apply, get the tunnel command:
#        terraform output bastion_ssh_tunnel
#
#   4. Open the tunnel in the background:
#        ssh -i ~/.ssh/logstream-bastion \
#            -L 5433:<rds-endpoint>:5432 \
#            -N -f ec2-user@<bastion-ip>
#
#   5. Connect your DB client to localhost:5433 (same RDS credentials):
#        psql -h localhost -p 5433 -U <db_user> -d <db_name>
#        # DBeaver / TablePlus: host=localhost port=5433
#
# SECURITY: In prod, restrict allowed_ssh_cidrs to static team IPs.
bastion_instance_type = "t3.micro"
allowed_ssh_cidrs     = ["0.0.0.0/0"]  # tighten in prod: ["<your-ip>/32"]

# ── Sensitive (supply via env var, NOT here) ───────────────────
# export TF_VAR_jwt_secret="your-jwt-secret"
# export TF_VAR_bastion_public_key="$(cat ~/.ssh/logstream-bastion.pub)"
