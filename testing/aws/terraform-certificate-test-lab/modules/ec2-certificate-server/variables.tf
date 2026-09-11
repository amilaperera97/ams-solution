# ---------------------------------------------------------------------------
# modules/ec2-certificate-server
#
# A single small EC2 instance that hosts on-disk certificate material in
# realistic locations so the discovery application can scan a filesystem via
# SSM Run Command (primary) or SSH (fallback).
#
# COST NOTE: this is the only always-on compute in the lab. t3.micro in
# eu-west-1 is roughly 0.0108 USD/hour plus the EBS volume.
# ---------------------------------------------------------------------------

variable "name" {
  description = "Instance name, used for the Name tag."
  type        = string
}

variable "ami_id" {
  description = "AMI to launch. Phase 1 uses the latest Amazon Linux 2023 x86_64 AMI resolved by a data source."
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type. Keep this small - the instance only stores and serves test certificates."
  type        = string
  default     = "t3.micro"
}

variable "subnet_id" {
  description = "Subnet the instance is launched into."
  type        = string
}

variable "security_group_ids" {
  description = "Security groups attached to the instance."
  type        = list(string)
}

variable "iam_instance_profile" {
  description = "Name of the instance profile granting SSM access and read access to the lab S3 certificate prefix."
  type        = string
}

variable "user_data" {
  description = "Rendered cloud-init/user-data script that lays down the certificate material."
  type        = string
  sensitive   = true
}

variable "key_name" {
  description = "EC2 key pair name for the optional SSH fallback path. Null means no key pair is associated."
  type        = string
  default     = null
}

variable "associate_public_ip_address" {
  description = "Give the instance a public IP. Phase 1 defaults to true so the SSM agent can reach the SSM endpoints through the internet gateway WITHOUT a paid NAT gateway."
  type        = bool
  default     = true
}

variable "root_volume_size" {
  description = "Root EBS volume size in GiB."
  type        = number
  default     = 10
}

variable "tags" {
  description = "Tags applied to the instance and its root volume."
  type        = map(string)
  default     = {}
}
