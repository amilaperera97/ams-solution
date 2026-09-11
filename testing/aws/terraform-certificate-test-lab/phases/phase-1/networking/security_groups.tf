# ---------------------------------------------------------------------------
# Security groups.
#
# SECURITY BASELINE
#   * The certificate server needs NO inbound access for the primary
#     discovery path. SSM Run Command works entirely over outbound HTTPS.
#   * Inbound 22 only exists when enable_ssh_fallback = true, and only from
#     the CIDRs you name. 0.0.0.0/0 requires a second explicit opt-in.
#   * The load balancer has no ingress rule at all unless you populate
#     alb_ingress_cidrs.
# ---------------------------------------------------------------------------

# Guard rail: refuse to build a wide-open SSH rule by accident.
locals {
  ssh_cidrs           = var.enable_ssh_fallback ? toset(var.ssh_allowed_cidrs) : toset([])
  ssh_has_open_cidr   = contains(var.ssh_allowed_cidrs, "0.0.0.0/0")
  ssh_config_is_valid = !var.enable_ssh_fallback || length(var.ssh_allowed_cidrs) > 0
}

check "ssh_fallback_is_narrowly_scoped" {
  assert {
    condition     = local.ssh_config_is_valid
    error_message = "enable_ssh_fallback = true but ssh_allowed_cidrs is empty, so port 22 would never be reachable. Set ssh_allowed_cidrs to your own egress IP /32."
  }

  assert {
    condition     = !local.ssh_has_open_cidr || var.allow_ssh_from_anywhere
    error_message = "ssh_allowed_cidrs contains 0.0.0.0/0. If you really want SSH open to the internet on this test lab, also set allow_ssh_from_anywhere = true."
  }
}

# ---------------------------------------------------------------------------
# Load balancer security group
# ---------------------------------------------------------------------------
resource "aws_security_group" "load_balancer" {
  name        = "${var.name_prefix}-sg-lb"
  description = "Certificate test lab load balancers"
  vpc_id      = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-sg-lb" })
}

resource "aws_vpc_security_group_ingress_rule" "lb_https" {
  for_each = toset(var.alb_ingress_cidrs)

  security_group_id = aws_security_group.load_balancer.id
  description       = "HTTPS from ${each.value}"
  cidr_ipv4         = each.value
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"

  tags = var.tags
}

resource "aws_vpc_security_group_ingress_rule" "lb_http" {
  for_each = toset(var.alb_ingress_cidrs)

  security_group_id = aws_security_group.load_balancer.id
  description       = "HTTP from ${each.value}"
  cidr_ipv4         = each.value
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"

  tags = var.tags
}

resource "aws_vpc_security_group_egress_rule" "lb_to_targets" {
  security_group_id            = aws_security_group.load_balancer.id
  description                  = "Load balancer to certificate server targets"
  referenced_security_group_id = aws_security_group.certificate_server.id
  ip_protocol                  = "-1"

  tags = var.tags
}

# ---------------------------------------------------------------------------
# Certificate server security group
# ---------------------------------------------------------------------------
resource "aws_security_group" "certificate_server" {
  name        = "${var.name_prefix}-sg-cert-server"
  description = "Certificate test lab EC2 certificate server"
  vpc_id      = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-sg-cert-server" })
}

# Outbound HTTPS is what the SSM agent, the AWS CLI and dnf all need.
resource "aws_vpc_security_group_egress_rule" "cert_server_all" {
  security_group_id = aws_security_group.certificate_server.id
  description       = "Outbound to AWS service endpoints and package repositories"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"

  tags = var.tags
}

# From the load balancer only - nginx on 80/443, Apache on 8443.
resource "aws_vpc_security_group_ingress_rule" "cert_server_from_lb" {
  for_each = toset(["80", "443", "8443"])

  security_group_id            = aws_security_group.certificate_server.id
  description                  = "Port ${each.key} from the lab load balancers"
  referenced_security_group_id = aws_security_group.load_balancer.id
  from_port                    = tonumber(each.key)
  to_port                      = tonumber(each.key)
  ip_protocol                  = "tcp"

  tags = var.tags
}

# NLB health checks and client traffic arrive from the VPC CIDR, not from a
# security group, because an NLB has no security group by default.
resource "aws_vpc_security_group_ingress_rule" "cert_server_from_vpc" {
  for_each = toset(["80", "443", "8443"])

  security_group_id = aws_security_group.certificate_server.id
  description       = "Port ${each.key} from inside the lab VPC (NLB health checks)"
  cidr_ipv4         = var.vpc_cidr
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
  ip_protocol       = "tcp"

  tags = var.tags
}

# OPTIONAL SSH fallback. Absent entirely when enable_ssh_fallback = false.
resource "aws_vpc_security_group_ingress_rule" "cert_server_ssh" {
  for_each = local.ssh_cidrs

  security_group_id = aws_security_group.certificate_server.id
  description       = "SSH fallback scanning path from ${each.value}"
  cidr_ipv4         = each.value
  from_port         = 22
  to_port           = 22
  ip_protocol       = "tcp"

  tags = var.tags
}
