locals {
  create_iam_certificate = var.enable_iam_server_certificate
}

resource "aws_iam_server_certificate" "legacy" {
  count = local.create_iam_certificate ? 1 : 0

  # name_prefix rather than name: IAM server certificate names cannot be
  # updated in place, so a prefix lets Terraform replace cleanly.
  name_prefix = "${var.name_prefix}-legacy-"
  path        = var.iam_path

  certificate_body  = var.generated_material[var.certificate_id].certificate_pem
  certificate_chain = var.generated_material[var.certificate_id].chain_pem
  private_key       = var.generated_material[var.certificate_id].private_key_pem

  # Justified: the certificate cannot be deleted while a CLB/CloudFront
  # distribution references it, so replacements are created first.
  lifecycle {
    create_before_destroy = true
  }

  tags = merge(var.tags, lookup(var.certificate_tags, var.certificate_id, {}), {
    Name                     = "${var.name_prefix}-legacy-server-certificate"
    CertificateStorageFormat = "IAM_SERVER_CERTIFICATE"
  })
}
