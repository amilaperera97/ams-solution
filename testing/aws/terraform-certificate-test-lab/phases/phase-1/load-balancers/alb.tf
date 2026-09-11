# ---------------------------------------------------------------------------
# Application Load Balancer.
#
# Discovery path:
#   elbv2:DescribeLoadBalancers
#     -> elbv2:DescribeListeners        (Certificates[0].CertificateArn = default)
#       -> elbv2:DescribeListenerCertificates  (all certs, IsDefault flag)
# ---------------------------------------------------------------------------

module "alb" {
  source = "../../../modules/alb"
  count  = var.enable_alb ? 1 : 0

  name               = "${var.lb_name_prefix}-alb"
  target_group_name  = "${var.lb_name_prefix}-alb-tg"
  vpc_id             = var.vpc_id
  subnet_ids         = var.subnet_ids
  security_group_ids = var.security_group_ids
  internal           = var.internal

  default_certificate_arn     = var.alb_default_certificate_arn
  additional_certificate_arns = var.alb_sni_certificate_arns

  ssl_policy           = var.ssl_policy
  enable_http_listener = var.enable_alb_http_listener
  target_port          = 80
  target_instance_ids  = var.target_instance_ids

  tags = merge(var.tags, {
    CertificateScenario     = "alb-listener-certificate-relationships"
    ExpectedDiscoveryMethod = "ELBV2_DESCRIBE_LISTENER_CERTIFICATES"
  })
}
