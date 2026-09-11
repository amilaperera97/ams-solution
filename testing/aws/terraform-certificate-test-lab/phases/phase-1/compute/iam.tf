# ---------------------------------------------------------------------------
# Instance role for the certificate server.
#
# Permissions are deliberately minimal:
#   AmazonSSMManagedInstanceCore  - the AWS managed policy that lets the SSM
#                                   agent register and run commands. This is
#                                   what makes ssm:SendCommand scanning work.
#   inline s3 read               - ONLY the ec2-staging/* prefix of the lab
#                                   bucket, nothing else.
#
# A brand new role is created. No existing role, user or policy is modified.
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "instance_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "certificate_server" {
  count = var.enable_ec2 ? 1 : 0

  name                 = "${var.name_prefix}-cert-server-role"
  description          = "Certificate test lab EC2 certificate server - SSM managed instance"
  assume_role_policy   = data.aws_iam_policy_document.instance_assume_role.json
  max_session_duration = 3600

  tags = merge(var.tags, { Name = "${var.name_prefix}-cert-server-role" })
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  count = var.enable_ec2 ? 1 : 0

  role       = aws_iam_role.certificate_server[0].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "staging_read" {
  count = var.enable_ec2 && var.certificate_bucket_arn != null ? 1 : 0

  statement {
    sid       = "ReadStagedCertificateMaterial"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${var.certificate_bucket_arn}/${var.ec2_staging_prefix}/*"]
  }

  statement {
    sid       = "ListStagingPrefix"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [var.certificate_bucket_arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["${var.ec2_staging_prefix}/*"]
    }
  }
}

resource "aws_iam_role_policy" "staging_read" {
  count = var.enable_ec2 && var.certificate_bucket_arn != null ? 1 : 0

  name   = "${var.name_prefix}-staging-read"
  role   = aws_iam_role.certificate_server[0].id
  policy = data.aws_iam_policy_document.staging_read[0].json
}

resource "aws_iam_instance_profile" "certificate_server" {
  count = var.enable_ec2 ? 1 : 0

  name = "${var.name_prefix}-cert-server-profile"
  role = aws_iam_role.certificate_server[0].name

  tags = merge(var.tags, { Name = "${var.name_prefix}-cert-server-profile" })
}

data "aws_partition" "current" {}
