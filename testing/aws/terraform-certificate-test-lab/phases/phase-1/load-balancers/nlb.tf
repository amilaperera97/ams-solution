# ---------------------------------------------------------------------------
# Network Load Balancer with a TLS listener.
#
# An NLB TLS listener terminates TLS with an ACM certificate exactly like an
# ALB HTTPS listener, and it supports additional SNI certificates through the
# same DescribeListenerCertificates API. That makes it a second, structurally
# different discovery path worth testing.
#
# NOTE: no security group is attached. NLB security groups are optional and
# adding one here would only complicate the health check path; the instance
# security group already allows the VPC CIDR on 443.
# ---------------------------------------------------------------------------

resource "aws_lb" "nlb" {
  count = var.enable_nlb ? 1 : 0

  name               = "${var.lb_name_prefix}-nlb"
  internal           = var.internal
  load_balancer_type = "network"
  subnets            = var.subnet_ids

  enable_deletion_protection       = false
  enable_cross_zone_load_balancing = true

  tags = merge(var.tags, {
    Name                    = "${var.lb_name_prefix}-nlb"
    CertificateScenario     = "nlb-tls-listener-certificate-relationship"
    ExpectedDiscoveryMethod = "ELBV2_DESCRIBE_LISTENER_CERTIFICATES"
  })
}

# TLS listener target group: the NLB terminates TLS and forwards plain TCP.
resource "aws_lb_target_group" "nlb_tls" {
  count = var.enable_nlb ? 1 : 0

  name        = "${var.lb_name_prefix}-nlb-tls-tg"
  vpc_id      = var.vpc_id
  port        = 443
  protocol    = "TCP"
  target_type = "instance"

  health_check {
    enabled  = true
    protocol = "TCP"
    port     = "traffic-port"
  }

  tags = merge(var.tags, { Name = "${var.lb_name_prefix}-nlb-tls-tg" })
}

resource "aws_lb_target_group_attachment" "nlb_tls" {
  for_each = var.enable_nlb ? toset(var.target_instance_ids) : toset([])

  target_group_arn = aws_lb_target_group.nlb_tls[0].arn
  target_id        = each.value
  port             = 443
}

# Plain TCP target group for the certificate-free listener.
resource "aws_lb_target_group" "nlb_tcp" {
  count = var.enable_nlb && var.enable_nlb_tcp_listener ? 1 : 0

  name        = "${var.lb_name_prefix}-nlb-tcp-tg"
  vpc_id      = var.vpc_id
  port        = 80
  protocol    = "TCP"
  target_type = "instance"

  health_check {
    enabled  = true
    protocol = "TCP"
    port     = "traffic-port"
  }

  tags = merge(var.tags, { Name = "${var.lb_name_prefix}-nlb-tcp-tg" })
}

resource "aws_lb_target_group_attachment" "nlb_tcp" {
  for_each = var.enable_nlb && var.enable_nlb_tcp_listener ? toset(var.target_instance_ids) : toset([])

  target_group_arn = aws_lb_target_group.nlb_tcp[0].arn
  target_id        = each.value
  port             = 80
}
