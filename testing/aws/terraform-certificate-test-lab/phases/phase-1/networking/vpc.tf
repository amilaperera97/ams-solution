# ---------------------------------------------------------------------------
# Dedicated lab VPC + internet gateway + public route table.
# ---------------------------------------------------------------------------

data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-igw" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-rt-public" })
}

resource "aws_route" "public_default" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

# Private route table has no default route on purpose: no NAT gateway is
# created, so private subnets are genuinely isolated.
resource "aws_route_table" "private" {
  count = var.enable_private_subnets ? 1 : 0

  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-rt-private" })
}

# ---------------------------------------------------------------------------
# OPTIONAL VPC endpoints - disabled by default because of the hourly charge.
# ---------------------------------------------------------------------------
locals {
  interface_endpoint_services = var.enable_vpc_endpoints ? toset(["ssm", "ssmmessages", "ec2messages"]) : toset([])
}

resource "aws_security_group" "vpc_endpoints" {
  count = var.enable_vpc_endpoints ? 1 : 0

  name        = "${var.name_prefix}-sg-vpce"
  description = "HTTPS from the lab VPC to the SSM interface endpoints"
  vpc_id      = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name_prefix}-sg-vpce" })
}

resource "aws_vpc_security_group_ingress_rule" "vpc_endpoints_https" {
  count = var.enable_vpc_endpoints ? 1 : 0

  security_group_id = aws_security_group.vpc_endpoints[0].id
  description       = "HTTPS from inside the lab VPC"
  cidr_ipv4         = aws_vpc.this.cidr_block
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"

  tags = var.tags
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_endpoint_services

  vpc_id              = aws_vpc.this.id
  service_name        = "com.amazonaws.${var.aws_region}.${each.value}"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [for subnet in aws_subnet.public : subnet.id]
  security_group_ids  = [aws_security_group.vpc_endpoints[0].id]
  private_dns_enabled = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-vpce-${each.value}" })
}

# Gateway endpoints are free - safe to create whenever endpoints are enabled.
resource "aws_vpc_endpoint" "s3" {
  count = var.enable_vpc_endpoints ? 1 : 0

  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids = concat(
    [aws_route_table.public.id],
    var.enable_private_subnets ? [aws_route_table.private[0].id] : [],
  )

  tags = merge(var.tags, { Name = "${var.name_prefix}-vpce-s3" })
}
