# ---------------------------------------------------------------------------
# phases/phase-1/compute
#
# One small Amazon Linux 2023 instance that holds FILESYSTEM certificates in
# realistic locations, serves two of them from nginx and Apache, and is
# registered with AWS Systems Manager so the Java backend can scan it with
# SSM Run Command:
#
#   Spring Boot -> AWS SDK -> ssm:SendCommand -> EC2 -> certificate scanner
#
# SCENARIOS HOSTED HERE
#   phase1-cert-027  /etc/nginx/ssl               nginx, CRITICAL (7 days)
#   phase1-cert-028  /etc/httpd/conf/ssl
#                    /etc/apache2/ssl             Apache, HEALTHY, ECDSA P-384
#   phase1-cert-029  /opt/application/certs       Java/Spring Boot, P12 + JKS
#   phase1-cert-030  /etc/ssl/certs
#                    /etc/ssl/private
#                    /opt/certificates            EXPIRED
#
# COST: the only always-on compute. t3.micro in eu-west-1 is about
# 0.0108 USD/hour (~8 USD/month) plus ~0.80 USD/month for a 10 GiB gp3 root
# volume. Set enable_ec2 = false to remove it entirely.
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
  description = "Region, passed into user-data for AWS CLI calls."
  type        = string
}

variable "enable_ec2" {
  description = "Create the EC2 certificate server."
  type        = bool
  default     = true
}

variable "instance_type" {
  description = "Instance type. Keep it small."
  type        = string
  default     = "t3.micro"
}

variable "root_volume_size" {
  description = "Root volume size in GiB."
  type        = number
  default     = 10

  validation {
    condition     = var.root_volume_size >= 8 && var.root_volume_size <= 30
    error_message = "root_volume_size must be between 8 and 30 GiB for this lab."
  }
}

variable "subnet_id" {
  description = "Subnet to launch into."
  type        = string
}

variable "security_group_ids" {
  description = "Security groups for the instance."
  type        = list(string)
}

variable "associate_public_ip_address" {
  description = "Assign a public IP so the SSM agent can reach the SSM endpoints through the internet gateway instead of a paid NAT gateway."
  type        = bool
  default     = true
}

# --- SSH fallback -----------------------------------------------------------

variable "enable_ssh_fallback" {
  description = "Associate an EC2 key pair so the SSH fallback scanning path can be tested. SSM Run Command is the primary path and needs none of this."
  type        = bool
  default     = false
}

variable "ssh_public_key" {
  description = "OpenSSH PUBLIC key (ssh-rsa/ssh-ed25519 ...). Only the public half is ever handled; no private key is generated, stored or output by this lab."
  type        = string
  default     = ""

  validation {
    condition     = var.ssh_public_key == "" || can(regex("^(ssh-rsa|ssh-ed25519|ecdsa-sha2-nistp(256|384|521)) ", var.ssh_public_key))
    error_message = "ssh_public_key must be an OpenSSH public key string, not a path and not a private key."
  }
}

# --- S3 staging -------------------------------------------------------------

variable "certificate_bucket_name" {
  description = "Bucket holding the staged certificate material, or null when S3 is disabled (the instance then generates its own self-signed material)."
  type        = string
  default     = null
}

variable "certificate_bucket_arn" {
  description = "ARN of the certificate bucket, for the instance role policy."
  type        = string
  default     = null
}

variable "ec2_staging_prefix" {
  description = "S3 prefix the instance downloads from."
  type        = string
  default     = "ec2-staging"
}

# --- Certificate layout -----------------------------------------------------

variable "nginx_certificate_id" {
  description = "Certificate id served by nginx."
  type        = string
  default     = "phase1-cert-027"
}

variable "apache_certificate_id" {
  description = "Certificate id served by Apache."
  type        = string
  default     = "phase1-cert-028"
}

variable "java_certificate_id" {
  description = "Certificate id converted to PKCS#12 and JKS for the Spring Boot layout."
  type        = string
  default     = "phase1-cert-029"
}

variable "expired_certificate_id" {
  description = "Certificate id used for the EXPIRED filesystem scenario, or null when the OpenSSL helper is disabled."
  type        = string
  default     = null
}

variable "keystore_password" {
  description = "Password for the PKCS#12 and JKS keystores built on the instance. TEST VALUE ONLY."
  type        = string
  sensitive   = true
}

variable "filesystem_inventory_json" {
  description = "Machine-readable description of the on-disk layout, written to /opt/certificates/certificate-inventory.json so a scanner can be validated against a known-good answer."
  type        = string
  default     = "{}"
}
