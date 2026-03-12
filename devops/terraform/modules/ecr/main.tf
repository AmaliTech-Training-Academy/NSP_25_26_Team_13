# ──────────────────────────────────────────────────────────────────────────────
# ECR Module — References existing ECR repositories (they already exist)
# ──────────────────────────────────────────────────────────────────────────────

locals {
  repos = ["backend", "data-engineering", "metabase"]
}

# Reference existing ECR repositories (they were created manually or in a previous run)
data "aws_ecr_repository" "repos" {
  for_each = toset(local.repos)
  name     = "${var.project_name}-${var.environment}-${each.key}"
}
