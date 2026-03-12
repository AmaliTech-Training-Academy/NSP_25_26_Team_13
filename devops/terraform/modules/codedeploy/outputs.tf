output "app_name" {
  value = aws_codedeploy_app.backend.name
}

output "deployment_group_name" {
  value = aws_codedeploy_deployment_group.backend.deployment_group_name
}
