# ── Random passwords ───────────────────────────────────────────────────────
resource "random_password" "db" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}:?"
}

# ── Secrets Manager — DB credentials ──────────────────────────────────────
resource "aws_secretsmanager_secret" "db" {
  name                    = "${var.project_name}/${var.environment}/db-credentials"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0

  tags = { Name = "${var.project_name}-${var.environment}-db-credentials" }
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = var.db_username
    password = random_password.db.result
    dbname   = var.db_name
    host     = var.db_host     # filled after RDS is created
    port     = "5432"
  })

  lifecycle {
    # Prevent Terraform from overwriting a password changed outside Terraform
    ignore_changes = [secret_string]
  }
}

# ── Secrets Manager — JWT secret ───────────────────────────────────────────
resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${var.project_name}/${var.environment}/jwt-secret"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0

  tags = { Name = "${var.project_name}-${var.environment}-jwt-secret" }
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = jsonencode({ jwt_secret = var.jwt_secret })

  lifecycle {
    ignore_changes = [secret_string]
  }
}
