# ---------------------------------------------------------------------------
# phases/phase-1/networking
#
# A dedicated, brand-new VPC for the lab. NOTHING here touches an existing
# VPC, subnet, route table or security group.
#
# COST DESIGN DECISION
#   No NAT gateway (approx. 33 USD/month + data). The certificate server
#   instead sits in a public subnet with a public IP and reaches the SSM
#   endpoints through the internet gateway, which is free. Optional interface
#   VPC endpoints are available for a private-subnet design but cost about
#   7.50 USD/month each per AZ.
# ---------------------------------------------------------------------------

variable "name_prefix" {
  description = "Name prefix from modules/common."
  type        = string
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
}

variable "aws_region" {
  description = "Region, used to build VPC endpoint service names without relying on a data source."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the dedicated lab VPC. Pick a range that cannot collide with anything you already run."
  type        = string
  default     = "10.42.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "vpc_cidr must be a valid IPv4 CIDR block."
  }
}

variable "subnet_count" {
  description = "Number of AZs/subnets. Two is the minimum for an ALB or NLB."
  type        = number
  default     = 2

  validation {
    condition     = var.subnet_count >= 2 && var.subnet_count <= 3
    error_message = "subnet_count must be 2 or 3."
  }
}

variable "enable_private_subnets" {
  description = "Also create private subnets (no internet route). Useful for Phase 3 container work; not required by Phase 1."
  type        = bool
  default     = false
}

variable "enable_vpc_endpoints" {
  description = "Create interface VPC endpoints for SSM (ssm, ssmmessages, ec2messages) and a gateway endpoint for S3. ONGOING COST: about 7.50 USD/month per interface endpoint per AZ. Only needed if you move the instance into a private subnet."
  type        = bool
  default     = false
}

variable "alb_ingress_cidrs" {
  description = "CIDRs allowed to reach the load balancer on 80/443. EMPTY BY DEFAULT so nothing is reachable. Certificate discovery does not need traffic."
  type        = list(string)
  default     = []
}

variable "enable_ssh_fallback" {
  description = "Open TCP/22 on the certificate server security group for the SSH fallback scanning path."
  type        = bool
  default     = false
}

variable "ssh_allowed_cidrs" {
  description = "CIDRs allowed to SSH when enable_ssh_fallback is true. Must be set to something narrow, e.g. your office egress IP /32."
  type        = list(string)
  default     = []
}

variable "allow_ssh_from_anywhere" {
  description = "Explicit, deliberate opt-in to 0.0.0.0/0 on port 22. Leave false. Only provided so the guard rail below is an informed override rather than a silent default."
  type        = bool
  default     = false
}
