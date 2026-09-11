# ---------------------------------------------------------------------------
# NLB listeners.
#
# The ALB listeners live inside modules/alb so that the "one listener, many
# certificates" wiring stays in one place. The NLB listeners are here because
# the NLB itself is defined locally in nlb.tf.
# ---------------------------------------------------------------------------

locals {
  # Never repeat the default certificate in the SNI set - AWS rejects it.
  nlb_sni_certificate_arns = toset([
    for arn in var.nlb_sni_certificate_arns : arn
    if arn != null && arn != "" && arn != var.nlb_default_certificate_arn
  ])
}

# TLS listener - terminates TLS with an ACM certificate.
resource "aws_lb_listener" "nlb_tls" {
  count = var.enable_nlb ? 1 : 0

  load_balancer_arn = aws_lb.nlb[0].arn
  port              = 443
  protocol          = "TLS"
  ssl_policy        = var.ssl_policy
  certificate_arn   = var.nlb_default_certificate_arn
  alpn_policy       = "None"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.nlb_tls[0].arn
  }

  tags = merge(var.tags, { Name = "${var.lb_name_prefix}-nlb-tls" })
}

# Additional SNI certificates on the NLB TLS listener.
resource "aws_lb_listener_certificate" "nlb_sni" {
  for_each = var.enable_nlb ? local.nlb_sni_certificate_arns : toset([])

  listener_arn    = aws_lb_listener.nlb_tls[0].arn
  certificate_arn = each.value
}

# Plain TCP listener - deliberately certificate-free.
resource "aws_lb_listener" "nlb_tcp" {
  count = var.enable_nlb && var.enable_nlb_tcp_listener ? 1 : 0

  load_balancer_arn = aws_lb.nlb[0].arn
  port              = 80
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.nlb_tcp[0].arn
  }

  tags = merge(var.tags, { Name = "${var.lb_name_prefix}-nlb-tcp" })
}
