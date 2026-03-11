# ── Application Load Balancer ─────────────────────────────────────────────
# HTTP-only — access the app via the output alb_dns_name on port 80.
# No ACM certificate or Route53 needed. To add HTTPS later, add an
# aws_lb_listener "https" resource and supply an ACM cert ARN.
resource "aws_lb" "main" {
  name               = "${var.project_name}-${var.environment}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.sg_alb_id]
  subnets            = var.public_subnet_ids

  enable_deletion_protection = var.environment == "prod" ? true : false

  tags = { Name = "${var.project_name}-${var.environment}-alb" }
}

# ── Target Groups ─────────────────────────────────────────────────────────
# Blue target group (live traffic)
resource "aws_lb_target_group" "backend_blue" {
  name        = "${var.project_name}-${var.environment}-be-blue"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"   # required for Fargate

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 10
    interval            = 30
    matcher             = "200"
  }

  tags = { Name = "${var.project_name}-${var.environment}-backend-blue" }

  lifecycle {
    create_before_destroy = true
  }
}

# Green target group (CodeDeploy replacement fleet)
resource "aws_lb_target_group" "backend_green" {
  name        = "${var.project_name}-${var.environment}-be-green"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 10
    interval            = 30
    matcher             = "200"
  }

  tags = { Name = "${var.project_name}-${var.environment}-backend-green" }

  lifecycle {
    create_before_destroy = true
  }
}

# Metabase target group
resource "aws_lb_target_group" "metabase" {
  name        = "${var.project_name}-${var.environment}-metabase"
  port        = 3000
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/api/health"
    healthy_threshold   = 2
    unhealthy_threshold = 5
    timeout             = 10
    interval            = 60
    matcher             = "200"
  }

  tags = { Name = "${var.project_name}-${var.environment}-metabase" }

  lifecycle {
    create_before_destroy = true
  }
}

# ── Listeners ─────────────────────────────────────────────────────────────

# Port 80 — production traffic → backend blue (live)
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend_blue.arn
  }
}

# Port 8080 — CodeDeploy test traffic → backend green (replacement fleet)
# CodeDeploy shifts traffic from blue→green after validating on this port.
resource "aws_lb_listener" "test" {
  load_balancer_arn = aws_lb.main.arn
  port              = 8080
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend_green.arn
  }
}

# Port 3000 — Metabase
resource "aws_lb_listener" "metabase" {
  load_balancer_arn = aws_lb.main.arn
  port              = 3000
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.metabase.arn
  }
}
