locals {
  # Guard against the AWS error you get when the default certificate is also
  # supplied as an additional SNI certificate.
  sni_certificate_arns = toset([
    for arn in var.additional_certificate_arns : arn
    if arn != null && arn != "" && arn != var.default_certificate_arn
  ])
}

resource "aws_lb" "this" {
  name               = var.name
  internal           = var.internal
  load_balancer_type = "application"
  subnets            = var.subnet_ids
  security_groups    = var.security_group_ids

  # Disposable test environment - never block terraform destroy.
  enable_deletion_protection = false
  drop_invalid_header_fields = true

  tags = merge(var.tags, { Name = var.name })
}

resource "aws_lb_target_group" "this" {
  name        = var.target_group_name
  vpc_id      = var.vpc_id
  port        = var.target_port
  protocol    = "HTTP"
  target_type = "instance"

  health_check {
    enabled  = true
    path     = var.health_check_path
    protocol = "HTTP"
    matcher  = "200-499"
  }

  tags = merge(var.tags, { Name = var.target_group_name })
}

resource "aws_lb_target_group_attachment" "this" {
  for_each = toset(var.target_instance_ids)

  target_group_arn = aws_lb_target_group.this.arn
  target_id        = each.value
  port             = var.target_port
}

# ---------------------------------------------------------------------------
# HTTP listener - deliberately certificate-free.
# ---------------------------------------------------------------------------
resource "aws_lb_listener" "http" {
  count = var.enable_http_listener ? 1 : 0

  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }

  tags = merge(var.tags, { Name = "${var.name}-http" })
}

# ---------------------------------------------------------------------------
# HTTPS listener - carries the default certificate.
# ---------------------------------------------------------------------------
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = var.ssl_policy
  certificate_arn   = var.default_certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }

  tags = merge(var.tags, { Name = "${var.name}-https" })
}

# ---------------------------------------------------------------------------
# Additional SNI certificates on the SAME listener. An ALB listener supports
# up to 25 additional certificates.
# ---------------------------------------------------------------------------
resource "aws_lb_listener_certificate" "sni" {
  for_each = local.sni_certificate_arns

  listener_arn    = aws_lb_listener.https.arn
  certificate_arn = each.value
}
