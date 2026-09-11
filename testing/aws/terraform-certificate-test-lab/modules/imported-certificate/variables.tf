# ---------------------------------------------------------------------------
# modules/imported-certificate
#
# Generates a THROWAWAY TEST key pair and X.509 certificate with the
# hashicorp/tls provider, and optionally imports it into ACM.
#
# TESTING NOTE / SECURITY NOTE
#   * Every key produced here is generated at apply time and is worthless
#     outside this lab. No real certificate or real private key is ever used.
#   * The private keys DO land in Terraform state. Use an encrypted remote
#     backend, or treat the local state file as a secret and never commit it.
#
# KNOWN AWS LIMITATION
#   ACM refuses to import an ALREADY EXPIRED certificate. The tls provider
#   also cannot back-date notBefore/notAfter. Expired-certificate scenarios
#   are therefore produced by the OpenSSL helper script and stored OUTSIDE
#   ACM (filesystem / S3 / Parameter Store). See scripts/.
# ---------------------------------------------------------------------------

variable "name" {
  description = "Logical name of the certificate scenario, e.g. phase1-cert-011."
  type        = string
}

variable "common_name" {
  description = "Subject CN of the generated certificate."
  type        = string
}

variable "dns_names" {
  description = "SAN dNSName entries. Include common_name here as well; modern TLS clients ignore CN. Wildcards are allowed."
  type        = list(string)
}

variable "organization" {
  description = "Subject organization of the generated certificate."
  type        = string
  default     = "Certificate Test Lab"
}

variable "organizational_unit" {
  description = "Subject OU of the generated certificate."
  type        = string
  default     = "Phase 1"
}

variable "country" {
  description = "Subject country code."
  type        = string
  default     = "IE"
}

variable "key_algorithm" {
  description = "Key algorithm for the generated key pair."
  type        = string
  default     = "RSA"

  validation {
    condition     = contains(["RSA", "ECDSA"], var.key_algorithm)
    error_message = "key_algorithm must be RSA or ECDSA."
  }
}

variable "rsa_bits" {
  description = "RSA modulus size. ACM import accepts 1024/2048/3072/4096."
  type        = number
  default     = 2048

  validation {
    condition     = contains([2048, 3072, 4096], var.rsa_bits)
    error_message = "rsa_bits must be 2048, 3072 or 4096 (1024 is intentionally disallowed in this lab)."
  }
}

variable "ecdsa_curve" {
  description = "EC curve when key_algorithm is ECDSA. ACM import accepts P256/P384/P521."
  type        = string
  default     = "P256"

  validation {
    condition     = contains(["P256", "P384", "P521"], var.ecdsa_curve)
    error_message = "ecdsa_curve must be P256, P384 or P521."
  }
}

variable "validity_hours" {
  description = "Certificate validity in hours from apply time. Drives the expiry test scenario (for example 168 = 7 days = CRITICAL)."
  type        = number
  default     = 8760

  validation {
    condition     = var.validity_hours >= 1
    error_message = "validity_hours must be at least 1. Expired certificates cannot be produced by the tls provider - use the OpenSSL script instead."
  }
}

variable "ca_certificate_pem" {
  description = "PEM of the lab test CA certificate. When both CA inputs are supplied the leaf is CA-signed, otherwise it is self-signed."
  type        = string
  default     = ""
}

variable "ca_private_key_pem" {
  description = "PEM of the lab test CA private key."
  type        = string
  default     = ""
  sensitive   = true
}

variable "ca_inputs_are_paired" {
  description = "Internal guard. Do not set. See the check block in main.tf."
  type        = bool
  default     = true
}

variable "import_to_acm" {
  description = "Import the generated material into ACM. Set false for scenarios whose only store is Secrets Manager / Parameter Store / S3 / EC2 filesystem / IAM."
  type        = bool
  default     = false
}

variable "acm_include_chain" {
  description = "Send the CA certificate as certificate_chain on ACM import. Required for CA-signed leaves; ignored for self-signed leaves. Set false if ACM ever rejects a self-signed root in the chain."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Tags applied to the ACM import, when created."
  type        = map(string)
  default     = {}
}
