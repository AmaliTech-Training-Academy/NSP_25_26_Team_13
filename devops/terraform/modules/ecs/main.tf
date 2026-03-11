# ── ECS Cluster ───────────────────────────────────────────────────────────
resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = { Name = "${var.project_name}-${var.environment}-cluster" }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1
  }
}

# ── CloudWatch Log Groups ─────────────────────────────────────────────────
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${var.project_name}/${var.environment}/backend"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "data_engineering" {
  name              = "/ecs/${var.project_name}/${var.environment}/data-engineering"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "metabase" {
  name              = "/ecs/${var.project_name}/${var.environment}/metabase"
  retention_in_days = 30
}

# ──────────────────────────────────────────────────────────────────────────
# BACKEND Task Definition
# ──────────────────────────────────────────────────────────────────────────
resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.project_name}-${var.environment}-backend"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.backend_task_role_arn

  container_definitions = jsonencode([{
    name      = "backend"
    image     = "${var.backend_image_url}:${var.image_tag}"
    essential = true

    portMappings = [{
      containerPort = var.backend_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.environment },
      { name = "SERVER_PORT", value = tostring(var.backend_port) },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${var.db_host}:5432/${var.db_name}" },
    ]

    secrets = [
      { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.db_secret_arn}:username::" },
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.db_secret_arn}:password::" },
      { name = "JWT_SECRET", valueFrom = "${var.jwt_secret_arn}:jwt_secret::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.backend.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "backend"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:${var.backend_port}/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = { Name = "${var.project_name}-${var.environment}-backend" }
}

# ── Backend ECS Service (CodeDeploy-managed Blue/Green) ───────────────────
resource "aws_ecs_service" "backend" {
  name            = "${var.project_name}-${var.environment}-backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count
  launch_type     = "FARGATE"

  deployment_controller {
    type = "CODE_DEPLOY"
  }

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.sg_backend_id]
    assign_public_ip = false   # NAT Gateway handles egress
  }

  load_balancer {
    target_group_arn = var.backend_blue_tg_arn
    container_name   = "backend"
    container_port   = var.backend_port
  }

  # CodeDeploy manages deployments — ignore task definition and desired count changes
  lifecycle {
    ignore_changes = [task_definition, desired_count, load_balancer]
  }

  tags = { Name = "${var.project_name}-${var.environment}-backend" }
}

# ──────────────────────────────────────────────────────────────────────────
# DATA ENGINEERING Task Definition (no persistent service — run by EventBridge)
# ──────────────────────────────────────────────────────────────────────────
resource "aws_ecs_task_definition" "data_engineering" {
  family                   = "${var.project_name}-${var.environment}-data-engineering"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.data_engineering_cpu
  memory                   = var.data_engineering_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.data_engineering_task_role_arn

  container_definitions = jsonencode([{
    name      = "data-engineering"
    image     = "${var.data_engineering_image_url}:${var.image_tag}"
    essential = true

    environment = [
      { name = "DB_HOST", value = var.db_host },
      { name = "DB_PORT", value = "5432" },
      { name = "DB_NAME", value = var.db_name },
      { name = "PYTHONUNBUFFERED", value = "1" },
    ]

    secrets = [
      { name = "DB_USER", valueFrom = "${var.db_secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${var.db_secret_arn}:password::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.data_engineering.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "data-engineering"
      }
    }
  }])

  tags = { Name = "${var.project_name}-${var.environment}-data-engineering" }
}

# ──────────────────────────────────────────────────────────────────────────
# METABASE Task Definition + Service
# ──────────────────────────────────────────────────────────────────────────
resource "aws_ecs_task_definition" "metabase" {
  family                   = "${var.project_name}-${var.environment}-metabase"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.metabase_cpu
  memory                   = var.metabase_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.metabase_task_role_arn

  container_definitions = jsonencode([{
    name      = "metabase"
    image     = "${var.metabase_image_url}:${var.image_tag}"
    essential = true

    portMappings = [{
      containerPort = var.metabase_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "MB_DB_TYPE", value = "postgres" },
      { name = "MB_DB_HOST", value = var.db_host },
      { name = "MB_DB_PORT", value = "5432" },
      { name = "MB_DB_DBNAME", value = var.db_name },
      { name = "MB_JETTY_PORT", value = tostring(var.metabase_port) },
    ]

    secrets = [
      { name = "MB_DB_USER", valueFrom = "${var.db_secret_arn}:username::" },
      { name = "MB_DB_PASS", valueFrom = "${var.db_secret_arn}:password::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.metabase.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "metabase"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:${var.metabase_port}/api/health || exit 1"]
      interval    = 60
      timeout     = 10
      retries     = 3
      startPeriod = 120
    }
  }])

  tags = { Name = "${var.project_name}-${var.environment}-metabase" }
}

resource "aws_ecs_service" "metabase" {
  name            = "${var.project_name}-${var.environment}-metabase"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.metabase.arn
  desired_count   = var.metabase_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.sg_metabase_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.metabase_tg_arn
    container_name   = "metabase"
    container_port   = var.metabase_port
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = { Name = "${var.project_name}-${var.environment}-metabase" }
}

# ── 5xx CloudWatch Alarm ───────────────────────────────────────────────────
resource "aws_cloudwatch_metric_alarm" "backend_5xx" {
  alarm_name          = "${var.project_name}-${var.environment}-backend-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  alarm_description   = "Backend returning 5xx errors"
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = split("/", split("loadbalancer/", var.alb_arn)[1])[0]
    TargetGroup  = split(":", var.backend_blue_tg_arn)[5]
  }
}
