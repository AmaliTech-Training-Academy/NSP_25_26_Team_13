variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "sg_alb_id" {
  type = string
}

# Hard-coded ports matching the application — no variables needed
# backend: 8080, metabase: 3000
