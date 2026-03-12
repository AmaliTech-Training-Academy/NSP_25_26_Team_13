output "public_ip" {
  description = "Elastic IP of the bastion host"
  value       = aws_eip.bastion.public_ip
}

output "instance_id" {
  description = "EC2 instance ID of the bastion host"
  value       = aws_instance.bastion.id
}

output "ssh_command" {
  description = "Ready-made SSH tunnel command (replace <rds-endpoint> with the value of terraform output rds_endpoint)"
  value       = "ssh -i <your-key.pem> -L 5433:<rds-endpoint>:5432 -N -f ec2-user@${aws_eip.bastion.public_ip}"
}
