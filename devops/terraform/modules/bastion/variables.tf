variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "public_subnet_id" {
  description = "ID of a public subnet to place the bastion in"
  type        = string
}

variable "sg_bastion_id" {
  description = "Security group ID for the bastion host (from vpc module)"
  type        = string
}

variable "bastion_instance_type" {
  description = "EC2 instance type for the bastion host"
  type        = string
  default     = "t3.micro"
}

variable "bastion_public_key" {
  description = "SSH public key to install on the bastion host (paste the contents of your ~/.ssh/id_rsa.pub or id_ed25519.pub)"
  type        = string
  sensitive   = true
}
