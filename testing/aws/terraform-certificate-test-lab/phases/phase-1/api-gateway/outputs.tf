output "rest_api_id" {
  description = "REST API ID, or null when disabled."
  value       = local.create ? aws_api_gateway_rest_api.this[0].id : null
}

output "stage_name" {
  description = "Stage the custom domains map to."
  value       = local.create ? aws_api_gateway_stage.this[0].stage_name : null
}

output "stage_invoke_url" {
  description = "Default execute-api invoke URL (uses the AWS-managed *.execute-api certificate, not one of the lab certificates)."
  value       = local.create ? aws_api_gateway_stage.this[0].invoke_url : null
}

output "custom_domains" {
  description = "Custom domain name to certificate relationships created by this module."
  value = [
    for domain in local.custom_domain_candidates : {
      certificate_id           = domain.certificate_id
      domain_name              = domain.domain_name
      regional_certificate_arn = domain.regional_certificate_arn
      regional_domain_name     = domain.regional_domain_name
      endpoint_type            = domain.endpoint_type
      security_policy          = domain.security_policy
      base_path_mapped_stage   = domain.base_path_mapped_stage
    }
    if domain.exists
  ]
}

output "acm_issued_domain_created" {
  description = "Whether the ACM-issued-backed custom domain was created."
  value       = local.create_acm_issued_domain
}
