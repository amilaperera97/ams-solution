# ---------------------------------------------------------------------------
# Public subnets (one per AZ) carry the ALB, NLB and the certificate server.
# Optional private subnets exist for future phases only.
# ---------------------------------------------------------------------------

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, var.subnet_count)
}

resource "aws_subnet" "public" {
  for_each = { for index, az in local.azs : az => index }

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.key
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, each.value)
  map_public_ip_on_launch = true

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-subnet-public-${each.key}"
    Tier = "public"
  })
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_subnet" "private" {
  for_each = var.enable_private_subnets ? { for index, az in local.azs : az => index } : {}

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.key
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, each.value + 100)
  map_public_ip_on_launch = false

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-subnet-private-${each.key}"
    Tier = "private"
  })
}

resource "aws_route_table_association" "private" {
  for_each = aws_subnet.private

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private[0].id
}
