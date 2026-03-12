# ──────────────────────────────────────────────────────────────────────────────
# Bastion Host
#
# PURPOSE:
#   Provides a jump server in a public subnet so developers outside AWS
#   (e.g. local machines in Ghana) can reach the private RDS instance via
#   an SSH tunnel.
#
# HOW TO CONNECT (after apply):
#   1. Get the bastion public IP:
#        terraform output bastion_public_ip
#
#   2. Open the SSH tunnel (maps localhost:5433 → RDS:5432 through the bastion):
#        ssh -i <your-key.pem> \
#            -L 5433:<rds-endpoint>:5432 \
#            -N -f \
#            ec2-user@<bastion-public-ip>
#
#   3. Connect your DB client to localhost:5433 with your RDS credentials.
#        psql -h localhost -p 5433 -U <db_user> -d <db_name>
#        # or point DBeaver / TablePlus to host=localhost port=5433
#
# SECURITY:
#   - The bastion accepts SSH only on port 22 (controlled by sg_bastion in vpc module).
#   - RDS accepts port 5432 from the bastion SG only (not from the internet).
#   - In prod, restrict var.allowed_ssh_cidrs to your team's static IPs.
#   - Rotate the key pair if any team member leaves.
# ──────────────────────────────────────────────────────────────────────────────

# Latest Amazon Linux 2023 AMI (x86_64)
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# EC2 key pair — upload the public key from your local SSH key pair
resource "aws_key_pair" "bastion" {
  key_name   = "${var.project_name}-${var.environment}-bastion-key"
  public_key = var.bastion_public_key

  tags = { Name = "${var.project_name}-${var.environment}-bastion-key" }
}

# Bastion EC2 instance — t3.micro is free-tier eligible
resource "aws_instance" "bastion" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = var.bastion_instance_type
  subnet_id                   = var.public_subnet_id
  vpc_security_group_ids      = [var.sg_bastion_id]
  key_name                    = aws_key_pair.bastion.key_name
  associate_public_ip_address = true

  # Minimal user-data: harden SSH and print connection info to the console log
  user_data = <<-EOF
    #!/bin/bash
    # Disable password auth — key-only access
    sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
    sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
    systemctl reload sshd

    echo "LogStream bastion host ready — $(date)" >> /var/log/bastion-init.log
  EOF

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 30   # AL2023 AMI snapshot requires >= 30 GB
    delete_on_termination = true
    encrypted             = true
  }

  tags = { Name = "${var.project_name}-${var.environment}-bastion" }
}

# Elastic IP — keeps the same public IP across stop/start cycles
resource "aws_eip" "bastion" {
  instance = aws_instance.bastion.id
  domain   = "vpc"

  depends_on = [aws_instance.bastion]
  tags       = { Name = "${var.project_name}-${var.environment}-bastion-eip" }
}
