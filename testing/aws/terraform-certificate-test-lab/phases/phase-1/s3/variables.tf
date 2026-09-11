# ---------------------------------------------------------------------------
# phases/phase-1/s3
#
# One dedicated, PRIVATE bucket with two distinct roles:
#
#   certificates/   discovery targets - scenarios phase1-cert-025 (PEM/CRT/
#                   chain) and phase1-cert-026 (PKCS#12). This is the
#                   "certificate stored in S3" test case.
#
#   ec2-staging/    bootstrap material the EC2 certificate server downloads
#                   with its instance role. Keeping it here rather than in
#                   user-data means no private key is ever visible in
#                   instance metadata.
#
# A brand new bucket is always created. No existing bucket is ever touched.
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

variable "bucket_name" {
  description = "Globally unique bucket name. Generated in phases/phase-1/locals.tf with a random suffix."
  type        = string
}

variable "force_destroy" {
  description = "Allow terraform destroy to delete a non-empty bucket. True by default because this lab is disposable."
  type        = bool
  default     = true
}

variable "enable_versioning" {
  description = "Enable bucket versioning. Useful for testing 'multiple versions of the same certificate object'."
  type        = bool
  default     = true
}

variable "certificate_prefix" {
  description = "Key prefix for the certificate-store discovery targets."
  type        = string
  default     = "certificates"
}

variable "ec2_staging_prefix" {
  description = "Key prefix for EC2 bootstrap material."
  type        = string
  default     = "ec2-staging"
}

variable "store_certificate_ids" {
  description = "Certificate ids from generated_material stored as S3 discovery targets."
  type        = list(string)
  default     = []
}

variable "store_openssl_certificate_ids" {
  description = "Certificate ids from openssl_material stored as S3 discovery targets."
  type        = list(string)
  default     = []
}

variable "pkcs12_certificate_id" {
  description = "Certificate id whose PKCS#12 keystore is uploaded, or null when the OpenSSL helper is disabled."
  type        = string
  default     = null
}

variable "staging_certificate_ids" {
  description = "Certificate ids from generated_material staged for the EC2 certificate server."
  type        = list(string)
  default     = []
}

variable "staging_openssl_certificate_ids" {
  description = "Certificate ids from openssl_material staged for the EC2 certificate server."
  type        = list(string)
  default     = []
}

variable "ca_signed_certificate_ids" {
  description = "Certificate ids that have a real chain, so chain.pem is only written where it is meaningful. Non-sensitive on purpose: Terraform cannot use a sensitive value in for_each."
  type        = list(string)
  default     = []
}

variable "generated_material" {
  description = "PEM material from the acm module (tls provider). SENSITIVE."
  type = map(object({
    certificate_pem = string
    fullchain_pem   = string
    chain_pem       = string
    private_key_pem = string
    not_after       = string
    ca_signed       = bool
  }))
  sensitive = true
}

variable "openssl_material" {
  description = "PEM material from the OpenSSL helper. SENSITIVE."
  type = map(object({
    certificate_pem = string
    private_key_pem = string
    chain_pem       = string
  }))
  default   = {}
  sensitive = true
}

variable "pkcs12_base64" {
  description = "Base64 encoded PKCS#12 keystore. SENSITIVE."
  type        = string
  default     = null
  sensitive   = true
}
