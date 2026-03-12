output "alb_arn" {
  value = aws_lb.main.arn
}

output "alb_dns_name" {
  description = "Your app URL — http://<this value>"
  value       = aws_lb.main.dns_name
}

output "alb_url" {
  description = "Full HTTP URL to access the application"
  value       = "http://${aws_lb.main.dns_name}"
}

output "alb_zone_id" {
  value = aws_lb.main.zone_id
}

output "http_listener_arn" {
  value = aws_lb_listener.http.arn
}

output "test_listener_arn" {
  description = "Port 8080 listener used by CodeDeploy to validate the green fleet"
  value       = aws_lb_listener.test.arn
}

output "backend_blue_tg_arn" {
  value = aws_lb_target_group.backend_blue.arn
}

output "backend_green_tg_arn" {
  value = aws_lb_target_group.backend_green.arn
}

output "backend_blue_tg_name" {
  value = aws_lb_target_group.backend_blue.name
}

output "backend_green_tg_name" {
  value = aws_lb_target_group.backend_green.name
}

output "metabase_tg_arn" {
  value = aws_lb_target_group.metabase.arn
}
