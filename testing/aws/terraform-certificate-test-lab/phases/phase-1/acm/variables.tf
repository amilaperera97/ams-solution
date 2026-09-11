# ---------------------------------------------------------------------------
# phases/phase-1/acm
#
# This module owns ALL certificate MATERIAL for Phase 1:
#
#   aws_managed_certificates.tf  ACM-issued certificates            (001-010)
#   imported_certificates.tf     generated + imported into ACM      (011-020)
#   custom_certificates.tf       lab test CA + material kept OUTSIDE ACM
#                                (consumed by secrets/, s3/, compute/, iam/)
#   private_ca.tf                optional AWS Private CA
#   validation.tf                optional dedicated Route 53 test zone
#   openssl_generated.tf         artefacts Terraform cannot produce natively
#                                (back-dated EXPIRED certificates, PKCS#12)
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
  description = "Per-scenario tag maps, keyed by certificate id (phase1-cert-001 ...). Built in phases/phase-1/locals.tf."
  type        = map(map(string))
}

# --- ACM-issued -------------------------------------------------------------

variable "acm_issued_definitions" {
  description = "ACM-issued (AWS-managed) certificate requests, keyed by certificate id."
  type = map(object({
    domain_name               = string
    subject_alternative_names = optional(list(string), [])
    key_algorithm             = optional(string, "RSA_2048")
  }))
}

variable "acm_issuance_mode" {
  description = <<-EOT
    How ACM-issued certificates are produced:
      public_dns_validation - public certificates validated by DNS. They stay
                              PENDING_VALIDATION unless you really control the
                              domain. No extra cost.
      private_ca            - issued from an AWS Private CA created by this
                              lab. Status is ISSUED immediately with no DNS
                              dependency, and the certificates are attachable
                              to ALB/NLB/API Gateway. SIGNIFICANT COST.
  EOT
  type        = string

  validation {
    condition     = contains(["public_dns_validation", "private_ca"], var.acm_issuance_mode)
    error_message = "acm_issuance_mode must be public_dns_validation or private_ca."
  }
}

# --- Route 53 (optional) ----------------------------------------------------

variable "create_route53_test_zone" {
  description = "Create a DEDICATED public hosted zone for the test domain and write ACM DNS validation records into it. CREATES DNS INFRASTRUCTURE and costs 0.50 USD/month. Never used against an existing zone."
  type        = bool
  default     = false
}

variable "route53_test_zone_name" {
  description = "Zone name for the dedicated test hosted zone."
  type        = string
  default     = ""
}

variable "wait_for_acm_dns_validation" {
  description = "Block terraform apply until ACM reports ISSUED. Only true if the test zone is genuinely delegated from the registrar, otherwise apply hangs."
  type        = bool
  default     = false
}

# --- AWS Private CA (optional) ---------------------------------------------

variable "enable_private_ca" {
  description = "Create an AWS Private CA root. ONGOING COST roughly 400 USD/month for a GENERAL_PURPOSE CA (about 50 USD/month in SHORT_LIVED_CERTIFICATE mode) plus per-certificate charges. Off by default."
  type        = bool
  default     = false
}

variable "private_ca_common_name" {
  description = "Subject CN of the Private CA root."
  type        = string
  default     = "Certificate Test Lab Root CA"
}

variable "private_ca_usage_mode" {
  description = "AWS Private CA usage mode. SHORT_LIVED_CERTIFICATE is much cheaper but limits certificate validity to 7 days."
  type        = string
  default     = "GENERAL_PURPOSE"

  validation {
    condition     = contains(["GENERAL_PURPOSE", "SHORT_LIVED_CERTIFICATE"], var.private_ca_usage_mode)
    error_message = "private_ca_usage_mode must be GENERAL_PURPOSE or SHORT_LIVED_CERTIFICATE."
  }
}

variable "private_ca_permanent_deletion_time_in_days" {
  description = "Restore window after deleting the Private CA. 7 is the minimum and the cheapest for a disposable lab."
  type        = number
  default     = 7

  validation {
    condition     = var.private_ca_permanent_deletion_time_in_days >= 7 && var.private_ca_permanent_deletion_time_in_days <= 30
    error_message = "Must be between 7 and 30 days."
  }
}

# --- Locally generated material --------------------------------------------

variable "lab_ca_common_name" {
  description = "Subject CN of the LOCAL (non-AWS) test CA used to produce CA-signed test leaves."
  type        = string
  default     = "Certificate Test Lab Local Test CA"
}

variable "lab_ca_validity_hours" {
  description = "Validity of the local test CA certificate."
  type        = number
  default     = 43800 # ~5 years
}

variable "generated_certificate_definitions" {
  description = "Locally generated test certificates, keyed by certificate id. import_to_acm distinguishes the ACM imports (011-020) from material that only lives in Secrets Manager / Parameter Store / S3 / EC2 / IAM."
  type = map(object({
    common_name       = string
    dns_names         = list(string)
    key_algorithm     = optional(string, "RSA")
    rsa_bits          = optional(number, 2048)
    ecdsa_curve       = optional(string, "P256")
    validity_hours    = number
    ca_signed         = optional(bool, false)
    import_to_acm     = optional(bool, false)
    acm_include_chain = optional(bool, true)
  }))
}

# --- OpenSSL helper ---------------------------------------------------------

variable "enable_openssl_generated_certificates" {
  description = "Run scripts/generate-test-certificates.sh during apply to produce the artefacts Terraform cannot create natively: BACK-DATED EXPIRED certificates and PKCS#12 keystores. Requires bash + openssl on the machine running Terraform."
  type        = bool
  default     = true
}

variable "openssl_script_path" {
  description = "Absolute path to generate-test-certificates.sh."
  type        = string
}

variable "generated_certificates_dir" {
  description = "Absolute path of the gitignored directory the script writes into."
  type        = string
}

variable "openssl_generation_trigger" {
  description = "Change this value to force the OpenSSL helper to run again (for example to refresh a 30-day certificate)."
  type        = string
  default     = "v1"
}

variable "pkcs12_password" {
  description = "Password for generated PKCS#12 / JKS keystores. TEST VALUE ONLY - it is written to state and to the instance."
  type        = string
  sensitive   = true
}
