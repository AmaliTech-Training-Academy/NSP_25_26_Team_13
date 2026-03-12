# ──────────────────────────────────────────────────────────────────────────────
# LogStream — Root Module
#
# Architecture:
#   Internet → ALB (public subnets) → ECS Fargate (private subnets) → RDS
#   EventBridge Scheduler → ECS Fargate (data-engineering tasks, private subnets)
#   GitHub Actions → OIDC → ECR push → CodeDeploy Blue/Green
#
# Usage:
#   terraform init   -backend-config=envs/dev/backend.hcl
#   terraform plan   -var-file=dev.tfvars
#   terraform apply  -var-file=dev.tfvars
#
# Sensitive variables must be supplied via environment variables:
#   export TF_VAR_jwt_secret="your-secret-here"
# ──────────────────────────────────────────────────────────────────────────────

# ── 1. VPC & Networking ───────────────────────────────────────────────────────
module "vpc" {
  source = "./modules/vpc"

  project_name         = var.project_name
  environment          = var.environment
  aws_region           = var.aws_region
  vpc_cidr             = var.vpc_cidr
  availability_zones   = var.availability_zones
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  single_nat_gateway   = var.single_nat_gateway
  backend_port         = var.backend_port
  metabase_port        = var.metabase_port
  allowed_ssh_cidrs    = var.allowed_ssh_cidrs
}

# ── 2. ECR repositories ───────────────────────────────────────────────────────
module "ecr" {
  source = "./modules/ecr"

  project_name         = var.project_name
  environment          = var.environment
  image_tag_mutability = var.ecr_image_tag_mutability
  scan_on_push         = var.ecr_scan_on_push
}

# ── 3. Secrets Manager ────────────────────────────────────────────────────────
# db_host is set to the RDS endpoint after RDS is created.
# On first apply Terraform creates the secret with host="pending" and RDS
# simultaneously; a second apply (or targeted apply of secrets after Rds) 
# updates the host in the secret value.
module "secrets" {
  source = "./modules/secrets"

  project_name = var.project_name
  environment  = var.environment
  db_username  = var.db_username
  db_name      = var.db_name
  db_host      = module.rds.db_host
  jwt_secret   = var.jwt_secret
}

# ── 4. RDS PostgreSQL ──────────────────────────────────────────────────────────
module "rds" {
  source = "./modules/rds"

  project_name             = var.project_name
  environment              = var.environment
  private_subnet_ids       = module.vpc.private_subnet_ids
  sg_rds_id                = module.vpc.sg_rds_id
  db_name                  = var.db_name
  db_username              = var.db_username
  db_password              = module.secrets.db_password
  db_instance_class        = var.db_instance_class
  db_allocated_storage     = var.db_allocated_storage
  db_max_allocated_storage = var.db_max_allocated_storage
  db_multi_az              = var.db_multi_az
  db_backup_retention_days = var.db_backup_retention_days
  db_deletion_protection   = var.db_deletion_protection
}

# ── 5. IAM Roles ──────────────────────────────────────────────────────────────
module "iam" {
  source = "./modules/iam"

  project_name   = var.project_name
  environment    = var.environment
  aws_region     = var.aws_region
  aws_account_id = var.aws_account_id
  github_org     = var.github_org
  github_repo    = var.github_repo
}

# ── 6. Application Load Balancer (HTTP-only) ──────────────────────────────
module "alb" {
  source = "./modules/alb"

  project_name      = var.project_name
  environment       = var.environment
  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids
  sg_alb_id         = module.vpc.sg_alb_id
}

# ── 7. ECS Cluster, Tasks & Services ──────────────────────────────────────────
module "ecs" {
  source = "./modules/ecs"

  project_name    = var.project_name
  environment     = var.environment
  aws_region      = var.aws_region
  private_subnet_ids = module.vpc.private_subnet_ids
  sg_backend_id   = module.vpc.sg_backend_id
  sg_metabase_id  = module.vpc.sg_metabase_id

  ecs_execution_role_arn         = module.iam.ecs_execution_role_arn
  backend_task_role_arn          = module.iam.backend_task_role_arn
  data_engineering_task_role_arn = module.iam.data_engineering_task_role_arn
  metabase_task_role_arn         = module.iam.metabase_task_role_arn

  backend_image_url          = module.ecr.backend_repository_url
  data_engineering_image_url = module.ecr.data_engineering_repository_url
  metabase_image_url         = module.ecr.metabase_repository_url
  image_tag                  = var.image_tag

  db_host       = module.rds.db_host
  db_name       = var.db_name
  db_secret_arn = module.secrets.db_secret_arn
  jwt_secret_arn = module.secrets.jwt_secret_arn

  backend_blue_tg_arn = module.alb.backend_blue_tg_arn
  metabase_tg_arn     = module.alb.metabase_tg_arn
  alb_arn             = module.alb.alb_arn

  backend_cpu            = var.backend_cpu
  backend_memory         = var.backend_memory
  backend_desired_count  = var.backend_desired_count
  backend_port           = var.backend_port

  data_engineering_cpu    = var.data_engineering_cpu
  data_engineering_memory = var.data_engineering_memory

  metabase_cpu           = var.metabase_cpu
  metabase_memory        = var.metabase_memory
  metabase_desired_count = var.metabase_desired_count
  metabase_port          = var.metabase_port

  depends_on = [module.rds, module.alb]
}

# ── 8. Bastion Host (SSH tunnel to private RDS) ────────────────────────────
# After apply, run:  terraform output bastion_ssh_tunnel
module "bastion" {
  source = "./modules/bastion"

  project_name          = var.project_name
  environment           = var.environment
  public_subnet_id      = module.vpc.public_subnet_ids[0]
  sg_bastion_id         = module.vpc.sg_bastion_id
  bastion_instance_type = var.bastion_instance_type
  bastion_public_key    = var.bastion_public_key

  depends_on = [module.vpc]
}

# ── 9. CodeDeploy (Blue/Green for backend) ──────────────────────────────────────
module "codedeploy" {
  source = "./modules/codedeploy"

  project_name          = var.project_name
  environment           = var.environment
  codedeploy_role_arn   = module.iam.codedeploy_role_arn
  ecs_cluster_name      = module.ecs.cluster_name
  ecs_service_name      = module.ecs.backend_service_name
  http_listener_arn     = module.alb.http_listener_arn
  test_listener_arn     = module.alb.test_listener_arn
  backend_blue_tg_name  = module.alb.backend_blue_tg_name
  backend_green_tg_name = module.alb.backend_green_tg_name

  depends_on = [module.ecs]
}

# ── 10. EventBridge Scheduler (4 cron jobs for data-engineering) ────────────────
module "eventbridge" {
  source = "./modules/eventbridge"

  project_name           = var.project_name
  environment            = var.environment
  aws_region             = var.aws_region
  aws_account_id         = var.aws_account_id
  ecs_cluster_name       = module.ecs.cluster_name
  data_engineering_task_definition_arn = module.ecs.data_engineering_task_definition_arn
  scheduler_role_arn     = module.iam.scheduler_role_arn
  private_subnet_ids     = module.vpc.private_subnet_ids
  sg_data_engineering_id = module.vpc.sg_data_engineering_id

  depends_on = [module.ecs]
}
