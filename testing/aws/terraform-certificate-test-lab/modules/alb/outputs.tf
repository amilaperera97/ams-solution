output "arn" {
  description = "ALB ARN."
  value       = aws_lb.this.arn
}

output "name" {
  description = "ALB name."
  value       = aws_lb.this.name
}

output "dns_name" {
  description = "ALB DNS name."
  value       = aws_lb.this.dns_name
}

output "target_group_arn" {
  description = "Target group ARN."
  value       = aws_lb_target_group.this.arn
}

output "http_listener_arn" {
  description = "HTTP listener ARN, or null when disabled. Has no certificate by design."
  value       = var.enable_http_listener ? aws_lb_listener.http[0].arn : null
}

output "https_listener_arn" {
  description = "HTTPS listener ARN - the discovery entry point for ALB certificate relationships."
  value       = aws_lb_listener.https.arn
}

output "https_listener_default_certificate_arn" {
  description = "Default certificate on the HTTPS listener."
  value       = aws_lb_listener.https.certificate_arn
}

output "https_listener_sni_certificate_arns" {
  description = "Additional SNI certificates attached to the HTTPS listener."
  value       = sort([for cert in aws_lb_listener_certificate.sni : cert.certificate_arn])
}
