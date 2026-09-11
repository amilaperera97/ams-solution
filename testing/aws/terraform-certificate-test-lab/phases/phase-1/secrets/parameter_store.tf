# ---------------------------------------------------------------------------
# SCENARIOS phase1-cert-023, phase1-cert-024
# Certificate material held in SSM Parameter Store.
#
# WHY PARAMETER STORE IS IN THIS LAB
#   Parameter Store is a very common place to find certificates in real
#   estates, even though ACM or Secrets Manager are the better choice for
#   production certificate lifecycle management (rotation hooks, resource
#   policies, cross-account sharing, automatic renewal). It is included here
#   precisely BECAUSE discovery tooling must cope with it.
#
# LAYOUT (one parameter per artefact, standard tier, free)
#   /certificate-test/phase1/cert-023/certificate    String
#   /certificate-test/phase1/cert-023/chain          String
#   /certificate-test/phase1/cert-023/private-key    SecureString
#
# SecureString is used ONLY for private keys. Public certificate bodies are
# plain String on purpose - that is both realistic and what makes the
# "certificate stored in clear text" finding testable.
#
# phase1-cert-024 is the EXPIRED scenario. It cannot live in ACM because ACM
# refuses to import an expired certificate, so Parameter Store hosts it.
# ---------------------------------------------------------------------------

locals {
  parameter_generated_ids = var.enable_parameter_store ? toset(var.parameter_store_certificate_ids) : toset([])
  parameter_openssl_ids   = var.enable_parameter_store ? toset(var.parameter_store_openssl_certificate_ids) : toset([])

  parameter_path = { for id in setunion(local.parameter_generated_ids, local.parameter_openssl_ids) :
    id => "${var.store_path_prefix}/${replace(id, "phase1-cert-", "cert-")}"
  }
}

# --- Generated (tls provider) material -------------------------------------
resource "aws_ssm_parameter" "certificate" {
  for_each = local.parameter_generated_ids

  name        = "${local.parameter_path[each.key]}/certificate"
  description = "Certificate test lab ${each.key} leaf certificate (PEM) - TEST MATERIAL ONLY"
  type        = "String"
  tier        = "Standard"
  value       = var.generated_material[each.key].certificate_pem

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}), {
    Name                     = "${each.key}-certificate"
    CertificateStorageFormat = "PEM"
  })
}

resource "aws_ssm_parameter" "chain" {
  for_each = local.parameter_generated_ids

  name        = "${local.parameter_path[each.key]}/chain"
  description = "Certificate test lab ${each.key} certificate chain (PEM) - TEST MATERIAL ONLY"
  type        = "String"
  tier        = "Standard"
  value       = var.generated_material[each.key].fullchain_pem

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}), {
    Name                     = "${each.key}-chain"
    CertificateStorageFormat = "PEM_CHAIN"
  })
}

resource "aws_ssm_parameter" "private_key" {
  for_each = local.parameter_generated_ids

  name        = "${local.parameter_path[each.key]}/private-key"
  description = "Certificate test lab ${each.key} private key - THROWAWAY TEST KEY"
  type        = "SecureString"
  tier        = "Standard"
  value       = var.generated_material[each.key].private_key_pem

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}), {
    Name                     = "${each.key}-private-key"
    CertificateStorageFormat = "PRIVATE_KEY_PEM"
  })
}

# --- OpenSSL material: the EXPIRED scenario --------------------------------
resource "aws_ssm_parameter" "openssl_certificate" {
  for_each = local.parameter_openssl_ids

  name        = "${local.parameter_path[each.key]}/certificate"
  description = "Certificate test lab ${each.key} EXPIRED leaf certificate (PEM) - TEST MATERIAL ONLY"
  type        = "String"
  tier        = "Standard"
  value       = var.openssl_material[each.key].certificate_pem

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}), {
    Name                     = "${each.key}-certificate"
    CertificateStorageFormat = "PEM"
  })
}

resource "aws_ssm_parameter" "openssl_chain" {
  for_each = local.parameter_openssl_ids

  name        = "${local.parameter_path[each.key]}/chain"
  description = "Certificate test lab ${each.key} certificate chain (PEM) - TEST MATERIAL ONLY"
  type        = "String"
  tier        = "Standard"
  value       = var.openssl_material[each.key].chain_pem

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}), {
    Name                     = "${each.key}-chain"
    CertificateStorageFormat = "PEM_CHAIN"
  })
}

resource "aws_ssm_parameter" "openssl_private_key" {
  for_each = local.parameter_openssl_ids

  name        = "${local.parameter_path[each.key]}/private-key"
  description = "Certificate test lab ${each.key} private key - THROWAWAY TEST KEY"
  type        = "SecureString"
  tier        = "Standard"
  value       = var.openssl_material[each.key].private_key_pem

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}), {
    Name                     = "${each.key}-private-key"
    CertificateStorageFormat = "PRIVATE_KEY_PEM"
  })
}
