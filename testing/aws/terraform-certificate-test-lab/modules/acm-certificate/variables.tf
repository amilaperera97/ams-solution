# ---------------------------------------------------------------------------
# modules/acm-certificate
#
# Requests an ACM-MANAGED (ACM-issued) certificate. Two mutually exclusive
# issuance models are supported:
#
#   1. PUBLIC + DNS validation (certificate_authority_arn = null)
#      ACM creates a public certificate. It stays in PENDING_VALIDATION until
#      the DNS validation CNAMEs resolve in *public* DNS. That requires you to
#      actually control the domain. See phases/phase-1/README.md.
#
#   2. PRIVATE via AWS Private CA (certificate_authority_arn set)
#      ACM issues the certificate immediately from your own private CA. No
#      DNS validation, status is ISSUED within seconds, and the certificate is
#      genuinely ACM-managed with automatic renewal. This is the only way to
#      get a real ISSUED + attachable ACM-issued certificate without owning a
#      public DNS zone. AWS Private CA has a significant monthly charge.
# ---------------------------------------------------------------------------

variable "name" {
  description = "Logical name of the certificate scenario, e.g. phase1-cert-001. Used in the Name tag."
  type        = string
}

variable "domain_name" {
  description = "Primary (CN) domain name requested for the certificate."
  type        = string
}

variable "subject_alternative_names" {
  description = "Additional SANs requested for the certificate. May include wildcards."
  type        = list(string)
  default     = []
}

variable "key_algorithm" {
  description = "ACM key algorithm. RSA_1024/RSA_2048/RSA_3072/RSA_4096 or EC_prime256v1/EC_secp384r1/EC_secp521r1."
  type        = string
  default     = "RSA_2048"

  validation {
    condition = contains([
      "RSA_1024", "RSA_2048", "RSA_3072", "RSA_4096",
      "EC_prime256v1", "EC_secp384r1", "EC_secp521r1",
    ], var.key_algorithm)
    error_message = "key_algorithm must be an ACM supported key algorithm."
  }
}

variable "validation_method" {
  description = "Validation method for PUBLIC certificates. DNS is strongly preferred; EMAIL requires a mailbox on the domain."
  type        = string
  default     = "DNS"

  validation {
    condition     = contains(["DNS", "EMAIL"], var.validation_method)
    error_message = "validation_method must be DNS or EMAIL."
  }
}

variable "certificate_authority_arn" {
  description = "ARN of an AWS Private CA. When set, ACM issues a PRIVATE certificate from that CA and no domain validation is performed."
  type        = string
  default     = null
}

variable "certificate_transparency_logging_preference" {
  description = "Certificate Transparency logging preference. Only applicable to PUBLIC certificates."
  type        = string
  default     = "ENABLED"

  validation {
    condition     = contains(["ENABLED", "DISABLED"], var.certificate_transparency_logging_preference)
    error_message = "Must be ENABLED or DISABLED."
  }
}

variable "create_route53_validation_records" {
  description = "Create Route 53 CNAME validation records in route53_zone_id. Only meaningful for PUBLIC DNS validated certificates."
  type        = bool
  default     = false
}

variable "route53_zone_id" {
  description = "Zone ID of the DEDICATED test hosted zone in which validation records are created. Never point this at a production zone."
  type        = string
  default     = null
}

variable "route53_validation_record_ttl" {
  description = "TTL for generated Route 53 validation records."
  type        = number
  default     = 60
}

variable "wait_for_validation" {
  description = "Create aws_acm_certificate_validation and BLOCK terraform apply until ACM reports ISSUED. Only enable when the zone is really delegated in public DNS, otherwise apply hangs until timeout."
  type        = bool
  default     = false
}

variable "validation_timeout" {
  description = "Timeout for the optional validation wait."
  type        = string
  default     = "20m"
}

variable "tags" {
  description = "Tags applied to the certificate."
  type        = map(string)
  default     = {}
}
