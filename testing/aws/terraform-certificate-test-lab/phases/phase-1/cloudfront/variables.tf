# ---------------------------------------------------------------------------
# phases/phase-1/cloudfront
#
# SCENARIO phase1-cert-005 is NOT implemented in Phase 1. CloudFront only
# accepts an ACM certificate from us-east-1 and Phase 1 is eu-west-1 only.
# See README.md in this directory for the full explanation and the Phase 2
# design. The matrix entry is DEFERRED_TO_PHASE_2.
#
# What this module CAN create, optionally, is a distribution using the
# DEFAULT CloudFront certificate. That needs no ACM certificate in any
# region and gives the scanner a TLS-terminating service with NO certificate
# ARN - a valuable negative test case.
#
# Disabled by default: a distribution takes 5-15 minutes to create and the
# same to delete.
# ---------------------------------------------------------------------------

variable "name_prefix" {
  description = "Name prefix from modules/common."
  type        = string
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
}

variable "enable_cloudfront" {
  description = "Create a CloudFront distribution that uses the DEFAULT CloudFront certificate. No ACM certificate and no us-east-1 resource is involved."
  type        = bool
  default     = false
}

variable "origin_domain_name" {
  description = "Origin domain for the distribution. The lab passes the internal ALB DNS name; the origin is never actually reachable and does not need to be - only the certificate metadata matters."
  type        = string
  default     = null
}

variable "price_class" {
  description = "CloudFront price class. The cheapest class is used by default."
  type        = string
  default     = "PriceClass_100"
}
