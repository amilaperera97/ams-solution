# ---------------------------------------------------------------------------
# The listener-to-certificate relationship table.
#
# Built as ONE list literal with an "exists" flag that is filtered in the
# output, rather than concat()-ing conditional lists. concat() requires every
# element to share a single type, and these objects legitimately differ (a
# certificate-free listener has no default certificate and an empty
# certificate list), so concat() fails on them.
#
# Splat + flatten()/one() is used instead of module.alb[0] so the expressions
# stay valid when the load balancer is disabled.
# ---------------------------------------------------------------------------

locals {
  alb_arn                 = one(module.alb[*].arn)
  alb_https_listener_arn  = one(module.alb[*].https_listener_arn)
  alb_http_listener_arn   = one(module.alb[*].http_listener_arn)
  alb_default_certificate = one(module.alb[*].https_listener_default_certificate_arn)
  alb_sni_certificates    = flatten(module.alb[*].https_listener_sni_certificate_arns)
  alb_all_certificates    = compact(concat([local.alb_default_certificate], local.alb_sni_certificates))

  nlb_arn                 = one(aws_lb.nlb[*].arn)
  nlb_tls_listener_arn    = one(aws_lb_listener.nlb_tls[*].arn)
  nlb_tcp_listener_arn    = one(aws_lb_listener.nlb_tcp[*].arn)
  nlb_default_certificate = one(aws_lb_listener.nlb_tls[*].certificate_arn)
  nlb_sni_certificates    = sort([for certificate in aws_lb_listener_certificate.nlb_sni : certificate.certificate_arn])
  nlb_all_certificates    = compact(concat([local.nlb_default_certificate], local.nlb_sni_certificates))

  listener_relationship_candidates = [
    {
      exists                  = var.enable_alb
      load_balancer_type      = "application"
      load_balancer_arn       = local.alb_arn
      listener_arn            = local.alb_https_listener_arn
      listener_protocol       = "HTTPS"
      listener_port           = 443
      terminates_tls          = true
      default_certificate_arn = local.alb_default_certificate
      certificate_arns        = local.alb_all_certificates
      certificate_count       = length(local.alb_all_certificates)
    },
    {
      exists                  = var.enable_alb && var.enable_alb_http_listener
      load_balancer_type      = "application"
      load_balancer_arn       = local.alb_arn
      listener_arn            = local.alb_http_listener_arn
      listener_protocol       = "HTTP"
      listener_port           = 80
      terminates_tls          = false
      default_certificate_arn = null
      certificate_arns        = compact([])
      certificate_count       = 0
    },
    {
      exists                  = var.enable_nlb
      load_balancer_type      = "network"
      load_balancer_arn       = local.nlb_arn
      listener_arn            = local.nlb_tls_listener_arn
      listener_protocol       = "TLS"
      listener_port           = 443
      terminates_tls          = true
      default_certificate_arn = local.nlb_default_certificate
      certificate_arns        = local.nlb_all_certificates
      certificate_count       = length(local.nlb_all_certificates)
    },
    {
      exists                  = var.enable_nlb && var.enable_nlb_tcp_listener
      load_balancer_type      = "network"
      load_balancer_arn       = local.nlb_arn
      listener_arn            = local.nlb_tcp_listener_arn
      listener_protocol       = "TCP"
      listener_port           = 80
      terminates_tls          = false
      default_certificate_arn = null
      certificate_arns        = compact([])
      certificate_count       = 0
    },
  ]
}
