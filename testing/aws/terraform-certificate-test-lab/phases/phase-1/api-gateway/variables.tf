# ---------------------------------------------------------------------------
# phases/phase-1/api-gateway
#
# A MINIMAL REST API whose only purpose is to give a custom domain name
# something to map onto. There is no application logic: a single MOCK
# integration on the root resource. The objective is certificate
# relationship testing, not API testing.
#
# THE RELATIONSHIP BEING TESTED
#   apigateway:GetDomainNames
#     -> domainName.regionalCertificateArn  -> ACM certificate
#     -> apigateway:GetBasePathMappings     -> restApiId + stage
#
# KNOWN AWS LIMITATIONS, DOCUMENTED NOT FAKED
#   1. ENDPOINT TYPE DECIDES THE CERTIFICATE REGION.
#      A REGIONAL custom domain uses an ACM certificate from the SAME region
#      (eu-west-1 here) via regional_certificate_arn. An EDGE custom domain
#      requires the certificate in us-east-1 via certificate_arn. Phase 1 is
#      eu-west-1 only, so REGIONAL is the only valid choice and the EDGE
#      variant is deferred to Phase 2.
#   2. THE CERTIFICATE MUST BE ISSUED.
#      API Gateway rejects a PENDING_VALIDATION certificate, so the default
#      custom domain uses an ACM IMPORT (phase1-cert-019), which is ISSUED
#      immediately. The ACM-ISSUED variant (phase1-cert-004) is created only
#      when attach_acm_issued_certificates = true.
#   3. API Gateway does NOT verify DNS ownership of a custom domain name. It
#      only requires that the certificate covers the name. Creating the
#      domain therefore works without you controlling public DNS - but no
#      client can resolve it, which is fine: discovery reads the API, not
#      the network.
#
# COST: a REST API custom domain name has no hourly charge. Requests are
# billed per million and this lab makes none.
# ---------------------------------------------------------------------------

variable "name_prefix" {
  description = "Name prefix from modules/common."
  type        = string
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
}

variable "certificate_tags" {
  description = "Per-scenario tag maps keyed by certificate id."
  type        = map(map(string))
}

variable "enable_api_gateway" {
  description = "Create the minimal REST API and its custom domain name(s)."
  type        = bool
  default     = true
}

variable "stage_name" {
  description = "Stage name the custom domain base path maps to."
  type        = string
  default     = "test"
}

variable "security_policy" {
  description = "Minimum TLS version for the custom domain endpoint."
  type        = string
  default     = "TLS_1_2"

  validation {
    condition     = contains(["TLS_1_0", "TLS_1_2"], var.security_policy)
    error_message = "security_policy must be TLS_1_0 or TLS_1_2."
  }
}

variable "imported_domain_name" {
  description = "Custom domain name backed by the ACM IMPORT (phase1-cert-019). Must be covered by that certificate."
  type        = string
}

variable "imported_certificate_arn" {
  description = "ACM ARN of the import backing imported_domain_name."
  type        = string
}

variable "imported_certificate_id" {
  description = "Certificate scenario id for tagging."
  type        = string
  default     = "phase1-cert-019"
}

variable "acm_issued_domain_name" {
  description = "Custom domain name backed by the ACM-ISSUED certificate (phase1-cert-004). Only used when acm_issued_certificate_arn is non-null."
  type        = string
  default     = null
}

variable "acm_issued_certificate_arn" {
  description = "ACM ARN of the ACM-issued certificate backing acm_issued_domain_name, or null to skip that domain."
  type        = string
  default     = null
}

variable "acm_issued_certificate_id" {
  description = "Certificate scenario id for tagging."
  type        = string
  default     = "phase1-cert-004"
}
