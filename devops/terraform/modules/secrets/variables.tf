variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "db_username" {
  type = string
}

variable "db_name" {
  type = string
}

variable "db_host" {
  description = "RDS endpoint — passed in after RDS module creates the instance"
  type        = string
  default     = "pending"
}

variable "jwt_secret" {
  type      = string
  sensitive = true
}
