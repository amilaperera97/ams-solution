output "vpc_id" {
  description = "Lab VPC ID."
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "Lab VPC CIDR."
  value       = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "Public subnet IDs, ordered by AZ."
  value       = [for az in local.azs : aws_subnet.public[az].id]
}

output "private_subnet_ids" {
  description = "Private subnet IDs (empty unless enable_private_subnets = true)."
  value       = [for az in local.azs : aws_subnet.private[az].id if contains(keys(aws_subnet.private), az)]
}

output "availability_zones" {
  description = "AZs used by the lab."
  value       = local.azs
}

output "load_balancer_security_group_id" {
  description = "Security group for ALB/NLB."
  value       = aws_security_group.load_balancer.id
}

output "certificate_server_security_group_id" {
  description = "Security group for the EC2 certificate server."
  value       = aws_security_group.certificate_server.id
}

output "ssh_fallback_enabled" {
  description = "Whether an inbound port 22 rule exists."
  value       = length(local.ssh_cidrs) > 0
}
