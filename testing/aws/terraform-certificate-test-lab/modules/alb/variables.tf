# ---------------------------------------------------------------------------
# modules/alb
#
# Application Load Balancer with an HTTP listener and an HTTPS listener that
# carries a DEFAULT certificate plus any number of ADDITIONAL SNI
# certificates. This is the core "one listener, many certificates" discovery
# scenario:
#
#   ALB -> Listener -> default certificate_arn
#   ALB -> Listener -> aws_lb_listener_certificate (1..n)
#
# COST NOTE: an ALB costs roughly 0.0252 USD/hour in eu-west-1 plus LCU.
# ---------------------------------------------------------------------------

variable "name" {
  description = "ALB name. AWS caps load balancer names at 32 characters."
  type        = string

  validation {
    condition     = length(var.name) <= 32 && can(regex("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$", var.name))
    error_message = "An ALB name must be 1-32 alphanumeric/hyphen characters and must not start or end with a hyphen."
  }
}

variable "target_group_name" {
  description = "Target group name. Also capped at 32 characters by AWS."
  type        = string

  validation {
    condition     = length(var.target_group_name) <= 32 && can(regex("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$", var.target_group_name))
    error_message = "A target group name must be 1-32 alphanumeric/hyphen characters and must not start or end with a hyphen."
  }
}

variable "vpc_id" {
  description = "VPC the target group lives in."
  type        = string
}

variable "subnet_ids" {
  description = "At least two subnets in different AZs - an ALB requirement."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "An ALB requires subnets in at least two Availability Zones."
  }
}

variable "security_group_ids" {
  description = "Security groups attached to the ALB."
  type        = list(string)
}

variable "internal" {
  description = "Create an internal (non internet-facing) ALB. Phase 1 defaults to internal so nothing is published to the internet."
  type        = bool
  default     = true
}

variable "default_certificate_arn" {
  description = "ACM ARN used as the HTTPS listener default certificate. Must be an ISSUED certificate."
  type        = string
}

variable "additional_certificate_arns" {
  description = "Extra ACM ARNs attached to the same HTTPS listener for SNI. The default certificate must NOT be repeated here."
  type        = list(string)
  default     = []
}

variable "ssl_policy" {
  description = "ELB security policy for the HTTPS listener."
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}

variable "enable_http_listener" {
  description = "Create a plain HTTP:80 listener as well, so the discovery application can distinguish a listener with no certificate from one with certificates."
  type        = bool
  default     = true
}

variable "target_port" {
  description = "Port the target group forwards to on the instances."
  type        = number
  default     = 80
}

variable "target_instance_ids" {
  description = "Instance IDs registered in the target group. May be empty."
  type        = list(string)
  default     = []
}

variable "health_check_path" {
  description = "Target group health check path."
  type        = string
  default     = "/"
}

variable "tags" {
  description = "Tags applied to all load balancer resources."
  type        = map(string)
  default     = {}
}
