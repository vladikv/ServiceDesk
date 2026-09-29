output "instance_id" {
  description = "EC2 instance ID for Systems Manager access."
  value       = aws_instance.servicedesk.id
}

output "availability_zone" {
  description = "Availability zone containing the EC2 instance."
  value       = aws_instance.servicedesk.availability_zone
}
