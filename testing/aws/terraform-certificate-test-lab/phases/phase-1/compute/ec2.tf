# ---------------------------------------------------------------------------
# The certificate server itself.
# ---------------------------------------------------------------------------

# Latest Amazon Linux 2023 in whichever region the provider is configured for.
# Resolved dynamically so no AMI ID is ever hardcoded.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-6.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

resource "aws_key_pair" "certificate_server" {
  count = var.enable_ec2 && var.enable_ssh_fallback && var.ssh_public_key != "" ? 1 : 0

  key_name   = "${var.name_prefix}-cert-server"
  public_key = var.ssh_public_key

  tags = merge(var.tags, { Name = "${var.name_prefix}-cert-server-key" })
}

locals {
  user_data = templatefile("${path.module}/user_data/certificate-server.sh.tftpl", {
    aws_region                = var.aws_region
    certificate_bucket_name   = coalesce(var.certificate_bucket_name, "")
    ec2_staging_prefix        = var.ec2_staging_prefix
    keystore_password         = var.keystore_password
    nginx_certificate_id      = var.nginx_certificate_id
    apache_certificate_id     = var.apache_certificate_id
    java_certificate_id       = var.java_certificate_id
    expired_certificate_id    = coalesce(var.expired_certificate_id, "")
    filesystem_inventory_json = var.filesystem_inventory_json
  })
}

module "certificate_server" {
  source = "../../../modules/ec2-certificate-server"
  count  = var.enable_ec2 ? 1 : 0

  name                        = "${var.name_prefix}-cert-server"
  ami_id                      = data.aws_ami.al2023.id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  security_group_ids          = var.security_group_ids
  iam_instance_profile        = aws_iam_instance_profile.certificate_server[0].name
  user_data                   = local.user_data
  key_name                    = length(aws_key_pair.certificate_server) > 0 ? aws_key_pair.certificate_server[0].key_name : null
  associate_public_ip_address = var.associate_public_ip_address
  root_volume_size            = var.root_volume_size

  tags = merge(var.tags, {
    CertificateTestId         = "phase1-cert-027 phase1-cert-028 phase1-cert-029 phase1-cert-030"
    CertificateScenario       = "filesystem-certificate-host"
    ExpectedDiscoveryMethod   = "SSM_RUN_COMMAND"
    ExpectedRenewalType       = "MANUAL"
    SSMManaged                = "true"
    CertificateScanEntryPoint = "/usr/local/bin/certificate-lab-scan"
  })
}
