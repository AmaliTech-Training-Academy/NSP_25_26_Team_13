# ─────────────────────────────────────────────────────────────────────────────
# prod.tfvars — LogStream PRODUCTION environment
# DO NOT COMMIT real secrets — jwt_secret is passed via TF_VAR_jwt_secret
#
# Usage (manual):
#   terraform init -backend-config=envs/prod/backend.hcl
#   TF_VAR_jwt_secret="..." terraform plan  -var-file=prod.tfvars
#   TF_VAR_jwt_secret="..." terraform apply -var-file=prod.tfvars
# ─────────────────────────────────────────────────────────────────────────────

# ── Global ────────────────────────────────────────────────
project_name   = "logstream"
environment    = "prod"
aws_region     = "eu-west-1"
aws_account_id = "205930639565"

tags = {
  Team       = "NSP-25-26-Team-13"
  CostCenter = "training"
  Env        = "prod"
}

# ── VPC / Networking ──────────────────────────────────────
vpc_cidr             = "10.1.0.0/16"
availability_zones   = ["eu-west-1a", "eu-west-1b"]
public_subnet_cidrs  = ["10.1.1.0/24", "10.1.2.0/24"]
private_subnet_cidrs = ["10.1.11.0/24", "10.1.12.0/24"]
single_nat_gateway   = false   # two NAT gateways for HA in prod

# ── RDS (PostgreSQL 16) ───────────────────────────────────
db_name                  = "logstream_db"
db_username              = "logstream_user"
db_instance_class        = "db.t3.small"
db_allocated_storage     = 30
db_max_allocated_storage = 100
db_multi_az              = true
db_backup_retention_days = 7
db_deletion_protection   = true

# ── ECR ───────────────────────────────────────────────────
image_tag                = "latest"
ecr_image_tag_mutability = "IMMUTABLE"
ecr_scan_on_push         = true

# ── ECS — Backend ─────────────────────────────────────────
backend_port          = 8080
backend_cpu           = 512
backend_memory        = 1024
backend_desired_count = 2

# ── ECS — Metabase ────────────────────────────────────────
metabase_port          = 3000
metabase_cpu           = 1024
metabase_memory        = 2048
metabase_desired_count = 1

# ── GitHub OIDC ───────────────────────────────────────────
github_org  = "KofiAckah"
github_repo = "NSP_25_26_Team_13"

# ── Bastion Host ──────────────────────────────────────────
# bastion_public_key is supplied via:
#   export TF_VAR_bastion_public_key="$(cat ~/.ssh/logstream-bastion.pub)"
bastion_instance_type = "t3.micro"
allowed_ssh_cidrs     = ["0.0.0.0/0"]   # restrict to team static IPs in prod
