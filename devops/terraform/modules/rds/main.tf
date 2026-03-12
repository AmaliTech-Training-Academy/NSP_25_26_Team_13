# ── Subnet group ───────────────────────────────────────────────────────────
resource "aws_db_subnet_group" "main" {
  name        = "${var.project_name}-${var.environment}-db-subnet-group"
  subnet_ids  = var.private_subnet_ids
  description = "Private subnets for RDS PostgreSQL"

  tags = { Name = "${var.project_name}-${var.environment}-db-subnet-group" }
}

# ── Parameter group (enables pg_trgm, sets log settings) ──────────────────
resource "aws_db_parameter_group" "main" {
  name        = "${var.project_name}-${var.environment}-pg16"
  family      = "postgres16"
  description = "LogStream PostgreSQL 16 parameters"

  parameter {
    name         = "shared_preload_libraries"
    value        = "pg_stat_statements"
    apply_method = "pending-reboot"
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"   # log queries > 1s
  }

  tags = { Name = "${var.project_name}-${var.environment}-pg16" }
}

# ── RDS PostgreSQL 16 ───────────────────────────────────────────────────────
resource "aws_db_instance" "main" {
  identifier = "${var.project_name}-${var.environment}-postgres"

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  multi_az               = var.db_multi_az
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.sg_rds_id]
  parameter_group_name   = aws_db_parameter_group.main.name

  backup_retention_period = var.db_backup_retention_days
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  skip_final_snapshot       = var.environment != "prod"
  final_snapshot_identifier = var.environment == "prod" ? "${var.project_name}-${var.environment}-final-snapshot" : null
  deletion_protection       = var.db_deletion_protection

  performance_insights_enabled = true

  tags = { Name = "${var.project_name}-${var.environment}-postgres" }

  lifecycle {
    ignore_changes = [password]
  }
}
