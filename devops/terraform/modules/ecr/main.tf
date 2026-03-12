locals {
  repos = ["backend", "data-engineering", "metabase"]
}

resource "aws_ecr_repository" "repos" {
  for_each = toset(local.repos)

  name                 = "${var.project_name}-${var.environment}-${each.key}"
  image_tag_mutability = var.image_tag_mutability
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = var.scan_on_push
  }

  tags = { Name = "${var.project_name}-${var.environment}-${each.key}" }
}

# ── Lifecycle policies — keep the last 10 tagged images per repo ───────────
resource "aws_ecr_lifecycle_policy" "repos" {
  for_each   = aws_ecr_repository.repos
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Retain last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
