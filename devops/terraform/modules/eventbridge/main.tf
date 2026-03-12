locals {
  # 4 scheduled jobs matching the data-engineering crontab
  schedules = {
    etl_15min = {
      description         = "ETL pipeline - every 15 minutes"
      schedule_expression = "rate(15 minutes)"
    }
    aggregate_hourly = {
      description         = "Hourly aggregation"
      schedule_expression = "cron(0 * * * ? *)"
    }
    cleanup_daily = {
      description         = "Daily cleanup"
      schedule_expression = "cron(0 0 * * ? *)"
    }
    retention_policy = {
      description         = "Daily retention policy (02:00 UTC)"
      schedule_expression = "cron(0 2 * * ? *)"
    }
  }
}

resource "aws_scheduler_schedule_group" "main" {
  name = "${var.project_name}-${var.environment}"
}

resource "aws_scheduler_schedule" "data_engineering" {
  for_each = local.schedules

  name       = "${var.project_name}-${var.environment}-${replace(each.key, "_", "-")}"
  group_name = aws_scheduler_schedule_group.main.name
  description = each.value.description

  schedule_expression          = each.value.schedule_expression
  schedule_expression_timezone = "UTC"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:ecs:${var.aws_region}:${var.aws_account_id}:cluster/${var.ecs_cluster_name}"
    role_arn = var.scheduler_role_arn

    ecs_parameters {
      task_definition_arn = var.data_engineering_task_definition_arn
      launch_type         = "FARGATE"

      network_configuration {
        subnets          = var.private_subnet_ids
        security_groups  = [var.sg_data_engineering_id]
        assign_public_ip = false
      }
    }

    retry_policy {
      maximum_retry_attempts       = 2
      maximum_event_age_in_seconds = 300
    }
  }
}
