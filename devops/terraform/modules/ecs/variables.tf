variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "sg_backend_id" {
  type = string
}

variable "sg_metabase_id" {
  type = string
}

variable "ecs_execution_role_arn" {
  type = string
}

variable "backend_task_role_arn" {
  type = string
}

variable "data_engineering_task_role_arn" {
  type = string
}

variable "metabase_task_role_arn" {
  type = string
}

variable "backend_image_url" {
  type = string
}

variable "data_engineering_image_url" {
  type = string
}

variable "metabase_image_url" {
  type = string
}

variable "image_tag" {
  type    = string
  default = "latest"
}

variable "db_host" {
  type = string
}

variable "db_name" {
  type = string
}

variable "db_secret_arn" {
  type      = string
  sensitive = true
}

variable "jwt_secret_arn" {
  type      = string
  sensitive = true
}

variable "backend_blue_tg_arn" {
  type = string
}

variable "metabase_tg_arn" {
  type = string
}

variable "alb_arn" {
  type = string
}

variable "backend_cpu" {
  type    = number
  default = 512
}

variable "backend_memory" {
  type    = number
  default = 1024
}

variable "backend_desired_count" {
  type    = number
  default = 1
}

variable "backend_port" {
  type    = number
  default = 8080
}

variable "data_engineering_cpu" {
  type    = number
  default = 256
}

variable "data_engineering_memory" {
  type    = number
  default = 512
}

variable "metabase_cpu" {
  type    = number
  default = 512
}

variable "metabase_memory" {
  type    = number
  default = 1024
}

variable "metabase_desired_count" {
  type    = number
  default = 1
}

variable "metabase_port" {
  type    = number
  default = 3000
}
