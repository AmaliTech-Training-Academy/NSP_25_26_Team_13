locals {
  # 5 scheduled jobs — each carries an explicit containerOverrides command so the
  # container executes the correct script when launched by EventBridge and then
  # exits cleanly (no persistent cron daemon needed).
  schedules = {
    etl_15min = {
      description         = "Incremental ETL - every 15 minutes"
      schedule_expression = "rate(15 minutes)"
      command             = ["python", "/app/scripts/etl_pipeline.py", "--mode", "standard"]
    }
    aggregate_hourly = {
      description         = "Hourly aggregation - every hour at :05"
      schedule_expression = "cron(5 * * * ? *)"
      command             = ["python", "/app/scripts/etl_pipeline.py", "--mode", "hourly"]
    }
    push_logs_to_api = {
      description         = "API log ingestion - every hour at :00"
      schedule_expression = "cron(0 * * * ? *)"
      command             = ["python", "/app/scripts/push_logs_to_api.py"]
    }
    aggregate_daily = {
      description         = "Daily aggregation - every day at 01:00 UTC"
      schedule_expression = "cron(0 1 * * ? *)"
      command             = ["python", "/app/scripts/etl_pipeline.py", "--mode", "daily"]
    }
    retention_policy = {
      description         = "Retention policy enforcement - every day at 02:00 UTC"
      schedule_expression = "cron(0 2 * * ? *)"
      command             = ["python", "/app/scripts/retention_policy.py"]
    }
  }
}

resource "aws_scheduler_schedule_group" "main" {
  name = "${var.project_name}-${var.environment}"
}

resource "aws_scheduler_schedule" "data_engineering" {
  for_each = local.schedules

  name        = "${var.project_name}-${var.environment}-${replace(each.key, "_", "-")}"
  group_name  = aws_scheduler_schedule_group.main.name
  description = each.value.description

  schedule_expression          = each.value.schedule_expression
  schedule_expression_timezone = "UTC"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:ecs:${var.aws_region}:${var.aws_account_id}:cluster/${var.ecs_cluster_name}"
    role_arn = var.scheduler_role_arn

    # Pass the explicit command for this schedule so the container runs the
    # correct script and exits — no cron daemon required inside the container.
    input = jsonencode({
      containerOverrides = [{
        name    = "data-engineering"
        command = each.value.command
      }]
    })

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
