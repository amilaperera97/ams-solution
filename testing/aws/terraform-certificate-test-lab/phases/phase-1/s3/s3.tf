# ---------------------------------------------------------------------------
# Private certificate bucket.
#
# Security controls applied:
#   * all four public access blocks ON
#   * SSE-S3 (AES256) default encryption + bucket key
#   * bucket owner enforced (ACLs disabled entirely)
#   * TLS-only bucket policy (aws:SecureTransport)
#   * versioning (optional, on by default)
# ---------------------------------------------------------------------------

resource "aws_s3_bucket" "certificates" {
  bucket        = var.bucket_name
  force_destroy = var.force_destroy

  tags = merge(var.tags, { Name = var.bucket_name })
}

resource "aws_s3_bucket_public_access_block" "certificates" {
  bucket = aws_s3_bucket.certificates.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "certificates" {
  bucket = aws_s3_bucket.certificates.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "certificates" {
  bucket = aws_s3_bucket.certificates.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }

    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_versioning" "certificates" {
  bucket = aws_s3_bucket.certificates.id

  versioning_configuration {
    status = var.enable_versioning ? "Enabled" : "Suspended"
  }
}

data "aws_iam_policy_document" "tls_only" {
  statement {
    sid     = "DenyNonTlsRequests"
    effect  = "Deny"
    actions = ["s3:*"]

    resources = [
      aws_s3_bucket.certificates.arn,
      "${aws_s3_bucket.certificates.arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "certificates" {
  bucket = aws_s3_bucket.certificates.id
  policy = data.aws_iam_policy_document.tls_only.json

  # The public access block must exist before a bucket policy is attached.
  depends_on = [aws_s3_bucket_public_access_block.certificates]
}
