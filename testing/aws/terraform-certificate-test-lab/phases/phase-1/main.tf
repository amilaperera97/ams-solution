# ===========================================================================
# Phase 1 - root module
#
# Wires the category modules together:
#
#   common          naming + tagging
#   networking      dedicated VPC, subnets, security groups
#   certificates    ACM issued + ACM imported + all local test PKI
#   certificate_store  private S3 bucket (certificate store + EC2 staging)
#   compute         EC2 certificate server + SSM instance role
#   load_balancers  ALB (HTTP + HTTPS/SNI) and NLB (TLS + TCP)
#   api_gateway     minimal REST API + REGIONAL custom domains
#   secrets         Secrets Manager + SSM Parameter Store
#   iam_certificates  IAM server certificate (global, legacy store)
#   cloudfront      optional, default CloudFront certificate only
#
# Dependency order is expressed through data flow, not depends_on, apart
# from the two places where AWS genuinely requires ordering.
# ===========================================================================

data "aws_caller_identity" "current" {}

# ---------------------------------------------------------------------------
# Naming and tagging
# ---------------------------------------------------------------------------
module "common" {
  source = "../../modules/common"

  project_name     = var.project_name
  environment_name = var.environment_name
  phase            = "phase-1"
  additional_tags  = var.additional_tags
}

# Random suffix so the globally unique bucket name never collides.
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

# ---------------------------------------------------------------------------
# Networking - always created; the load balancers and EC2 depend on it.
# ---------------------------------------------------------------------------
module "networking" {
  source = "./networking"

  name_prefix = module.common.name_prefix
  tags        = module.common.common_tags
  aws_region  = var.aws_region

  vpc_cidr               = var.vpc_cidr
  subnet_count           = var.subnet_count
  enable_private_subnets = var.enable_private_subnets
  enable_vpc_endpoints   = var.enable_vpc_endpoints

  alb_ingress_cidrs       = var.allowed_ingress_cidrs
  enable_ssh_fallback     = var.enable_ssh_fallback
  ssh_allowed_cidrs       = var.ssh_allowed_cidrs
  allow_ssh_from_anywhere = var.allow_ssh_from_anywhere
}

# ---------------------------------------------------------------------------
# All 30 certificates' material.
# ---------------------------------------------------------------------------
module "certificates" {
  source = "./acm"

  name_prefix      = module.common.name_prefix
  tags             = module.common.common_tags
  certificate_tags = local.certificate_tags

  acm_issued_definitions = local.acm_issued_definitions
  acm_issuance_mode      = var.acm_issuance_mode

  create_route53_test_zone    = var.create_route53_test_zone
  route53_test_zone_name      = var.certificate_test_domain
  wait_for_acm_dns_validation = var.wait_for_acm_dns_validation

  enable_private_ca     = var.enable_private_ca
  private_ca_usage_mode = var.private_ca_usage_mode

  generated_certificate_definitions = local.generated_certificate_definitions

  enable_openssl_generated_certificates = var.enable_openssl_generated_certificates
  openssl_script_path                   = local.openssl_script_path
  generated_certificates_dir            = local.generated_certificates_dir
  openssl_generation_trigger            = var.openssl_generation_trigger
  pkcs12_password                       = var.pkcs12_password
}

# ---------------------------------------------------------------------------
# S3 certificate store + EC2 staging area.
# ---------------------------------------------------------------------------
module "certificate_store" {
  source = "./s3"
  count  = var.enable_s3 ? 1 : 0

  name_prefix      = module.common.name_prefix
  tags             = module.common.common_tags
  certificate_tags = local.certificate_tags

  bucket_name       = local.s3_bucket_name
  force_destroy     = var.s3_force_destroy
  enable_versioning = var.s3_enable_versioning

  store_certificate_ids         = local.s3_store_certificate_ids
  store_openssl_certificate_ids = local.s3_store_openssl_certificate_ids
  pkcs12_certificate_id         = local.pkcs12_certificate_id

  staging_certificate_ids         = var.enable_ec2 ? local.ec2_staging_certificate_ids : []
  staging_openssl_certificate_ids = var.enable_ec2 ? local.ec2_staging_openssl_certificate_ids : []

  ca_signed_certificate_ids = local.ca_signed_certificate_ids

  generated_material = module.certificates.pem_material
  openssl_material   = module.certificates.openssl_material
  pkcs12_base64      = module.certificates.openssl_pkcs12_base64
}

# ---------------------------------------------------------------------------
# EC2 certificate server (filesystem scenarios + SSM Run Command target).
# ---------------------------------------------------------------------------
module "compute" {
  source = "./compute"

  name_prefix = module.common.name_prefix
  tags        = module.common.common_tags
  aws_region  = var.aws_region

  enable_ec2       = var.enable_ec2
  instance_type    = var.ec2_instance_type
  root_volume_size = var.ec2_root_volume_size

  subnet_id                   = module.networking.public_subnet_ids[0]
  security_group_ids          = [module.networking.certificate_server_security_group_id]
  associate_public_ip_address = true

  enable_ssh_fallback = var.enable_ssh_fallback
  ssh_public_key      = var.ssh_public_key

  certificate_bucket_name = one(module.certificate_store[*].bucket_name)
  certificate_bucket_arn  = one(module.certificate_store[*].bucket_arn)
  ec2_staging_prefix      = "ec2-staging"

  expired_certificate_id    = local.expired_filesystem_certificate_id
  keystore_password         = var.pkcs12_password
  filesystem_inventory_json = local.filesystem_inventory_json
}

# ---------------------------------------------------------------------------
# Load balancers - the certificate-to-listener relationships.
# ---------------------------------------------------------------------------
module "load_balancers" {
  source = "./load-balancers"

  name_prefix    = module.common.name_prefix
  lb_name_prefix = local.load_balancer_name_prefix
  tags           = module.common.common_tags

  vpc_id             = module.networking.vpc_id
  subnet_ids         = module.networking.public_subnet_ids
  security_group_ids = [module.networking.load_balancer_security_group_id]

  internal            = var.internal_load_balancers
  ssl_policy          = var.ssl_policy
  target_instance_ids = module.compute.instance_ids

  enable_alb               = var.enable_alb
  enable_alb_http_listener = true
  enable_nlb               = var.enable_nlb
  enable_nlb_tcp_listener  = true

  alb_default_certificate_arn = local.alb_default_certificate_arn
  alb_sni_certificate_arns    = local.alb_sni_certificate_arns
  nlb_default_certificate_arn = local.nlb_default_certificate_arn
  nlb_sni_certificate_arns    = local.nlb_sni_certificate_arns
}

# ---------------------------------------------------------------------------
# API Gateway - the custom-domain-to-certificate relationship.
# ---------------------------------------------------------------------------
module "api_gateway" {
  source = "./api-gateway"

  name_prefix      = module.common.name_prefix
  tags             = module.common.common_tags
  certificate_tags = local.certificate_tags

  enable_api_gateway = var.enable_api_gateway

  imported_domain_name     = local.api_gateway_imported_domain_name
  imported_certificate_arn = local.api_gateway_imported_certificate_arn

  acm_issued_domain_name     = local.api_gateway_acm_issued_domain_name
  acm_issued_certificate_arn = local.api_gateway_acm_issued_certificate_arn
}

# ---------------------------------------------------------------------------
# Secrets Manager + Parameter Store certificate stores.
# ---------------------------------------------------------------------------
module "secrets" {
  source = "./secrets"

  name_prefix      = module.common.name_prefix
  tags             = module.common.common_tags
  certificate_tags = local.certificate_tags

  store_path_prefix            = var.certificate_store_path_prefix
  enable_secrets_manager       = var.enable_secrets_manager
  enable_parameter_store       = var.enable_parameter_store
  secrets_recovery_window_days = var.secrets_recovery_window_days

  secrets_manager_certificate_ids         = local.secrets_manager_certificate_ids
  parameter_store_certificate_ids         = local.parameter_store_certificate_ids
  parameter_store_openssl_certificate_ids = local.parameter_store_openssl_certificate_ids

  generated_material  = module.certificates.pem_material
  openssl_material    = module.certificates.openssl_material
  certificate_domains = local.certificate_domains
}

# ---------------------------------------------------------------------------
# IAM server certificate - the legacy, global certificate store.
# ---------------------------------------------------------------------------
module "iam_certificates" {
  source = "./iam"

  name_prefix      = module.common.name_prefix
  tags             = module.common.common_tags
  certificate_tags = local.certificate_tags

  enable_iam_server_certificate = var.enable_iam_server_certificate
  certificate_id                = "phase1-cert-018"

  generated_material = module.certificates.pem_material
}

# ---------------------------------------------------------------------------
# CloudFront - optional, DEFAULT certificate only. No ACM, no us-east-1.
# ---------------------------------------------------------------------------
module "cloudfront" {
  source = "./cloudfront"

  name_prefix = module.common.name_prefix
  tags        = module.common.common_tags

  enable_cloudfront  = var.enable_cloudfront
  origin_domain_name = module.load_balancers.alb_dns_name
}
