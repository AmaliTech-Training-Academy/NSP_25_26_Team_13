output "cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "cluster_arn" {
  value = aws_ecs_cluster.main.arn
}

output "backend_service_name" {
  value = aws_ecs_service.backend.name
}

output "backend_task_definition_arn" {
  value = aws_ecs_task_definition.backend.arn
}

output "data_engineering_task_definition_arn" {
  value = aws_ecs_task_definition.data_engineering.arn
}

output "metabase_service_name" {
  value = aws_ecs_service.metabase.name
}
