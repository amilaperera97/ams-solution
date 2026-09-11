output "alb_arn" {
  description = "ALB ARN, or null when disabled."
  value       = local.alb_arn
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = one(module.alb[*].dns_name)
}

output "alb_https_listener_arn" {
  description = "ALB HTTPS listener ARN."
  value       = local.alb_https_listener_arn
}

output "alb_http_listener_arn" {
  description = "ALB HTTP listener ARN. Carries no certificate by design."
  value       = local.alb_http_listener_arn
}

output "alb_default_certificate_arn" {
  description = "Default certificate on the ALB HTTPS listener."
  value       = local.alb_default_certificate
}

output "alb_sni_certificate_arns" {
  description = "Additional SNI certificates on the ALB HTTPS listener."
  value       = local.alb_sni_certificates
}

output "nlb_arn" {
  description = "NLB ARN, or null when disabled."
  value       = local.nlb_arn
}

output "nlb_dns_name" {
  description = "NLB DNS name."
  value       = one(aws_lb.nlb[*].dns_name)
}

output "nlb_tls_listener_arn" {
  description = "NLB TLS listener ARN."
  value       = local.nlb_tls_listener_arn
}

output "nlb_tcp_listener_arn" {
  description = "NLB TCP listener ARN. Carries no certificate by design."
  value       = local.nlb_tcp_listener_arn
}

output "nlb_default_certificate_arn" {
  description = "Default certificate on the NLB TLS listener."
  value       = local.nlb_default_certificate
}

output "nlb_sni_certificate_arns" {
  description = "Additional SNI certificates on the NLB TLS listener."
  value       = local.nlb_sni_certificates
}

output "listener_certificate_relationships" {
  description = "Flattened listener-to-certificate relationships, the exact shape the discovery application must reconstruct. Certificate-free listeners are included on purpose as negative test cases."
  value = [
    for relationship in local.listener_relationship_candidates : {
      load_balancer_type      = relationship.load_balancer_type
      load_balancer_arn       = relationship.load_balancer_arn
      listener_arn            = relationship.listener_arn
      listener_protocol       = relationship.listener_protocol
      listener_port           = relationship.listener_port
      terminates_tls          = relationship.terminates_tls
      default_certificate_arn = relationship.default_certificate_arn
      certificate_arns        = relationship.certificate_arns
      certificate_count       = relationship.certificate_count
    }
    if relationship.exists
  ]
}
