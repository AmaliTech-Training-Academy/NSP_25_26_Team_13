output "backend_repository_url" {
  value = data.aws_ecr_repository.repos["backend"].repository_url
}

output "data_engineering_repository_url" {
  value = data.aws_ecr_repository.repos["data-engineering"].repository_url
}

output "metabase_repository_url" {
  value = data.aws_ecr_repository.repos["metabase"].repository_url
}

output "repository_urls" {
  value = { for k, v in data.aws_ecr_repository.repos : k => v.repository_url }
}
