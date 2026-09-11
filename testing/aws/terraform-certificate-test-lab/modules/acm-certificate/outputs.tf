output "arn" {
  description = "ARN of the ACM certificate."
  value       = aws_acm_certificate.this.arn
}

output "id" {
  description = "ACM certificate ID (same as ARN for this resource type)."
  value       = aws_acm_certificate.this.id
}

output "domain_name" {
  description = "Primary domain name of the certificate."
  value       = aws_acm_certificate.this.domain_name
}

output "subject_alternative_names" {
  description = "SANs reported by ACM (includes the primary domain)."
  value       = aws_acm_certificate.this.subject_alternative_names
}

output "status" {
  description = "ACM status. PENDING_VALIDATION for public certificates whose DNS records are not resolvable."
  value       = aws_acm_certificate.this.status
}

output "not_after" {
  description = "Expiry timestamp reported by ACM. Null until the certificate is ISSUED."
  value       = aws_acm_certificate.this.not_after
}

output "type" {
  description = "ACM certificate type, AMAZON_ISSUED or PRIVATE."
  value       = aws_acm_certificate.this.type
}

output "renewal_eligibility" {
  description = "ACM renewal eligibility, ELIGIBLE or INELIGIBLE."
  value       = aws_acm_certificate.this.renewal_eligibility
}

output "domain_validation_options" {
  description = "DNS validation records that must exist in public DNS for a public certificate to become ISSUED."
  value       = aws_acm_certificate.this.domain_validation_options
}

output "validation_record_fqdns" {
  description = "FQDNs of the Route 53 validation records, when this module created them."
  value       = [for record in aws_route53_record.validation : record.fqdn]
}
