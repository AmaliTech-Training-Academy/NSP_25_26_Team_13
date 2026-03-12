# Partial backend configuration — pass the environment-specific hcl file at init time:
#
#   Dev:     terraform init -backend-config=envs/dev/backend.hcl
#   Staging: terraform init -backend-config=envs/staging/backend.hcl
#   Prod:    terraform init -backend-config=envs/prod/backend.hcl
#
# Bootstrap the S3 bucket + DynamoDB table first (see bootstrap/).

terraform {
  backend "s3" {}
}
