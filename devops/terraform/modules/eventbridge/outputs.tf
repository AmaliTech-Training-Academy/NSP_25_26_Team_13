output "schedule_group_name" {
  value = aws_scheduler_schedule_group.main.name
}

output "schedule_names" {
  value = [for s in aws_scheduler_schedule.data_engineering : s.name]
}
