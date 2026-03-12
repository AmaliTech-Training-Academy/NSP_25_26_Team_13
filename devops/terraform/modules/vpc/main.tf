locals {
  # How many NAT gateways to create: 1 (single) or 1 per AZ (HA)
  nat_gateway_count = var.single_nat_gateway ? 1 : length(var.availability_zones)
}

# ── VPC ────────────────────────────────────────────────────────────────────
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "${var.project_name}-${var.environment}-vpc" }
}

# ── Public subnets (one per AZ) ────────────────────────────────────────────
resource "aws_subnet" "public" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-${var.environment}-public-${var.availability_zones[count.index]}"
    Tier = "public"
  }
}

# ── Private subnets (one per AZ) ───────────────────────────────────────────
resource "aws_subnet" "private" {
  count = length(var.availability_zones)

  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name = "${var.project_name}-${var.environment}-private-${var.availability_zones[count.index]}"
    Tier = "private"
  }
}

# ── Internet Gateway (public egress) ───────────────────────────────────────
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project_name}-${var.environment}-igw" }
}

# ── Elastic IPs for NAT Gateways ───────────────────────────────────────────
resource "aws_eip" "nat" {
  count  = local.nat_gateway_count
  domain = "vpc"

  depends_on = [aws_internet_gateway.main]
  tags       = { Name = "${var.project_name}-${var.environment}-nat-eip-${count.index + 1}" }
}

# ── NAT Gateways (in public subnets) ───────────────────────────────────────
resource "aws_nat_gateway" "main" {
  count = local.nat_gateway_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  depends_on = [aws_internet_gateway.main]
  tags       = { Name = "${var.project_name}-${var.environment}-nat-${count.index + 1}" }
}

# ── Public route table ─────────────────────────────────────────────────────
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project_name}-${var.environment}-rt-public" }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ── Private route tables (one per AZ, or one shared if single_nat_gateway) ─
resource "aws_route_table" "private" {
  count  = local.nat_gateway_count
  vpc_id = aws_vpc.main.id
  tags = {
    Name = "${var.project_name}-${var.environment}-rt-private-${count.index + 1}"
  }
}

resource "aws_route" "private_nat" {
  count = local.nat_gateway_count

  route_table_id         = aws_route_table.private[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.main[count.index].id
}

resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id = aws_subnet.private[count.index].id
  # When single_nat_gateway=true all private subnets share route_table[0]
  route_table_id = aws_route_table.private[var.single_nat_gateway ? 0 : count.index].id
}

# ── S3 Gateway Endpoint (free — avoids NAT charges for ECR layer pulls) ────
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = aws_route_table.private[*].id

  tags = { Name = "${var.project_name}-${var.environment}-vpce-s3" }
}

# ──────────────────────────────────────────────────────────────────────────
# Security Groups
# ──────────────────────────────────────────────────────────────────────────

# ALB — public-facing, allows HTTP (and HTTPS when cert is attached)
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-${var.environment}-sg-alb"
  description = "ALB - ingress HTTP/HTTPS from Internet"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTP from Internet"
  }

  # Port 443 ingress removed — no HTTPS listener configured yet.
  # Add back when ACM certificate and HTTPS listener are in place.

  # Metabase port (restrict to trusted CIDRs in prod)
  ingress {
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Metabase port from Internet"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound"
  }

  tags = { Name = "${var.project_name}-${var.environment}-sg-alb" }
}

# Backend ECS tasks — only from ALB
resource "aws_security_group" "backend" {
  name        = "${var.project_name}-${var.environment}-sg-backend"
  description = "Backend ECS tasks - ingress only from ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = var.backend_port
    to_port         = var.backend_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
    description     = "From ALB"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound - NAT to ECR, Secrets Manager, RDS"
  }

  tags = { Name = "${var.project_name}-${var.environment}-sg-backend" }
}

# Metabase ECS tasks — only from ALB on port 3000
resource "aws_security_group" "metabase" {
  name        = "${var.project_name}-${var.environment}-sg-metabase"
  description = "Metabase ECS tasks - ingress only from ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = var.metabase_port
    to_port         = var.metabase_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
    description     = "From ALB"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound"
  }

  tags = { Name = "${var.project_name}-${var.environment}-sg-metabase" }
}

# Data-engineering ECS tasks — no inbound, egress to RDS + internet (via NAT)
resource "aws_security_group" "data_engineering" {
  name        = "${var.project_name}-${var.environment}-sg-data-engineering"
  description = "Data-engineering Fargate tasks - no inbound"
  vpc_id      = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound"
  }

  tags = { Name = "${var.project_name}-${var.environment}-sg-data-engineering" }
}

# RDS — only from backend and data-engineering tasks
resource "aws_security_group" "rds" {
  name        = "${var.project_name}-${var.environment}-sg-rds"
  description = "RDS PostgreSQL - ingress from ECS tasks only"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.backend.id]
    description     = "From backend ECS"
  }

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.data_engineering.id]
    description     = "From data-engineering ECS"
  }

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.metabase.id]
    description     = "From Metabase ECS"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound"
  }

  tags = { Name = "${var.project_name}-${var.environment}-sg-rds" }
}
