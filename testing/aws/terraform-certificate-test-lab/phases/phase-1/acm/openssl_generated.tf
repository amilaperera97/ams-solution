# ---------------------------------------------------------------------------
# Artefacts the Terraform providers CANNOT produce natively.
#
# 1. BACK-DATED / EXPIRED certificates
#    hashicorp/tls always sets notBefore to "now", so it can never emit an
#    already-expired certificate. `openssl ca -startdate -enddate` can.
#    Required for: phase1-cert-024, phase1-cert-030 (expiryScenario EXPIRED)
#
# 2. PKCS#12 keystores
#    There is no Terraform resource that emits a PKCS#12 / PFX container.
#    Required for: phase1-cert-026 (S3-hosted .p12)
#
# HOW IT WORKS
#   A terraform_data resource shells out to scripts/generate-test-certificates.sh
#   during apply. The data sources below depend on it, which defers their read
#   to apply time, so a clean checkout can run `terraform plan` without the
#   files existing yet.
#
#   You may also run the script yourself before applying, and set
#   enable_openssl_generated_certificates = false to stop Terraform invoking
#   it. The script is idempotent.
#
#   Output goes to a GITIGNORED directory. Nothing generated here is ever
#   committed.
# ---------------------------------------------------------------------------

locals {
  openssl_certificate_ids = var.enable_openssl_generated_certificates ? toset([
    "phase1-cert-024", # EXPIRED  -> SSM Parameter Store
    "phase1-cert-026", # WARNING  -> S3 as PKCS#12
    "phase1-cert-030", # EXPIRED  -> EC2 filesystem
  ]) : toset([])
}

resource "terraform_data" "openssl_certificates" {
  count = var.enable_openssl_generated_certificates ? 1 : 0

  triggers_replace = [
    var.openssl_generation_trigger,
    var.generated_certificates_dir,
  ]

  provisioner "local-exec" {
    interpreter = ["/usr/bin/env", "bash", "-c"]
    command     = "bash '${var.openssl_script_path}' --output-dir '${var.generated_certificates_dir}' --p12-password '${var.pkcs12_password}' --force"
  }
}

# --- Leaf certificates (public material) -----------------------------------
data "local_file" "openssl_certificate" {
  for_each = local.openssl_certificate_ids

  filename = "${var.generated_certificates_dir}/${each.key}/cert.crt"

  depends_on = [terraform_data.openssl_certificates]
}

# --- Leaf private keys (throwaway test keys) -------------------------------
data "local_sensitive_file" "openssl_private_key" {
  for_each = local.openssl_certificate_ids

  filename = "${var.generated_certificates_dir}/${each.key}/cert.key"

  depends_on = [terraform_data.openssl_certificates]
}

# --- The OpenSSL lab CA that signed them (chain material) ------------------
data "local_file" "openssl_ca_certificate" {
  count = var.enable_openssl_generated_certificates ? 1 : 0

  filename = "${var.generated_certificates_dir}/ca/openssl-lab-ca.crt"

  depends_on = [terraform_data.openssl_certificates]
}

# --- PKCS#12 keystore for phase1-cert-026 ----------------------------------
data "local_sensitive_file" "openssl_pkcs12" {
  count = var.enable_openssl_generated_certificates ? 1 : 0

  filename = "${var.generated_certificates_dir}/phase1-cert-026/cert.p12"

  depends_on = [terraform_data.openssl_certificates]
}
