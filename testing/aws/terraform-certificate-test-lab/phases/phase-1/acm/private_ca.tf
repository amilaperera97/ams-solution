# ---------------------------------------------------------------------------
# OPTIONAL: AWS Private CA (ACM PCA) root.
#
# WHY THIS EXISTS
#   A public ACM certificate only reaches ISSUED once its DNS validation
#   records resolve in public DNS, which requires you to own the domain. An
#   ALB/NLB/API Gateway will REFUSE a certificate that is not ISSUED.
#
#   AWS Private CA is the only way to obtain a genuinely ACM-MANAGED,
#   auto-renewing, ISSUED, attachable certificate with no DNS dependency.
#
# COST WARNING
#   A GENERAL_PURPOSE CA is billed at roughly 400 USD/month (pro-rated),
#   SHORT_LIVED_CERTIFICATE mode at roughly 50 USD/month, plus a per-issued
#   certificate charge. This is by far the most expensive thing in the lab
#   and is therefore disabled by default.
#
# DELETION
#   terraform destroy disables and schedules deletion of the CA with a
#   restore window of private_ca_permanent_deletion_time_in_days. You are
#   NOT billed for a CA in PENDING_DELETION/DISABLED state.
# ---------------------------------------------------------------------------

resource "aws_acmpca_certificate_authority" "root" {
  count = var.enable_private_ca ? 1 : 0

  type       = "ROOT"
  usage_mode = var.private_ca_usage_mode

  certificate_authority_configuration {
    key_algorithm     = "RSA_2048"
    signing_algorithm = "SHA256WITHRSA"

    subject {
      common_name  = var.private_ca_common_name
      organization = "Certificate Test Lab"
      country      = "IE"
    }
  }

  permanent_deletion_time_in_days = var.private_ca_permanent_deletion_time_in_days
  enabled                         = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-private-ca-root" })
}

# Self-sign the root CA's own CSR.
resource "aws_acmpca_certificate" "root" {
  count = var.enable_private_ca ? 1 : 0

  certificate_authority_arn   = aws_acmpca_certificate_authority.root[0].arn
  certificate_signing_request = aws_acmpca_certificate_authority.root[0].certificate_signing_request
  signing_algorithm           = "SHA256WITHRSA"
  template_arn                = "arn:${data.aws_partition.current.partition}:acm-pca:::template/RootCACertificate/V1"

  validity {
    type  = "YEARS"
    value = 10
  }
}

resource "aws_acmpca_certificate_authority_certificate" "root" {
  count = var.enable_private_ca ? 1 : 0

  certificate_authority_arn = aws_acmpca_certificate_authority.root[0].arn
  certificate               = aws_acmpca_certificate.root[0].certificate
}

# ACM needs explicit permission on the CA to issue and, critically, to
# AUTO-RENEW private certificates. Without this the certificates are issued
# but renewal_eligibility never becomes ELIGIBLE.
resource "aws_acmpca_permission" "acm" {
  count = var.enable_private_ca ? 1 : 0

  certificate_authority_arn = aws_acmpca_certificate_authority.root[0].arn
  actions                   = ["IssueCertificate", "GetCertificate", "ListPermissions"]
  principal                 = "acm.amazonaws.com"
}

data "aws_partition" "current" {}
