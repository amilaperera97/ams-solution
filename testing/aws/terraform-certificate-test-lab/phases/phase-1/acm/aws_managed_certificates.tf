# ---------------------------------------------------------------------------
# SCENARIOS phase1-cert-001 .. phase1-cert-010
# AWS-MANAGED / ACM-ISSUED certificates.
#
# Discovery expectations for these:
#   acm:ListCertificates / acm:DescribeCertificate
#   Type                = AMAZON_ISSUED (public) or PRIVATE (Private CA)
#   RenewalEligibility  = ELIGIBLE once ISSUED and in use
#   Classification      = AWS-managed, automatically renewable
#
# NOT IMPLEMENTED HERE
#   phase1-cert-005 (CloudFront) is absent on purpose. A CloudFront
#   distribution can only use an ACM certificate from us-east-1, and Phase 1
#   is eu-west-1 only. See ../cloudfront/cloudfront.tf and the test matrix
#   entry marked DEFERRED_TO_PHASE_2.
# ---------------------------------------------------------------------------

module "acm_issued" {
  source   = "../../../modules/acm-certificate"
  for_each = var.acm_issued_definitions

  name                      = each.key
  domain_name               = each.value.domain_name
  subject_alternative_names = each.value.subject_alternative_names
  key_algorithm             = each.value.key_algorithm

  # Public DNS validation, or immediate issuance from the lab Private CA.
  certificate_authority_arn = var.acm_issuance_mode == "private_ca" ? aws_acmpca_certificate_authority.root[0].arn : null
  validation_method         = "DNS"

  create_route53_validation_records = local.manage_test_zone
  route53_zone_id                   = local.manage_test_zone ? aws_route53_zone.test[0].zone_id : null
  wait_for_validation               = var.wait_for_acm_dns_validation

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}))

  # A private certificate cannot be issued until the CA has its own
  # certificate installed.
  depends_on = [aws_acmpca_certificate_authority_certificate.root]
}
