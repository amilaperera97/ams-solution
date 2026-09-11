# ===========================================================================
# Phase 1 - variables
#
# Everything expensive or environment-specific is a variable with a safe
# default. Copy terraform.tfvars.example to terraform.tfvars and adjust.
# ===========================================================================

# ---------------------------------------------------------------------------
# Identity / naming
# ---------------------------------------------------------------------------

variable "aws_region" {
  description = "AWS region for Phase 1. Phase 1 is single-region by design; other regions are Phase 2."
  type        = string
  default     = "eu-west-1"

  validation {
    condition     = var.aws_region == "eu-west-1"
    error_message = "Phase 1 is eu-west-1 only. Additional regions are introduced in Phase 2 (phases/phase-2/README.md). Change this only if you have read that document and accept that the Phase 1 test matrix documents eu-west-1 ARNs."
  }
}

variable "project_name" {
  description = "Project prefix for every resource name and the Project tag."
  type        = string
  default     = "certificate-test-lab"
}

variable "environment_name" {
  description = "Environment/phase name segment. Resources become certificate-test-lab-phase1-<suffix>."
  type        = string
  default     = "phase1"
}

variable "additional_tags" {
  description = "Extra tags merged into the common tag set on every resource."
  type        = map(string)
  default     = {}
}

# ---------------------------------------------------------------------------
# Test domains
# ---------------------------------------------------------------------------

variable "certificate_test_domain" {
  description = <<-EOT
    PUBLIC-SHAPED test domain used for ACM-ISSUED certificate requests and the
    API Gateway custom domain. It does NOT have to be a domain you control -
    see phases/phase-1/README.md section "ACM validation without a public DNS
    zone". The default is the IANA-reserved example.com, which is safe to
    request and will simply stay PENDING_VALIDATION.
  EOT
  type        = string
  default     = "example.com"

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$", var.certificate_test_domain))
    error_message = "certificate_test_domain must be a valid lowercase DNS domain name with at least two labels."
  }
}

variable "certificate_internal_domain" {
  description = "PRIVATE/internal test domain used for every self-signed and lab-CA-signed certificate. A non-public TLD is fine here because these certificates are never validated by a public CA."
  type        = string
  default     = "cert-lab.internal"

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$", var.certificate_internal_domain))
    error_message = "certificate_internal_domain must be a valid lowercase DNS domain name with at least two labels."
  }
}

# ---------------------------------------------------------------------------
# ACM issuance strategy
# ---------------------------------------------------------------------------

variable "acm_issuance_mode" {
  description = <<-EOT
    How the ten AWS-managed certificates are issued.

    public_dns_validation (default, free)
      Public ACM certificates with DNS validation. Unless you delegate a real
      domain, they remain PENDING_VALIDATION. That is intentional and is
      itself a discovery test case. They cannot be attached to a listener.

    private_ca (requires enable_private_ca = true, EXPENSIVE)
      ACM issues them from an AWS Private CA created by this lab. Status is
      ISSUED within seconds, no DNS involved, and they ARE attachable.
  EOT
  type        = string
  default     = "public_dns_validation"

  validation {
    condition     = contains(["public_dns_validation", "private_ca"], var.acm_issuance_mode)
    error_message = "acm_issuance_mode must be public_dns_validation or private_ca."
  }
}

variable "create_route53_test_zone" {
  description = "Create a DEDICATED, brand-new public hosted zone for certificate_test_domain and write ACM validation records into it. Never touches an existing zone. COST 0.50 USD/month. On its own this does NOT make validation succeed - you must also delegate NS records at your registrar."
  type        = bool
  default     = false
}

variable "wait_for_acm_dns_validation" {
  description = "Block terraform apply until ACM reports ISSUED. Only set true when the test zone is genuinely delegated, otherwise apply hangs for the full timeout."
  type        = bool
  default     = false
}

variable "enable_private_ca" {
  description = "Create an AWS Private CA root. ONGOING COST roughly 400 USD/month (GENERAL_PURPOSE) or 50 USD/month (SHORT_LIVED_CERTIFICATE), plus per-certificate charges. This is the single most expensive switch in the lab."
  type        = bool
  default     = false
}

variable "private_ca_usage_mode" {
  description = "AWS Private CA usage mode. SHORT_LIVED_CERTIFICATE is far cheaper but caps certificate validity at 7 days."
  type        = string
  default     = "GENERAL_PURPOSE"

  validation {
    condition     = contains(["GENERAL_PURPOSE", "SHORT_LIVED_CERTIFICATE"], var.private_ca_usage_mode)
    error_message = "private_ca_usage_mode must be GENERAL_PURPOSE or SHORT_LIVED_CERTIFICATE."
  }
}

variable "attach_acm_issued_certificates" {
  description = <<-EOT
    Add the ACM-ISSUED certificates as additional SNI certificates on the ALB
    and NLB listeners, and create the API Gateway custom domain backed by
    phase1-cert-004.

    Only set true when those certificates will actually reach ISSUED, i.e.
    acm_issuance_mode = "private_ca", or a delegated test zone with
    wait_for_acm_dns_validation = true. Otherwise apply fails with
    CertificateNotFound / UnsupportedCertificate.

    The DEFAULT listener certificates are ACM imports either way, so the test
    matrix is identical whichever value you choose.
  EOT
  type        = bool
  default     = false
}

# ---------------------------------------------------------------------------
# Feature switches - everything with a running cost can be turned off
# ---------------------------------------------------------------------------

variable "enable_ec2" {
  description = "Create the EC2 certificate server for filesystem scanning. COST about 9 USD/month (t3.micro + 10 GiB gp3)."
  type        = bool
  default     = true
}

variable "enable_alb" {
  description = "Create the Application Load Balancer. COST about 18 USD/month plus LCU."
  type        = bool
  default     = true
}

variable "enable_nlb" {
  description = "Create the Network Load Balancer. COST about 20 USD/month plus NLCU."
  type        = bool
  default     = true
}

variable "enable_api_gateway" {
  description = "Create the minimal REST API and custom domain names. No hourly cost."
  type        = bool
  default     = true
}

variable "enable_cloudfront" {
  description = "Create a CloudFront distribution using the DEFAULT CloudFront certificate (no ACM, no us-east-1). Off by default because create/destroy each take 5-15 minutes. Requires enable_alb = true for an origin."
  type        = bool
  default     = false
}

variable "enable_secrets_manager" {
  description = "Create the Secrets Manager certificate scenarios. COST 0.40 USD per secret per month (2 secrets)."
  type        = bool
  default     = true
}

variable "enable_parameter_store" {
  description = "Create the SSM Parameter Store certificate scenarios. Standard-tier parameters are free."
  type        = bool
  default     = true
}

variable "enable_s3" {
  description = "Create the private certificate bucket. Pennies per month. Disabling it also removes the EC2 staging path, so the instance falls back to generating its own material and the EXPIRED filesystem scenario is skipped."
  type        = bool
  default     = true
}

variable "enable_iam_server_certificate" {
  description = "Create the IAM server certificate scenario (phase1-cert-018). Free. IAM is a global service - see phases/phase-1/iam/variables.tf."
  type        = bool
  default     = true
}

variable "enable_openssl_generated_certificates" {
  description = "Run scripts/generate-test-certificates.sh during apply to produce the EXPIRED certificates and the PKCS#12 keystore. Requires bash + openssl locally. When false, scenarios 024, 026 and 030 are NOT created."
  type        = bool
  default     = true
}

variable "enable_inventory_file" {
  description = "Write the machine-readable certificate inventory to disk on apply, in addition to exposing it as a Terraform output."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# Networking
# ---------------------------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR for the brand-new, dedicated lab VPC. Pick something that cannot collide with your existing networks."
  type        = string
  default     = "10.42.0.0/16"
}

variable "subnet_count" {
  description = "Number of AZs. Two is the minimum for an ALB or NLB."
  type        = number
  default     = 2
}

variable "enable_private_subnets" {
  description = "Also create private subnets with no internet route. Not needed by Phase 1; provided for Phase 3."
  type        = bool
  default     = false
}

variable "enable_vpc_endpoints" {
  description = "Create SSM interface endpoints and an S3 gateway endpoint. ONGOING COST about 7.50 USD/month per interface endpoint per AZ. Only needed if you move the instance into a private subnet. No NAT gateway is ever created."
  type        = bool
  default     = false
}

variable "allowed_ingress_cidrs" {
  description = "CIDRs allowed to reach the load balancers on 80/443. EMPTY BY DEFAULT so nothing is reachable - certificate discovery reads AWS APIs and does not need network access."
  type        = list(string)
  default     = []
}

variable "internal_load_balancers" {
  description = "Create internal (non internet-facing) load balancers. True by default so no lab infrastructure is published to the internet."
  type        = bool
  default     = true
}

variable "load_balancer_name_prefix" {
  description = "ABBREVIATED prefix for ALB/NLB/target group names. AWS caps these at 32 characters, which the full 27-character lab prefix cannot fit. Leave empty to derive certlab-<environment_name>. Tags still carry the full Project and Phase values."
  type        = string
  default     = ""
}

variable "ssl_policy" {
  description = "ELB security policy for the HTTPS and TLS listeners."
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}

# ---------------------------------------------------------------------------
# EC2 / SSM / SSH
# ---------------------------------------------------------------------------

variable "ec2_instance_type" {
  description = "Instance type for the certificate server. Keep it small."
  type        = string
  default     = "t3.micro"
}

variable "ec2_root_volume_size" {
  description = "Root volume size in GiB."
  type        = number
  default     = 10
}

variable "enable_ssh_fallback" {
  description = <<-EOT
    Prepare the SSH fallback scanning path: associate an EC2 key pair and open
    TCP/22 to ssh_allowed_cidrs.

    LEAVE THIS FALSE unless you need it. SSM Run Command is the primary path,
    needs no inbound port at all, is fully audited in CloudTrail and requires
    no key management. Port 22 simply does not exist when this is false.
  EOT
  type        = bool
  default     = false
}

variable "ssh_allowed_cidrs" {
  description = "CIDRs allowed to SSH when enable_ssh_fallback is true. Use your own egress IP as a /32. 0.0.0.0/0 is blocked unless allow_ssh_from_anywhere is also true."
  type        = list(string)
  default     = []
}

variable "allow_ssh_from_anywhere" {
  description = "Deliberate second opt-in required before 0.0.0.0/0 is accepted in ssh_allowed_cidrs. Leave false."
  type        = bool
  default     = false
}

variable "ssh_public_key" {
  description = "OpenSSH PUBLIC key for the key pair. Only the public half is ever handled - this lab never generates, stores or outputs an SSH private key."
  type        = string
  default     = ""
}

# ---------------------------------------------------------------------------
# Stores
# ---------------------------------------------------------------------------

variable "certificate_store_path_prefix" {
  description = "Shared, predictable prefix for Secrets Manager secret names and Parameter Store parameter names."
  type        = string
  default     = "/certificate-test/phase1"
}

variable "secrets_recovery_window_days" {
  description = "Secrets Manager recovery window. 0 deletes immediately on destroy, which is what a disposable lab wants - otherwise the secret name stays reserved and a re-apply fails."
  type        = number
  default     = 0
}

variable "s3_bucket_name_override" {
  description = "Explicit bucket name. Leave empty to generate certificate-test-lab-phase1-certs-<random>."
  type        = string
  default     = ""
}

variable "s3_force_destroy" {
  description = "Let terraform destroy empty and delete the bucket. True because this lab is disposable."
  type        = bool
  default     = true
}

variable "s3_enable_versioning" {
  description = "Enable bucket versioning, which also lets you test 'multiple versions of the same certificate object'."
  type        = bool
  default     = true
}

variable "pkcs12_password" {
  description = "Password for every PKCS#12/PFX/JKS keystore in the lab. TEST VALUE ONLY - it is stored in Terraform state and written to the instance. Never reuse a real password here."
  type        = string
  default     = "certificate-test-lab"
  sensitive   = true

  validation {
    condition     = length(var.pkcs12_password) >= 6
    error_message = "PKCS#12 and JKS require a password of at least 6 characters."
  }
}

variable "openssl_generation_trigger" {
  description = "Bump this string to force the OpenSSL helper to regenerate its artefacts, for example to refresh the 30-day certificate after it drifts."
  type        = string
  default     = "v1"
}

# ---------------------------------------------------------------------------
# Self-check
# ---------------------------------------------------------------------------

variable "expected_certificate_scenario_count" {
  description = "Number of certificate scenarios the test matrix must contain. A check block asserts this, so an accidental edit to locals.tf fails validation instead of silently shrinking the matrix."
  type        = number
  default     = 30
}
