# ──────────────────────────────────────────────────────────
# Global
# ──────────────────────────────────────────────────────────
variable "project_name" {
  description = "Short name used as a prefix for all resources"
  type        = string
  default     = "logstream"
}

variable "environment" {
  description = "Deployment environment (dev | staging | prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "eu-west-1"
}

variable "aws_account_id" {
  description = "AWS account ID — used to construct ECR URIs and IAM ARNs"
  type        = string
}

variable "tags" {
  description = "Additional tags merged onto every resource"
  type        = map(string)
  default     = {}
}

# ──────────────────────────────────────────────────────────
# VPC / Networking
# ──────────────────────────────────────────────────────────
variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of AZs to deploy into (at least 2 recommended)"
  type        = list(string)
  default     = ["eu-west-1a", "eu-west-1b"]
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24"]
}

variable "single_nat_gateway" {
  description = "Use one NAT Gateway shared across all AZs (cheaper for dev/staging). Set false in prod for HA."
  type        = bool
  default     = true
}

# ──────────────────────────────────────────────────────────
# RDS (PostgreSQL)
# ──────────────────────────────────────────────────────────
variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "logstream_db"
}

variable "db_username" {
  description = "Master username for RDS"
  type        = string
  default     = "logstream_user"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage in GiB"
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Max storage autoscaling ceiling in GiB"
  type        = number
  default     = 100
}

variable "db_multi_az" {
  description = "Enable Multi-AZ for RDS (recommended in prod)"
  type        = bool
  default     = false
}

variable "db_backup_retention_days" {
  description = "Number of days to retain automated backups (0 = disabled)"
  type        = number
  default     = 7
}

variable "db_deletion_protection" {
  description = "Prevent accidental deletion of RDS instance"
  type        = bool
  default     = false
}

# ──────────────────────────────────────────────────────────
# ECR / Docker images
# ──────────────────────────────────────────────────────────
variable "image_tag" {
  description = "Docker image tag deployed to ECS (usually git SHA from CI)"
  type        = string
  default     = "latest"
}

variable "ecr_image_tag_mutability" {
  description = "MUTABLE or IMMUTABLE image tags in ECR"
  type        = string
  default     = "MUTABLE"
}

variable "ecr_scan_on_push" {
  description = "Enable ECR basic scanning on image push"
  type        = bool
  default     = true
}

# ──────────────────────────────────────────────────────────
# Secrets
# ──────────────────────────────────────────────────────────
variable "jwt_secret" {
  description = "JWT signing secret — pass via TF_VAR_jwt_secret env var, never store in tfvars"
  type        = string
  sensitive   = true
}

# ──────────────────────────────────────────────────────────
# ECS — Backend
# ──────────────────────────────────────────────────────────
variable "backend_cpu" {
  description = "vCPU units for backend Fargate task (1024 = 1 vCPU)"
  type        = number
  default     = 512
}

variable "backend_memory" {
  description = "Memory in MiB for backend Fargate task"
  type        = number
  default     = 1024
}

variable "backend_desired_count" {
  description = "Desired number of backend ECS tasks"
  type        = number
  default     = 1
}

variable "backend_port" {
  description = "Port the backend Spring Boot app listens on"
  type        = number
  default     = 8080
}

# ──────────────────────────────────────────────────────────
# ECS — Data Engineering
# ──────────────────────────────────────────────────────────
variable "data_engineering_cpu" {
  description = "vCPU units for data-engineering Fargate task"
  type        = number
  default     = 256
}

variable "data_engineering_memory" {
  description = "Memory in MiB for data-engineering Fargate task"
  type        = number
  default     = 512
}

variable "api_url_batch" {
  description = "Full URL for the backend batch log ingestion endpoint (used by push_logs_to_api.py)"
  type        = string
}

# ──────────────────────────────────────────────────────────
# ECS — Metabase
# ──────────────────────────────────────────────────────────
variable "metabase_cpu" {
  description = "vCPU units for Metabase Fargate task"
  type        = number
  default     = 512
}

variable "metabase_memory" {
  description = "Memory in MiB for Metabase Fargate task"
  type        = number
  default     = 1024
}

variable "metabase_desired_count" {
  description = "Desired number of Metabase ECS tasks"
  type        = number
  default     = 1
}

variable "metabase_port" {
  description = "Port Metabase listens on"
  type        = number
  default     = 3000
}

# ──────────────────────────────────────────────────────────
# GitHub Actions OIDC
# ──────────────────────────────────────────────────────────
variable "github_org" {
  description = "GitHub organisation (e.g. amalitech-training)"
  type        = string
  default     = ""
}

variable "github_repo" {
  description = "GitHub repository name (e.g. NSP_25_26_Team_13)"
  type        = string
  default     = ""
}

# ──────────────────────────────────────────────────────────
# Bastion Host
# ──────────────────────────────────────────────────────────
variable "bastion_public_key" {
  description = "SSH public key to install on the bastion (contents of ~/.ssh/id_ed25519.pub)"
  type        = string
  sensitive   = true
}

variable "bastion_instance_type" {
  description = "EC2 instance type for the bastion host"
  type        = string
  default     = "t3.micro"
}

variable "allowed_ssh_cidrs" {
  description = "CIDR blocks allowed to SSH into the bastion. Restrict to your team's IPs in production."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}
