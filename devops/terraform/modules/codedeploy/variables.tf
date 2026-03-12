variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "codedeploy_role_arn" {
  type = string
}

variable "ecs_cluster_name" {
  type = string
}

variable "ecs_service_name" {
  type = string
}

variable "http_listener_arn" {
  description = "Port 80 listener — production traffic route"
  type        = string
}

variable "test_listener_arn" {
  description = "Port 8080 listener — CodeDeploy test traffic route for green validation"
  type        = string
}

variable "backend_blue_tg_name" {
  type = string
}

variable "backend_green_tg_name" {
  type = string
}
