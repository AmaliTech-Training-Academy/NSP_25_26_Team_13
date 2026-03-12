# Bootstrap — run once to create the S3 bucket and DynamoDB table that Terraform
# uses for remote state.  This directory uses a LOCAL backend intentionally.
#
#   cd devops/terraform/bootstrap
#   terraform init
#   terraform apply

terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "eu-west-1"
}

data "aws_caller_identity" "current" {}

# ── S3 bucket for Terraform state ──────────────────────────────────────────
resource "aws_s3_bucket" "terraform_state" {
  bucket = "logstream-terraform-state-${data.aws_caller_identity.current.account_id}"

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name      = "logstream-terraform-state"
    ManagedBy = "terraform-bootstrap"
  }
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket                  = aws_s3_bucket.terraform_state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ── DynamoDB table for state locking ───────────────────────────────────────
resource "aws_dynamodb_table" "terraform_locks" {
  name         = "logstream-terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = {
    Name      = "logstream-terraform-locks"
    ManagedBy = "terraform-bootstrap"
  }
}
