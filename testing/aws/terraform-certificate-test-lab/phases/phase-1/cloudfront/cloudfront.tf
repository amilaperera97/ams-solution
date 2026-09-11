locals {
  # Refuse to build a distribution with no origin rather than failing at apply.
  create = var.enable_cloudfront && var.origin_domain_name != null
}

check "cloudfront_configuration_is_complete" {
  assert {
    condition     = !var.enable_cloudfront || var.origin_domain_name != null
    error_message = "enable_cloudfront = true requires an origin. Enable the ALB (enable_alb = true) so its DNS name can be used as the origin, or leave CloudFront disabled."
  }
}

# ---------------------------------------------------------------------------
# Distribution using the DEFAULT CloudFront certificate.
#
# Discovery expectations:
#   cloudfront:ListDistributions
#     -> ViewerCertificate.CloudFrontDefaultCertificate = true
#     -> ViewerCertificate.ACMCertificateArn            = absent
#     -> Aliases                                        = empty
#   Classification: AWS-managed, no tracked certificate, renewal N/A.
#
# There is intentionally NO aliases block and NO acm_certificate_arn here.
# Adding either would require a us-east-1 certificate and would break the
# Phase 1 single-region rule.
# ---------------------------------------------------------------------------
resource "aws_cloudfront_distribution" "default_certificate" {
  count = local.create ? 1 : 0

  enabled         = true
  comment         = "Certificate test lab - distribution using the DEFAULT CloudFront certificate"
  price_class     = var.price_class
  is_ipv6_enabled = false

  origin {
    origin_id   = "lab-origin"
    domain_name = var.origin_domain_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = "lab-origin"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 60
    max_ttl     = 300
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # The whole point of this scenario: the AWS-managed default certificate.
  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = merge(var.tags, {
    Name                    = "${var.name_prefix}-cloudfront"
    CertificateTestId       = "phase1-cert-005-alternative"
    CertificateType         = "AWS_DEFAULT_CLOUDFRONT_CERTIFICATE"
    CertificateScenario     = "cloudfront-default-certificate-no-arn"
    ExpectedDiscoveryMethod = "CLOUDFRONT_LIST_DISTRIBUTIONS"
    ExpectedRenewalType     = "AWS_INTERNAL_NO_ACTION"
  })
}
