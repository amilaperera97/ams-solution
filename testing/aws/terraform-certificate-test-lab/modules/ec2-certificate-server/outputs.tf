output "instance_id" {
  description = "EC2 instance ID - this is the target for ssm:SendCommand."
  value       = aws_instance.this.id
}

output "instance_arn" {
  description = "EC2 instance ARN."
  value       = aws_instance.this.arn
}

output "private_ip" {
  description = "Private IPv4 address."
  value       = aws_instance.this.private_ip
}

output "public_ip" {
  description = "Public IPv4 address, when one was assigned."
  value       = aws_instance.this.public_ip
}

output "availability_zone" {
  description = "AZ the instance runs in."
  value       = aws_instance.this.availability_zone
}
