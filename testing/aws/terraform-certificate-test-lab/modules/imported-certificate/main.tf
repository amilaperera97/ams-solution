locals {
  # Derived from the CA CERTIFICATE, never from the CA PRIVATE KEY. The key
  # is a sensitive variable, and branching on it would mark every derived
  # value (certificate_pem, not_after, is_ca_signed) as sensitive too, which
  # would make the certificate inventory unreadable. The CA certificate is
  # public material, so it is the safe discriminator.
  ca_signed = var.ca_certificate_pem != ""

  # key_encipherment is an RSA-only key usage. Advertising it on an ECDSA
  # certificate is meaningless, so build the allowed_uses list per algorithm.
  leaf_allowed_uses = var.key_algorithm == "RSA" ? [
    "digital_signature",
    "key_encipherment",
    "server_auth",
    "client_auth",
    ] : [
    "digital_signature",
    "key_agreement",
    "server_auth",
    "client_auth",
  ]
}

# A CA certificate without its key cannot sign anything. Catch that at plan
# time instead of failing inside the tls provider.
check "ca_inputs_are_paired" {
  assert {
    condition     = (var.ca_certificate_pem == "") == (var.ca_private_key_pem == "")
    error_message = "ca_certificate_pem and ca_private_key_pem must both be supplied, or both be empty. Supplying only one produces a certificate that cannot be signed."
  }
}

resource "tls_private_key" "this" {
  algorithm   = var.key_algorithm
  rsa_bits    = var.key_algorithm == "RSA" ? var.rsa_bits : null
  ecdsa_curve = var.key_algorithm == "ECDSA" ? var.ecdsa_curve : null
}

# ---------------------------------------------------------------------------
# Self-signed leaf (no lab CA supplied).
# ---------------------------------------------------------------------------
resource "tls_self_signed_cert" "this" {
  count = local.ca_signed ? 0 : 1

  private_key_pem       = tls_private_key.this.private_key_pem
  dns_names             = var.dns_names
  validity_period_hours = var.validity_hours
  allowed_uses          = local.leaf_allowed_uses
  is_ca_certificate     = false
  set_subject_key_id    = true

  subject {
    common_name         = var.common_name
    organization        = var.organization
    organizational_unit = var.organizational_unit
    country             = var.country
  }
}

# ---------------------------------------------------------------------------
# CA-signed leaf (lab CA supplied) - gives the scanner a realistic
# "CA-signed test certificate" with a chain to verify.
# ---------------------------------------------------------------------------
resource "tls_cert_request" "this" {
  count = local.ca_signed ? 1 : 0

  private_key_pem = tls_private_key.this.private_key_pem
  dns_names       = var.dns_names

  subject {
    common_name         = var.common_name
    organization        = var.organization
    organizational_unit = var.organizational_unit
    country             = var.country
  }
}

resource "tls_locally_signed_cert" "this" {
  count = local.ca_signed ? 1 : 0

  cert_request_pem      = tls_cert_request.this[0].cert_request_pem
  ca_private_key_pem    = var.ca_private_key_pem
  ca_cert_pem           = var.ca_certificate_pem
  validity_period_hours = var.validity_hours
  allowed_uses          = local.leaf_allowed_uses
  set_subject_key_id    = true
}

locals {
  certificate_pem = local.ca_signed ? tls_locally_signed_cert.this[0].cert_pem : tls_self_signed_cert.this[0].cert_pem
  chain_pem       = local.ca_signed ? var.ca_certificate_pem : ""

  validity_end_time   = local.ca_signed ? tls_locally_signed_cert.this[0].validity_end_time : tls_self_signed_cert.this[0].validity_end_time
  validity_start_time = local.ca_signed ? tls_locally_signed_cert.this[0].validity_start_time : tls_self_signed_cert.this[0].validity_start_time

  # Full-chain bundle, the shape most servers (nginx / HAProxy) expect.
  fullchain_pem = local.ca_signed ? "${local.certificate_pem}${var.ca_certificate_pem}" : local.certificate_pem
}

# ---------------------------------------------------------------------------
# Optional ACM import - this is what makes the certificate discoverable via
# the ACM ListCertificates / DescribeCertificate APIs with Type = IMPORTED.
# ---------------------------------------------------------------------------
resource "aws_acm_certificate" "imported" {
  count = var.import_to_acm ? 1 : 0

  private_key       = tls_private_key.this.private_key_pem
  certificate_body  = local.certificate_pem
  certificate_chain = local.ca_signed && var.acm_include_chain ? var.ca_certificate_pem : null

  # Justified: an imported certificate attached to a listener cannot be
  # deleted in place, so replacements must be created first.
  lifecycle {
    create_before_destroy = true
  }

  tags = merge(var.tags, { Name = var.name })
}
