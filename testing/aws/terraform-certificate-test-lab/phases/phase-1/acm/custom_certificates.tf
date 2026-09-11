# ---------------------------------------------------------------------------
# The LOCAL test CA, plus every locally generated certificate that does NOT
# go into ACM.
#
# These back the non-ACM certificate stores, which is exactly the
# "certificates stored outside ACM" part of the discovery matrix:
#   phase1-cert-018  IAM server certificate
#   phase1-cert-021  Secrets Manager
#   phase1-cert-022  Secrets Manager
#   phase1-cert-023  SSM Parameter Store
#   phase1-cert-025  S3 (PEM / CRT / chain)
#   phase1-cert-027  EC2 filesystem, nginx
#   phase1-cert-028  EC2 filesystem, Apache
#   phase1-cert-029  EC2 filesystem, Java keystore
#
# SECURITY: every key here is generated at apply time, exists only for this
# lab, and is worthless. They are still real private keys as far as the
# Terraform state file is concerned - keep state encrypted and out of git.
# ---------------------------------------------------------------------------

# --- Local test CA ("CA-signed test certificate" scenarios) ----------------
resource "tls_private_key" "lab_ca" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "tls_self_signed_cert" "lab_ca" {
  private_key_pem = tls_private_key.lab_ca.private_key_pem

  validity_period_hours = var.lab_ca_validity_hours
  is_ca_certificate     = true
  set_subject_key_id    = true

  allowed_uses = [
    "cert_signing",
    "crl_signing",
    "digital_signature",
  ]

  subject {
    common_name         = var.lab_ca_common_name
    organization        = "Certificate Test Lab"
    organizational_unit = "Phase 1"
    country             = "IE"
  }
}

locals {
  # Split the generated definitions by destination so each concern gets its
  # own file-level story while still being a single source of truth.
  non_acm_definitions = {
    for id, definition in var.generated_certificate_definitions : id => definition
    if !definition.import_to_acm
  }

  acm_import_definitions = {
    for id, definition in var.generated_certificate_definitions : id => definition
    if definition.import_to_acm
  }
}

# --- Generated material that stays OUTSIDE ACM -----------------------------
module "non_acm_certificates" {
  source   = "../../../modules/imported-certificate"
  for_each = local.non_acm_definitions

  name           = each.key
  common_name    = each.value.common_name
  dns_names      = each.value.dns_names
  key_algorithm  = each.value.key_algorithm
  rsa_bits       = each.value.rsa_bits
  ecdsa_curve    = each.value.ecdsa_curve
  validity_hours = each.value.validity_hours

  ca_certificate_pem = each.value.ca_signed ? tls_self_signed_cert.lab_ca.cert_pem : ""
  ca_private_key_pem = each.value.ca_signed ? tls_private_key.lab_ca.private_key_pem : ""

  import_to_acm = false

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}))
}
