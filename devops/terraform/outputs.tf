output "alb_url" {
  description = "Your app URL — open this in a browser after apply"
  value       = "http://${module.alb.alb_dns_name}"
}

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = module.alb.alb_dns_name
}

output "metabase_url" {
  description = "Metabase analytics dashboard URL"
  value       = "http://${module.alb.alb_dns_name}:3000"
}

output "ecr_backend_url" {
  description = "ECR repository URL for the backend image"
  value       = module.ecr.backend_repository_url
}

output "ecr_data_engineering_url" {
  description = "ECR repository URL for the data-engineering image"
  value       = module.ecr.data_engineering_repository_url
}

output "ecr_metabase_url" {
  description = "ECR repository URL for the metabase image"
  value       = module.ecr.metabase_repository_url
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint (host:port)"
  value       = module.rds.db_endpoint
}

output "rds_db_name" {
  description = "RDS database name"
  value       = module.rds.db_name
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = module.ecs.cluster_name
}

output "ecs_backend_service_name" {
  description = "ECS backend service name"
  value       = module.ecs.backend_service_name
}

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = module.vpc.private_subnet_ids
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = module.vpc.public_subnet_ids
}

output "codedeploy_app_name" {
  description = "CodeDeploy application name"
  value       = module.codedeploy.app_name
}

output "codedeploy_deployment_group_name" {
  description = "CodeDeploy deployment group name"
  value       = module.codedeploy.deployment_group_name
}

output "github_actions_role_arn" {
  description = "IAM role ARN for GitHub Actions OIDC"
  value       = module.iam.github_actions_role_arn
}

output "db_secret_arn" {
  description = "Secrets Manager ARN containing RDS credentials"
  value       = module.secrets.db_secret_arn
  sensitive   = true
}

# ── Bastion ─────────────────────────────────────────────────────────────────────
output "bastion_public_ip" {
  description = "Elastic IP of the bastion host"
  value       = module.bastion.public_ip
}

output "bastion_ssh_tunnel" {
  description = "Copy-paste this to open an SSH tunnel: localhost:5433 maps to RDS:5432"
  value       = "ssh -i <your-key.pem> -L 5433:${module.rds.db_host}:5432 -N -f ec2-user@${module.bastion.public_ip}"
}
