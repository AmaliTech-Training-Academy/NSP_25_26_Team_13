output "ecs_execution_role_arn" {
  value = aws_iam_role.ecs_execution.arn
}

output "backend_task_role_arn" {
  value = aws_iam_role.backend_task.arn
}

output "data_engineering_task_role_arn" {
  value = aws_iam_role.data_engineering_task.arn
}

output "metabase_task_role_arn" {
  value = aws_iam_role.metabase_task.arn
}

output "codedeploy_role_arn" {
  value = aws_iam_role.codedeploy.arn
}

output "scheduler_role_arn" {
  value = aws_iam_role.scheduler.arn
}

output "github_actions_role_arn" {
  value = aws_iam_role.github_actions.arn
}
