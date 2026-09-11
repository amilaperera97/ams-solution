# ---------------------------------------------------------------------------
# SCENARIOS phase1-cert-021, phase1-cert-022
# Certificate material held in AWS Secrets Manager.
#
# Layout: a single JSON secret per certificate, with the field names most
# commonly used in the wild, so the discovery application can parse it:
#
#   { "certificate": "...", "privateKey": "...", "certificateChain": "...",
#     "certificateId": "...", "domain": "..." }
#
# Expected discovery mechanism:
#   secretsmanager:ListSecrets with a filter on the /certificate-test/phase1
#   prefix, then GetSecretValue and parse the PEM to extract notAfter.
#
# Expected renewal type: MANUAL / EXTERNAL - Secrets Manager has no
# certificate lifecycle of its own.
#
# COST: 0.40 USD per secret per month plus API calls.
# ---------------------------------------------------------------------------

locals {
  secrets_manager_ids = var.enable_secrets_manager ? toset(var.secrets_manager_certificate_ids) : toset([])
}

resource "aws_secretsmanager_secret" "certificate" {
  for_each = local.secrets_manager_ids

  # e.g. /certificate-test/phase1/cert-021
  name        = "${var.store_path_prefix}/${replace(each.key, "phase1-cert-", "cert-")}"
  description = "Certificate test lab scenario ${each.key} (${lookup(var.certificate_domains, each.key, "unknown domain")}) - TEST MATERIAL ONLY"

  recovery_window_in_days = var.secrets_recovery_window_days

  tags = merge(var.tags, lookup(var.certificate_tags, each.key, {}), {
    Name                     = "${each.key}-secret"
    CertificateStorageFormat = "JSON_PEM_BUNDLE"
  })
}

resource "aws_secretsmanager_secret_version" "certificate" {
  for_each = local.secrets_manager_ids

  secret_id = aws_secretsmanager_secret.certificate[each.key].id

  secret_string = jsonencode({
    certificateId    = each.key
    domain           = lookup(var.certificate_domains, each.key, null)
    certificate      = var.generated_material[each.key].certificate_pem
    privateKey       = var.generated_material[each.key].private_key_pem
    certificateChain = var.generated_material[each.key].chain_pem
    notAfter         = var.generated_material[each.key].not_after
    source           = "certificate-test-lab"
  })
}
