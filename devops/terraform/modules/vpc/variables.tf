variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "vpc_cidr" {
  type = string
}

variable "availability_zones" {
  type = list(string)
}

variable "public_subnet_cidrs" {
  type = list(string)
}

variable "private_subnet_cidrs" {
  type = list(string)
}

variable "single_nat_gateway" {
  type    = bool
  default = true
}

variable "backend_port" {
  type    = number
  default = 8080
}

variable "metabase_port" {
  type    = number
  default = 3000
}
