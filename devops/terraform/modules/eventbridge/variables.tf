variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "aws_account_id" {
  type = string
}

variable "ecs_cluster_name" {
  type = string
}

variable "data_engineering_task_definition_arn" {
  type = string
}

variable "scheduler_role_arn" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "sg_data_engineering_id" {
  type = string
}
