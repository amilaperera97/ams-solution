# ---------------------------------------------------------------------------
# phases/phase-1/load-balancers
#
# THE CERTIFICATE-TO-SERVICE RELATIONSHIPS BEING TESTED
#
#   ALB -> HTTP listener  -> (no certificate)            negative test case
#   ALB -> HTTPS listener -> default certificate         1:1 relationship
#   ALB -> HTTPS listener -> N additional SNI certs      1:N relationship
#   NLB -> TLS listener   -> default certificate         1:1 relationship
#   NLB -> TLS listener   -> N additional SNI certs      1:N relationship
#   NLB -> TCP listener   -> (no certificate)            negative test case
#
# WHY THE ATTACHED CERTIFICATES ARE ACM *IMPORTS* BY DEFAULT
#   An ELB listener will only accept a certificate whose ACM status is
#   ISSUED. A public ACM certificate is PENDING_VALIDATION until its DNS
#   validation records resolve in public DNS, which requires you to own the
#   domain. An ACM import is ISSUED immediately, so the imports carry the
#   listeners and every relationship above is exercised out of the box.
#
#   When you DO have issuable ACM-managed certificates (a delegated Route 53
#   test zone, or acm_issuance_mode = "private_ca"), set
#   attach_acm_issued_certificates = true and the ACM-issued certificates are
#   added as additional SNI certificates on the same listeners. The default
#   certificates never change, so the test matrix stays stable either way.
#
# COST: ALB about 0.0252 USD/hour, NLB about 0.0270 USD/hour in eu-west-1,
# plus LCU/NLCU. Roughly 18-20 USD/month each. Both are individually
# disableable.
# ---------------------------------------------------------------------------

variable "name_prefix" {
  description = "Full name prefix from modules/common. Used for tags, not for the load balancer names themselves."
  type        = string
}

variable "lb_name_prefix" {
  description = <<-EOT
    ABBREVIATED prefix for load balancer and target group names.

    AWS caps both at 32 characters, and the full lab prefix
    (certificate-test-lab-phase1, 27 chars) leaves no room for a
    "-nlb-tls-tg" suffix. Load balancer resources therefore use a short
    prefix; every one of them still carries the full Project/Phase tags, so
    filtering by tag is unaffected.
  EOT
  type        = string

  validation {
    condition     = length(var.lb_name_prefix) <= 18 && can(regex("^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$", var.lb_name_prefix))
    error_message = "lb_name_prefix must be 1-18 alphanumeric/hyphen characters and must not start or end with a hyphen. 18 leaves room for the longest suffix used by this lab, '-nlb-tls-tg'."
  }
}

variable "tags" {
  description = "Common tags."
  type        = map(string)
}

variable "vpc_id" {
  description = "Lab VPC ID."
  type        = string
}

variable "subnet_ids" {
  description = "At least two subnets in different AZs."
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups for the ALB. An NLB in this lab has no security group."
  type        = list(string)
}

variable "target_instance_ids" {
  description = "Instance IDs to register in the target groups. May be empty."
  type        = list(string)
  default     = []
}

variable "internal" {
  description = "Create internal load balancers. True by default so nothing is published to the internet."
  type        = bool
  default     = true
}

variable "ssl_policy" {
  description = "ELB security policy for the HTTPS/TLS listeners."
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}

variable "enable_alb" {
  description = "Create the Application Load Balancer."
  type        = bool
  default     = true
}

variable "enable_alb_http_listener" {
  description = "Also create a plain HTTP:80 listener, giving the scanner a listener with no certificate."
  type        = bool
  default     = true
}

variable "enable_nlb" {
  description = "Create the Network Load Balancer."
  type        = bool
  default     = true
}

variable "enable_nlb_tcp_listener" {
  description = "Also create a plain TCP:80 listener on the NLB, giving the scanner a second listener with no certificate."
  type        = bool
  default     = true
}

variable "alb_default_certificate_arn" {
  description = "ACM ARN for the ALB HTTPS listener default certificate. Must be ISSUED."
  type        = string
}

variable "alb_sni_certificate_arns" {
  description = "Additional ACM ARNs attached to the ALB HTTPS listener for SNI."
  type        = list(string)
  default     = []
}

variable "nlb_default_certificate_arn" {
  description = "ACM ARN for the NLB TLS listener default certificate. Must be ISSUED."
  type        = string
}

variable "nlb_sni_certificate_arns" {
  description = "Additional ACM ARNs attached to the NLB TLS listener for SNI."
  type        = list(string)
  default     = []
}
