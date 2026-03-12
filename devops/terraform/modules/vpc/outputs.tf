output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "nat_gateway_ids" {
  value = aws_nat_gateway.main[*].id
}

output "sg_alb_id" {
  value = aws_security_group.alb.id
}

output "sg_backend_id" {
  value = aws_security_group.backend.id
}

output "sg_metabase_id" {
  value = aws_security_group.metabase.id
}

output "sg_data_engineering_id" {
  value = aws_security_group.data_engineering.id
}

output "sg_rds_id" {
  value = aws_security_group.rds.id
}

output "sg_bastion_id" {
  value = aws_security_group.bastion.id
}
