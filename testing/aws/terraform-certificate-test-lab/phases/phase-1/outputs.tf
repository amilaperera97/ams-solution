# ===========================================================================
# Phase 1 - outputs
#
# The two you will use most:
#   terraform output -json certificate_inventory   full 30-scenario inventory
#   terraform output -json service_relationships   certificate-to-service map
# ===========================================================================

# ---------------------------------------------------------------------------
# Environment identity
# ---------------------------------------------------------------------------

output "aws_account_id" {
  description = "Account the lab was deployed into."
  value       = data.aws_caller_identity.current.account_id
}

output "aws_region" {
  description = "Region the lab was deployed into. Phase 1 is single-region."
  value       = var.aws_region
}

output "name_prefix" {
  description = "Prefix every lab resource name starts with. Use it to filter the lab out of a shared account."
  value       = module.common.name_prefix
}

output "common_tags" {
  description = "Tag set applied to every resource. Use Project = certificate-test-lab to scope discovery."
  value       = module.common.common_tags
}

# ---------------------------------------------------------------------------
# The certificate inventory
# ---------------------------------------------------------------------------

output "certificate_inventory" {
  description = "All 30 certificate scenarios with ARNs, domains, SANs, type, issuance, region, account, attachment, expiry scenario, storage location and the expected scanner behaviour. This is the primary machine-readable artefact."
  value       = local.certificate_inventory
}

output "certificate_inventory_summary" {
  description = "Counts by category, management model, ACM presence, attachment state and expiry scenario."
  value       = local.inventory_summary
}

output "certificate_inventory_file" {
  description = "Path of the inventory JSON written on apply, or null when enable_inventory_file = false."
  value       = var.enable_inventory_file ? local_file.certificate_inventory[0].filename : null
}

output "certificate_arns_by_id" {
  description = "Flat map of certificate id to ARN. Null for scenarios with no ARN (filesystem, S3, Secrets Manager, Parameter Store, and the deferred CloudFront scenario)."
  value       = local.runtime_arn
}

output "acm_issued_certificates" {
  description = "The ten AWS-managed / ACM-issued scenarios as ACM reports them, including status and renewal eligibility."
  value       = module.certificates.acm_issued_certificates
}

output "acm_imported_certificates" {
  description = "The ACM-imported (customer-managed) scenarios as ACM reports them. Type = IMPORTED, RenewalEligibility = INELIGIBLE."
  value       = module.certificates.acm_imported_certificates
}

output "acm_issued_expected_status" {
  description = "The ACM status you should expect for the ACM-issued scenarios under the current configuration: PENDING_VALIDATION unless the test zone is delegated or AWS Private CA is used."
  value       = local.acm_issued_expected_status
}

output "acm_dns_validation_records" {
  description = "DNS records that must resolve in PUBLIC DNS for each ACM-issued certificate to become ISSUED. Empty when acm_issuance_mode = private_ca."
  value       = module.certificates.acm_issued_validation_records
}

output "route53_test_zone_name_servers" {
  description = "Name servers to delegate to at your registrar when create_route53_test_zone = true. Empty otherwise."
  value       = module.certificates.route53_test_zone_name_servers
}

output "private_ca_arn" {
  description = "ARN of the AWS Private CA root, or null when disabled."
  value       = module.certificates.private_ca_arn
}

output "lab_ca_certificate_pem" {
  description = "The LOCAL test CA certificate, so a scanner can verify the CA-signed scenarios. Public material only."
  value       = module.certificates.lab_ca_certificate_pem
}

# ---------------------------------------------------------------------------
# Certificate-to-service relationships
# ---------------------------------------------------------------------------

output "service_relationships" {
  description = "Every certificate-to-service relationship the discovery application must reconstruct."
  value = {
    load_balancer_listeners = module.load_balancers.listener_certificate_relationships
    api_gateway_domains     = module.api_gateway.custom_domains
    cloudfront = {
      distribution_id          = module.cloudfront.distribution_id
      distribution_domain_name = module.cloudfront.distribution_domain_name
      uses_default_certificate = module.cloudfront.uses_default_certificate
      phase_1_limitation       = module.cloudfront.phase_1_limitation
    }
    iam_server_certificate = {
      arn         = module.iam_certificates.server_certificate_arn
      name        = module.iam_certificates.server_certificate_name
      path        = module.iam_certificates.iam_path
      attached_to = "NONE - an IAM server certificate can only be used by a Classic Load Balancer or CloudFront"
    }
  }
}

output "load_balancers" {
  description = "ALB and NLB identifiers and listener ARNs."
  value = {
    alb = {
      arn                     = module.load_balancers.alb_arn
      dns_name                = module.load_balancers.alb_dns_name
      https_listener_arn      = module.load_balancers.alb_https_listener_arn
      http_listener_arn       = module.load_balancers.alb_http_listener_arn
      default_certificate_arn = module.load_balancers.alb_default_certificate_arn
      sni_certificate_arns    = module.load_balancers.alb_sni_certificate_arns
    }
    nlb = {
      arn                     = module.load_balancers.nlb_arn
      dns_name                = module.load_balancers.nlb_dns_name
      tls_listener_arn        = module.load_balancers.nlb_tls_listener_arn
      tcp_listener_arn        = module.load_balancers.nlb_tcp_listener_arn
      default_certificate_arn = module.load_balancers.nlb_default_certificate_arn
      sni_certificate_arns    = module.load_balancers.nlb_sni_certificate_arns
    }
  }
}

output "api_gateway" {
  description = "REST API and custom domain details."
  value = {
    rest_api_id               = module.api_gateway.rest_api_id
    stage_name                = module.api_gateway.stage_name
    stage_invoke_url          = module.api_gateway.stage_invoke_url
    custom_domains            = module.api_gateway.custom_domains
    acm_issued_domain_created = module.api_gateway.acm_issued_domain_created
  }
}

# ---------------------------------------------------------------------------
# Certificate stores
# ---------------------------------------------------------------------------

output "certificate_stores" {
  description = "Where to look for each non-ACM certificate store."
  value = {
    s3 = {
      bucket_name        = one(module.certificate_store[*].bucket_name)
      bucket_arn         = one(module.certificate_store[*].bucket_arn)
      certificate_prefix = one(module.certificate_store[*].certificate_prefix)
      ec2_staging_prefix = one(module.certificate_store[*].ec2_staging_prefix)
      object_keys        = one(module.certificate_store[*].certificate_object_keys)
    }
    secrets_manager = {
      path_prefix  = module.secrets.store_path_prefix
      secret_names = module.secrets.secrets_manager_secret_names
      secret_arns  = module.secrets.secrets_manager_secret_arns
    }
    parameter_store = {
      path_prefix = module.secrets.store_path_prefix
      parameters  = module.secrets.parameter_store_paths
    }
    iam = {
      path = module.iam_certificates.iam_path
      name = module.iam_certificates.server_certificate_name
      arn  = module.iam_certificates.server_certificate_arn
    }
    ec2_filesystem = {
      instance_id = module.compute.instance_id
      paths       = module.compute.filesystem_certificate_paths
    }
  }
}

# ---------------------------------------------------------------------------
# Scanning entry points
# ---------------------------------------------------------------------------

output "ssm_scan_target" {
  description = "Everything needed to drive a certificate scan over SSM Run Command from the Spring Boot backend."
  value = {
    instance_id             = module.compute.instance_id
    instance_role_arn       = module.compute.instance_role_arn
    document_name           = "AWS-RunShellScript"
    scanner_script          = "/usr/local/bin/certificate-lab-scan"
    expected_answer_file    = "/opt/certificates/certificate-inventory.json"
    bootstrap_log           = "/var/log/certificate-lab-bootstrap.log"
    bootstrap_complete_flag = "/opt/certificates/BOOTSTRAP_COMPLETE"
    example_cli_command     = module.compute.certificate_scan_command
  }
}

output "ssh_fallback" {
  description = "State of the optional SSH fallback scanning path."
  value = {
    enabled             = var.enable_ssh_fallback
    security_group_id   = module.networking.certificate_server_security_group_id
    port_22_rule_exists = module.networking.ssh_fallback_enabled
    key_pair_name       = module.compute.ssh_key_name
    public_ip           = module.compute.instance_public_ip
    note                = "SSM Run Command is the primary path and needs no inbound port. When enable_ssh_fallback = false no port 22 rule exists at all."
  }
}

# ---------------------------------------------------------------------------
# Networking
# ---------------------------------------------------------------------------

output "networking" {
  description = "Lab VPC details. Nothing pre-existing was touched."
  value = {
    vpc_id                               = module.networking.vpc_id
    vpc_cidr                             = module.networking.vpc_cidr
    public_subnet_ids                    = module.networking.public_subnet_ids
    private_subnet_ids                   = module.networking.private_subnet_ids
    availability_zones                   = module.networking.availability_zones
    load_balancer_security_group_id      = module.networking.load_balancer_security_group_id
    certificate_server_security_group_id = module.networking.certificate_server_security_group_id
  }
}

# ---------------------------------------------------------------------------
# Cost and teardown reminders
# ---------------------------------------------------------------------------

output "ongoing_cost_resources" {
  description = "Resources in THIS deployment that cost money for as long as they exist. Destroy the lab when you are done testing."
  value = compact([
    var.enable_alb ? "ALB (~18 USD/month + LCU)" : "",
    var.enable_nlb ? "NLB (~20 USD/month + NLCU)" : "",
    var.enable_ec2 ? "EC2 ${var.ec2_instance_type} + ${var.ec2_root_volume_size} GiB gp3 (~9 USD/month)" : "",
    var.enable_secrets_manager ? "Secrets Manager x${length(local.secrets_manager_certificate_ids)} (~0.40 USD/secret/month)" : "",
    var.enable_s3 ? "S3 bucket (pennies)" : "",
    var.create_route53_test_zone ? "Route 53 hosted zone (0.50 USD/month)" : "",
    var.enable_vpc_endpoints ? "3 SSM interface VPC endpoints (~7.50 USD/month each per AZ)" : "",
    var.enable_private_ca ? "AWS PRIVATE CA - ${var.private_ca_usage_mode} (~400 USD/month GENERAL_PURPOSE, ~50 USD/month SHORT_LIVED_CERTIFICATE) - THE MOST EXPENSIVE ITEM IN THIS LAB" : "",
    var.enable_cloudfront ? "CloudFront distribution (request-based, ~0 with no traffic)" : "",
  ])
}

output "destroy_command" {
  description = "How to tear the whole phase down."
  value       = "cd phases/phase-1 && terraform destroy"
}
