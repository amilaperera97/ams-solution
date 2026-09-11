# ---------------------------------------------------------------------------
# OPTIONAL: dedicated Route 53 public hosted zone for the lab test domain.
#
# READ THIS BEFORE ENABLING
#   * This creates a BRAND NEW hosted zone. It never reads, imports or
#     modifies an existing zone, and it never touches a production zone.
#   * Creating the zone alone does NOT make ACM DNS validation succeed. ACM
#     resolves the validation CNAME through the PUBLIC DNS hierarchy, so the
#     domain's registrar must delegate NS records to this zone's name
#     servers. Until you do that, the certificates stay PENDING_VALIDATION.
#   * Cost: 0.50 USD/month per hosted zone, plus query charges.
#
#   If you do NOT own a public domain, leave create_route53_test_zone = false
#   and use one of the two supported alternatives:
#     a) accept PENDING_VALIDATION for scenarios 001-010 (default; the
#        PENDING_VALIDATION status is itself a useful discovery test case),
#        while every ATTACHED certificate comes from an ACM import, or
#     b) set acm_issuance_mode = "private_ca" for real ISSUED ACM-managed
#        certificates with no DNS dependency (see private_ca.tf).
# ---------------------------------------------------------------------------

locals {
  route53_zone_name = var.route53_test_zone_name
  manage_test_zone  = var.acm_issuance_mode == "public_dns_validation" && var.create_route53_test_zone
}

resource "aws_route53_zone" "test" {
  count = local.manage_test_zone ? 1 : 0

  name    = local.route53_zone_name
  comment = "Dedicated disposable zone for the certificate test lab - ${var.name_prefix}"

  # Disposable lab: allow destroy even if records remain.
  force_destroy = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-test-zone" })
}

# Guard rail: catch the configuration that silently does nothing useful.
check "acm_validation_configuration_is_coherent" {
  assert {
    condition     = !(var.wait_for_acm_dns_validation && !local.manage_test_zone)
    error_message = "wait_for_acm_dns_validation = true requires acm_issuance_mode = \"public_dns_validation\" AND create_route53_test_zone = true, otherwise there are no validation records to wait on."
  }

  assert {
    condition     = !(var.create_route53_test_zone && var.route53_test_zone_name == "")
    error_message = "create_route53_test_zone = true requires a non-empty route53_test_zone_name."
  }

  assert {
    condition     = !(var.acm_issuance_mode == "private_ca" && !var.enable_private_ca)
    error_message = "acm_issuance_mode = \"private_ca\" requires enable_private_ca = true."
  }
}
