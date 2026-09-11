# ---------------------------------------------------------------------------
# phases/phase-1/iam
#
# SCENARIO phase1-cert-018 - the IAM SERVER CERTIFICATE store.
#
# THIS IS A REAL, SUPPORTED AWS RESOURCE - NOT A MOCK
#   aws_iam_server_certificate maps to iam:UploadServerCertificate. It is the
#   legacy certificate store that predates ACM and is still very much present
#   in older estates, which makes it a genuine discovery target.
#
# LIMITATIONS YOU MUST KNOW (documented, not faked)
#   1. IAM is a GLOBAL service. The certificate has no AWS Region; its ARN is
#      arn:aws:iam::<account>:server-certificate/<path><name>. It is NOT an
#      eu-west-1 resource and it is NOT a us-east-1 resource. Nothing in this
#      module violates the Phase 1 "eu-west-1 only" rule.
#   2. An IAM server certificate can only be consumed by a CLASSIC Load
#      Balancer or by CloudFront. ALB, NLB and API Gateway require ACM. This
#      lab therefore leaves it INTENTIONALLY UNATTACHED rather than creating
#      a Classic Load Balancer just to consume it. Attaching it to a CLB is
#      a Phase 4 scenario.
#   3. IAM rejects an expired certificate on upload, exactly like ACM, so
#      this scenario is HEALTHY/long-lived.
#   4. Discovery uses iam:ListServerCertificates +
#      iam:GetServerCertificate - a completely different API surface from
#      ACM, which is the main reason this scenario exists.
#   5. Renewal is fully MANUAL: delete and re-upload. There is no renewal
#      mechanism of any kind.
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

variable "enable_iam_server_certificate" {
  description = "Create the IAM server certificate scenario. Free of charge."
  type        = bool
  default     = true
}

variable "certificate_id" {
  description = "Certificate id backing this scenario."
  type        = string
  default     = "phase1-cert-018"
}

variable "iam_path" {
  description = "IAM path for the server certificate. A dedicated path makes the lab certificates trivially filterable and keeps them away from anything pre-existing."
  type        = string
  default     = "/certificate-test-lab/"

  validation {
    condition     = startswith(var.iam_path, "/") && endswith(var.iam_path, "/")
    error_message = "iam_path must start and end with /."
  }
}

variable "generated_material" {
  description = "PEM material from the acm module. SENSITIVE."
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
