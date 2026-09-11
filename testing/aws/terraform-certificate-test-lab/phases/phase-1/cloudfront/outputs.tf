output "distribution_id" {
  description = "CloudFront distribution ID, or null when not created."
  value       = local.create ? aws_cloudfront_distribution.default_certificate[0].id : null
}

output "distribution_arn" {
  description = "CloudFront distribution ARN, or null when not created."
  value       = local.create ? aws_cloudfront_distribution.default_certificate[0].arn : null
}

output "distribution_domain_name" {
  description = "The *.cloudfront.net domain name covered by the default certificate."
  value       = local.create ? aws_cloudfront_distribution.default_certificate[0].domain_name : null
}

output "uses_default_certificate" {
  description = "Always true when created - this module never attaches an ACM certificate."
  value       = local.create
}

output "phase_1_limitation" {
  description = "Machine-readable record of why the ACM-backed CloudFront scenario is absent from Phase 1."
  value = {
    certificate_id = "phase1-cert-005"
    status         = "DEFERRED_TO_PHASE_2"
    reason         = "A CloudFront distribution can only use an ACM certificate issued in us-east-1. Phase 1 is eu-west-1 only, so no valid configuration exists. See phases/phase-1/cloudfront/README.md."
  }
}
