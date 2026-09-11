# ---------------------------------------------------------------------------
# SCENARIOS phase1-cert-011 .. phase1-cert-020 (minus 018, which is an IAM
# server certificate and therefore lives in custom_certificates.tf).
#
# CUSTOMER-MANAGED certificates IMPORTED into ACM.
#
# Discovery expectations:
#   acm:ListCertificates / acm:DescribeCertificate
#   Type                = IMPORTED
#   RenewalEligibility  = INELIGIBLE  <- ACM never auto-renews an import
#   Classification      = customer-managed, MANUAL renewal required
#
# KNOWN AWS LIMITATION
#   ACM rejects the import of an already-expired certificate. There is
#   therefore no EXPIRED scenario in ACM anywhere in this lab; the expired
#   certificates live on the EC2 filesystem and in Parameter Store instead.
#   The nearest-to-expiry ACM import is phase1-cert-014 at 7 days.
# ---------------------------------------------------------------------------

module "acm_imported" {
  source   = "../../../modules/imported-certificate"
  for_each = local.acm_import_definitions

  name           = each.key
  common_name    = each.value.common_name
  dns_names      = each.value.dns_names
  key_algorithm  = each.value.key_algorithm
  rsa_bits       = each.value.rsa_bits
  ecdsa_curve    = each.value.ecdsa_curve
  validity_hours = each.value.validity_hours

  ca_certificate_pem = each.value.ca_signed ? tls_self_signed_cert.lab_ca.cert_pem : ""
  ca_private_key_pem = each.value.ca_signed ? tls_private_key.lab_ca.private_key_pem : ""

  import_to_acm     = true
  acm_include_chain = each.value.acm_include_chain

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}))
}
