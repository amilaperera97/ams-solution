locals {
  is_private = var.certificate_authority_arn != null

  # aws_acm_certificate_validation / Route 53 records only make sense for a
  # public, DNS-validated certificate.
  manage_dns_validation = !local.is_private && var.validation_method == "DNS" && var.create_route53_validation_records && var.route53_zone_id != null
}

# ---------------------------------------------------------------------------
# The ACM-managed certificate itself.
# ---------------------------------------------------------------------------
resource "aws_acm_certificate" "this" {
  domain_name               = var.domain_name
  subject_alternative_names = var.subject_alternative_names
  key_algorithm             = var.key_algorithm

  # A private (Private CA issued) certificate must NOT specify a validation
  # method; a public certificate must. Never send both.
  validation_method         = local.is_private ? null : var.validation_method
  certificate_authority_arn = var.certificate_authority_arn

  # Certificate Transparency logging is a public-certificate-only option.
  dynamic "options" {
    for_each = local.is_private ? [] : [1]

    content {
      certificate_transparency_logging_preference = var.certificate_transparency_logging_preference
    }
  }

  # Justified lifecycle rule: an ACM certificate that is referenced by an
  # ALB/NLB listener cannot be deleted while attached. create_before_destroy
  # lets Terraform replace it without a deadlock.
  lifecycle {
    create_before_destroy = true
  }

  tags = merge(var.tags, { Name = var.name })
}

# ---------------------------------------------------------------------------
# Optional DNS validation records, created ONLY in the dedicated lab hosted
# zone. See phases/phase-1/acm/validation.tf for how the zone is created.
# ---------------------------------------------------------------------------
resource "aws_route53_record" "validation" {
  for_each = local.manage_dns_validation ? {
    for dvo in aws_acm_certificate.this.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  } : {}

  zone_id         = var.route53_zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = var.route53_validation_record_ttl
  allow_overwrite = true
}

# ---------------------------------------------------------------------------
# Optional blocking wait for ISSUED status.
# ---------------------------------------------------------------------------
resource "aws_acm_certificate_validation" "this" {
  count = local.manage_dns_validation && var.wait_for_validation ? 1 : 0

  certificate_arn         = aws_acm_certificate.this.arn
  validation_record_fqdns = [for record in aws_route53_record.validation : record.fqdn]

  timeouts {
    create = var.validation_timeout
  }
}
