output "db_secret_arn" {
  value     = aws_secretsmanager_secret.db.arn
  sensitive = true
}

output "jwt_secret_arn" {
  value     = aws_secretsmanager_secret.jwt.arn
  sensitive = true
}

output "db_password" {
  value     = random_password.db.result
  sensitive = true
}
