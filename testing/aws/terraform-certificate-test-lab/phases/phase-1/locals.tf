# ===========================================================================
# Phase 1 - THE 30 CERTIFICATE TEST SCENARIOS
#
# This file is the SINGLE SOURCE OF TRUTH for the test matrix. Everything
# else - the resources, the tags, the inventory output and the JSON/YAML
# matrix - is derived from it.
#
# certificate-test-matrix.yaml is a human-readable mirror of this data and
# README.md renders it as a table. If you change a scenario here, update
# those two as well.
#
# NAMING
#   phase1-cert-001 .. phase1-cert-030
#
# BREAKDOWN
#   001-010  AWS-managed / ACM-ISSUED           (10)
#   011-020  customer-managed, IMPORTED to ACM  (10, of which 018 is IAM)
#   021-030  customer-managed, OUTSIDE ACM      (10)
# ===========================================================================

locals {
  # Short aliases used throughout the scenario definitions.
  pub = var.certificate_test_domain
  int = var.certificate_internal_domain

  # ACM-issued certificates only become ISSUED (and therefore attachable)
  # under one of these two configurations.
  acm_issued_are_attachable = var.attach_acm_issued_certificates

  acm_issuance_method = var.acm_issuance_mode == "private_ca" ? "ACM_PRIVATE_CA_ISSUED" : "ACM_PUBLIC_DNS_VALIDATED"

  acm_issued_expected_status = (
    var.acm_issuance_mode == "private_ca" ? "ISSUED" :
    var.wait_for_acm_dns_validation ? "ISSUED" : "PENDING_VALIDATION"
  )
}

# ---------------------------------------------------------------------------
# ACM-ISSUED certificate REQUESTS (001-010).
#
# phase1-cert-005 is absent on purpose: CloudFront requires a us-east-1
# certificate and Phase 1 is eu-west-1 only. See cloudfront/README.md.
# ---------------------------------------------------------------------------
locals {
  acm_issued_definitions = {
    "phase1-cert-001" = {
      domain_name               = "cert001.${local.pub}"
      subject_alternative_names = []
      key_algorithm             = "RSA_2048"
    }

    "phase1-cert-002" = {
      domain_name               = "*.app002.${local.pub}"
      subject_alternative_names = ["app002.${local.pub}"]
      key_algorithm             = "RSA_2048"
    }

    "phase1-cert-003" = {
      domain_name = "cert003.${local.pub}"
      subject_alternative_names = [
        "www.cert003.${local.pub}",
        "api.cert003.${local.pub}",
        "admin.cert003.${local.pub}",
      ]
      key_algorithm = "RSA_2048"
    }

    "phase1-cert-004" = {
      domain_name               = "api004.${local.pub}"
      subject_alternative_names = []
      key_algorithm             = "RSA_2048"
    }

    # phase1-cert-005 intentionally omitted - CloudFront / us-east-1.

    "phase1-cert-006" = {
      domain_name               = "nlb006.${local.pub}"
      subject_alternative_names = []
      key_algorithm             = "RSA_2048"
    }

    "phase1-cert-007" = {
      domain_name               = "sni007.${local.pub}"
      subject_alternative_names = []
      key_algorithm             = "RSA_2048"
    }

    "phase1-cert-008" = {
      domain_name               = "unused008.${local.pub}"
      subject_alternative_names = []
      key_algorithm             = "RSA_2048"
    }

    # ECDSA. ACM supports EC_prime256v1 / EC_secp384r1 / EC_secp521r1 for
    # issued certificates; ALB and NLB support P-256 and P-384.
    "phase1-cert-009" = {
      domain_name = "ecdsa009.${local.pub}"
      subject_alternative_names = [
        "alt1.ecdsa009.${local.pub}",
        "alt2.ecdsa009.${local.pub}",
      ]
      key_algorithm = "EC_prime256v1"
    }

    "phase1-cert-010" = {
      domain_name               = "*.svc010.${local.pub}"
      subject_alternative_names = ["svc010.${local.pub}"]
      key_algorithm             = "RSA_2048"
    }
  }
}

# ---------------------------------------------------------------------------
# LOCALLY GENERATED certificates (hashicorp/tls).
#
# import_to_acm = true  -> 011-017, 019, 020  (ACM Type = IMPORTED)
# import_to_acm = false -> 018, 021, 022, 023, 025, 027, 028, 029
#                          (IAM / Secrets Manager / Parameter Store / S3 / EC2)
#
# 024, 026 and 030 are NOT here - they need OpenSSL back-dating or PKCS#12
# and come from scripts/generate-test-certificates.sh.
# ---------------------------------------------------------------------------
locals {
  generated_certificate_definitions = {
    # --- 011: imported, self-signed, attached to the NLB TLS listener ------
    "phase1-cert-011" = {
      common_name    = "nlb011.${local.int}"
      dns_names      = ["nlb011.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 8760 # 365 days
      import_to_acm  = true
    }

    # --- 012: imported WILDCARD, attached to the ALB via SNI --------------
    "phase1-cert-012" = {
      common_name    = "*.alb012.${local.int}"
      dns_names      = ["*.alb012.${local.int}", "alb012.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 8760 # 365 days
      import_to_acm  = true
    }

    # --- 013: imported MULTI-SAN, 60 days, attached to the ALB via SNI ----
    "phase1-cert-013" = {
      common_name = "multi013.${local.int}"
      dns_names = [
        "multi013.${local.int}",
        "www.multi013.${local.int}",
        "api.multi013.${local.int}",
        "admin.multi013.${local.int}",
      ]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 1440 # 60 days
      import_to_acm  = true
    }

    # --- 014: imported, CRITICAL (7 days), unattached ---------------------
    "phase1-cert-014" = {
      common_name    = "expiring014.${local.int}"
      dns_names      = ["expiring014.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 168 # 7 days
      import_to_acm  = true
    }

    # --- 015: imported, CA-SIGNED, 3 years, unattached --------------------
    "phase1-cert-015" = {
      common_name    = "longlived015.${local.int}"
      dns_names      = ["longlived015.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 26280 # 1095 days
      ca_signed      = true
      import_to_acm  = true
    }

    # --- 016: imported, ALB HTTPS listener DEFAULT certificate ------------
    "phase1-cert-016" = {
      common_name    = "alb016.${local.int}"
      dns_names      = ["alb016.${local.int}", "www.alb016.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 17520 # 730 days
      import_to_acm  = true
    }

    # --- 017: imported ECDSA P-256, CA-signed, ALB via SNI ----------------
    "phase1-cert-017" = {
      common_name    = "ecdsa017.${local.int}"
      dns_names      = ["ecdsa017.${local.int}"]
      key_algorithm  = "ECDSA"
      ecdsa_curve    = "P256"
      validity_hours = 8760 # 365 days
      ca_signed      = true
      import_to_acm  = true
    }

    # --- 018: IAM SERVER CERTIFICATE (not ACM at all) --------------------
    "phase1-cert-018" = {
      common_name    = "iam018.${local.int}"
      dns_names      = ["iam018.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 8760 # 365 days
      ca_signed      = true
      import_to_acm  = false
    }

    # --- 019: imported, API Gateway custom domain ------------------------
    # Uses the PUBLIC-shaped domain because it becomes an API Gateway custom
    # domain name. API Gateway does not verify DNS ownership.
    "phase1-cert-019" = {
      common_name    = "api019.${local.pub}"
      dns_names      = ["api019.${local.pub}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 8760 # 365 days
      import_to_acm  = true
    }

    # --- 020: NEAR-DUPLICATE of 016 - same subject and SANs, new key -----
    "phase1-cert-020" = {
      common_name    = "alb016.${local.int}"
      dns_names      = ["alb016.${local.int}", "www.alb016.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 17520 # 730 days
      import_to_acm  = true
    }

    # --- 021: Secrets Manager, 30 days -----------------------------------
    "phase1-cert-021" = {
      common_name    = "sm021.${local.int}"
      dns_names      = ["sm021.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 720 # 30 days
      import_to_acm  = false
    }

    # --- 022: Secrets Manager, WILDCARD, 1 day ---------------------------
    "phase1-cert-022" = {
      common_name    = "*.sm022.${local.int}"
      dns_names      = ["*.sm022.${local.int}", "sm022.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 24 # 1 day
      import_to_acm  = false
    }

    # --- 023: Parameter Store, 90 days -----------------------------------
    "phase1-cert-023" = {
      common_name    = "ps023.${local.int}"
      dns_names      = ["ps023.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 2160 # 90 days
      import_to_acm  = false
    }

    # --- 025: S3, RSA-4096, CA-signed, multi-SAN, 2 years ----------------
    "phase1-cert-025" = {
      common_name = "s3-025.${local.int}"
      dns_names = [
        "s3-025.${local.int}",
        "bucket.s3-025.${local.int}",
        "objects.s3-025.${local.int}",
      ]
      key_algorithm  = "RSA"
      rsa_bits       = 4096
      validity_hours = 17520 # 730 days
      ca_signed      = true
      import_to_acm  = false
    }

    # --- 027: EC2 filesystem, nginx, CRITICAL (7 days) -------------------
    "phase1-cert-027" = {
      common_name    = "nginx027.${local.int}"
      dns_names      = ["nginx027.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 168 # 7 days
      import_to_acm  = false
    }

    # --- 028: EC2 filesystem, Apache, ECDSA P-384 ------------------------
    "phase1-cert-028" = {
      common_name    = "apache028.${local.int}"
      dns_names      = ["apache028.${local.int}"]
      key_algorithm  = "ECDSA"
      ecdsa_curve    = "P384"
      validity_hours = 8760 # 365 days
      import_to_acm  = false
    }

    # --- 029: EC2 filesystem, Java/Spring Boot keystores, 60 days --------
    "phase1-cert-029" = {
      common_name    = "springboot029.${local.int}"
      dns_names      = ["springboot029.${local.int}"]
      key_algorithm  = "RSA"
      rsa_bits       = 2048
      validity_hours = 1440 # 60 days
      import_to_acm  = false
    }
  }

  # Which generated certificates go where.
  ca_signed_certificate_ids = [
    for id, definition in local.generated_certificate_definitions : id
    if lookup(definition, "ca_signed", false)
  ]

  secrets_manager_certificate_ids = ["phase1-cert-021", "phase1-cert-022"]
  parameter_store_certificate_ids = ["phase1-cert-023"]
  s3_store_certificate_ids        = ["phase1-cert-025"]
  ec2_staging_certificate_ids     = ["phase1-cert-027", "phase1-cert-028", "phase1-cert-029"]

  # OpenSSL-produced scenarios and their destinations.
  openssl_enabled                         = var.enable_openssl_generated_certificates
  parameter_store_openssl_certificate_ids = local.openssl_enabled ? ["phase1-cert-024"] : []
  s3_store_openssl_certificate_ids        = local.openssl_enabled ? ["phase1-cert-026"] : []
  ec2_staging_openssl_certificate_ids     = local.openssl_enabled && var.enable_s3 ? ["phase1-cert-030"] : []
  pkcs12_certificate_id                   = local.openssl_enabled ? "phase1-cert-026" : null
  expired_filesystem_certificate_id       = local.openssl_enabled && var.enable_s3 ? "phase1-cert-030" : null
}

# ===========================================================================
# THE TEST MATRIX ITSELF
#
# Every entry carries the same set of keys so it can be rendered straight to
# JSON/YAML and diffed against whatever your Java discovery application
# reports. Fields marked "expected_" are the assertions your scanner should
# satisfy.
# ===========================================================================
locals {
  certificate_scenarios = {
    # =====================================================================
    # 001-010  AWS-MANAGED / ACM-ISSUED
    # =====================================================================

    "phase1-cert-001" = {
      certificate_id                  = "phase1-cert-001"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "ACM_ISSUED"
      issuance_method                 = local.acm_issuance_method
      domain                          = "cert001.${local.pub}"
      sans                            = ["cert001.${local.pub}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "AMAZON_CA_OR_AWS_PRIVATE_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 395
      attachment                      = local.acm_issued_are_attachable ? "ATTACHED" : "UNATTACHED"
      aws_service                     = "ELBV2_ALB_HTTPS_LISTENER_SNI"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ACM_LIST_CERTIFICATES"
      expected_renewal_type           = "ACM_AUTOMATIC"
      expected_scanner_classification = "AWS_MANAGED_AUTO_RENEWABLE"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "Plain single-domain ACM-issued certificate. Attached to the ALB HTTPS listener as an SNI certificate only when attach_acm_issued_certificates = true, because a listener rejects a PENDING_VALIDATION certificate."
    }

    "phase1-cert-002" = {
      certificate_id                  = "phase1-cert-002"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "ACM_ISSUED"
      issuance_method                 = local.acm_issuance_method
      domain                          = "*.app002.${local.pub}"
      sans                            = ["*.app002.${local.pub}", "app002.${local.pub}"]
      wildcard                        = true
      key_spec                        = "RSA-2048"
      signed_by                       = "AMAZON_CA_OR_AWS_PRIVATE_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 395
      attachment                      = local.acm_issued_are_attachable ? "ATTACHED" : "UNATTACHED"
      aws_service                     = "ELBV2_ALB_HTTPS_LISTENER_SNI"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ACM_LIST_CERTIFICATES"
      expected_renewal_type           = "ACM_AUTOMATIC"
      expected_scanner_classification = "AWS_MANAGED_AUTO_RENEWABLE_WILDCARD"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "WILDCARD ACM-issued certificate with the apex as an extra SAN. Tests wildcard detection on an AWS-managed certificate."
    }

    "phase1-cert-003" = {
      certificate_id                  = "phase1-cert-003"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "ACM_ISSUED"
      issuance_method                 = local.acm_issuance_method
      domain                          = "cert003.${local.pub}"
      sans                            = ["cert003.${local.pub}", "www.cert003.${local.pub}", "api.cert003.${local.pub}", "admin.cert003.${local.pub}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "AMAZON_CA_OR_AWS_PRIVATE_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 395
      attachment                      = local.acm_issued_are_attachable ? "ATTACHED" : "UNATTACHED"
      aws_service                     = "ELBV2_ALB_HTTPS_LISTENER_SNI"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ACM_LIST_CERTIFICATES"
      expected_renewal_type           = "ACM_AUTOMATIC"
      expected_scanner_classification = "AWS_MANAGED_AUTO_RENEWABLE_MULTI_SAN"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "MULTI-SAN ACM-issued certificate, four names. Tests SAN enumeration on an AWS-managed certificate."
    }

    "phase1-cert-004" = {
      certificate_id                  = "phase1-cert-004"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "ACM_ISSUED"
      issuance_method                 = local.acm_issuance_method
      domain                          = "api004.${local.pub}"
      sans                            = ["api004.${local.pub}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "AMAZON_CA_OR_AWS_PRIVATE_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 395
      attachment                      = local.acm_issued_are_attachable ? "ATTACHED" : "UNATTACHED"
      aws_service                     = "API_GATEWAY_REGIONAL_CUSTOM_DOMAIN"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "APIGATEWAY_GET_DOMAIN_NAMES"
      expected_renewal_type           = "ACM_AUTOMATIC"
      expected_scanner_classification = "AWS_MANAGED_AUTO_RENEWABLE"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "Intended for the API Gateway REGIONAL custom domain. The custom domain is only created when attach_acm_issued_certificates = true; otherwise phase1-cert-019 carries that relationship."
    }

    # ---------------------------------------------------------------------
    # 005 - CloudFront. NOT CREATED IN PHASE 1.
    # ---------------------------------------------------------------------
    "phase1-cert-005" = {
      certificate_id                  = "phase1-cert-005"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "NOT_CREATED"
      issuance_method                 = "NOT_IMPLEMENTED_IN_PHASE_1"
      domain                          = "cdn005.${local.pub}"
      sans                            = ["cdn005.${local.pub}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "AMAZON_CA"
      expiry_scenario                 = "NOT_APPLICABLE"
      validity_days                   = 0
      attachment                      = "NOT_APPLICABLE"
      aws_service                     = "CLOUDFRONT_DISTRIBUTION"
      storage_location                = "DEFERRED_TO_PHASE_2"
      expected_discovery_mechanism    = "CLOUDFRONT_LIST_DISTRIBUTIONS"
      expected_renewal_type           = "ACM_AUTOMATIC"
      expected_scanner_classification = "DEFERRED_TO_PHASE_2"
      region                          = "us-east-1"
      implemented                     = false
      duplicate_of                    = null
      notes                           = "HARD AWS LIMITATION: a CloudFront distribution can only use an ACM certificate issued in us-east-1. Phase 1 is eu-west-1 only, so no valid configuration exists and none is created. Phase 1 offers a CloudFront distribution using the DEFAULT CloudFront certificate instead (enable_cloudfront = true), which needs no ACM certificate in any region. Full Phase 2 design in phases/phase-1/cloudfront/README.md."
    }

    "phase1-cert-006" = {
      certificate_id                  = "phase1-cert-006"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "ACM_ISSUED"
      issuance_method                 = local.acm_issuance_method
      domain                          = "nlb006.${local.pub}"
      sans                            = ["nlb006.${local.pub}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "AMAZON_CA_OR_AWS_PRIVATE_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 395
      attachment                      = local.acm_issued_are_attachable ? "ATTACHED" : "UNATTACHED"
      aws_service                     = "ELBV2_NLB_TLS_LISTENER_SNI"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ELBV2_DESCRIBE_LISTENER_CERTIFICATES"
      expected_renewal_type           = "ACM_AUTOMATIC"
      expected_scanner_classification = "AWS_MANAGED_AUTO_RENEWABLE"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "Intended for the NLB TLS listener. Added as an additional SNI certificate when attach_acm_issued_certificates = true."
    }

    "phase1-cert-007" = {
      certificate_id                  = "phase1-cert-007"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "ACM_ISSUED"
      issuance_method                 = local.acm_issuance_method
      domain                          = "sni007.${local.pub}"
      sans                            = ["sni007.${local.pub}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "AMAZON_CA_OR_AWS_PRIVATE_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 395
      attachment                      = local.acm_issued_are_attachable ? "ATTACHED" : "UNATTACHED"
      aws_service                     = "ELBV2_ALB_HTTPS_LISTENER_SNI"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ELBV2_DESCRIBE_LISTENER_CERTIFICATES"
      expected_renewal_type           = "ACM_AUTOMATIC"
      expected_scanner_classification = "AWS_MANAGED_AUTO_RENEWABLE"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "Third certificate for the SAME ALB HTTPS listener. Exists specifically to prove the 1-listener-to-N-certificates relationship is discovered in full."
    }

    "phase1-cert-008" = {
      certificate_id                  = "phase1-cert-008"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "ACM_ISSUED"
      issuance_method                 = local.acm_issuance_method
      domain                          = "unused008.${local.pub}"
      sans                            = ["unused008.${local.pub}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "AMAZON_CA_OR_AWS_PRIVATE_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 395
      attachment                      = "UNATTACHED"
      aws_service                     = "NONE"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ACM_LIST_CERTIFICATES"
      expected_renewal_type           = "ACM_AUTOMATIC_BUT_INELIGIBLE_WHILE_UNUSED"
      expected_scanner_classification = "AWS_MANAGED_UNUSED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "DELIBERATELY UNATTACHED and never attached under any flag. Note the real AWS behaviour your scanner must model: ACM will not auto-renew a certificate that is not associated with an AWS service, so RenewalEligibility stays INELIGIBLE even though the certificate is ACM-issued."
    }

    "phase1-cert-009" = {
      certificate_id                  = "phase1-cert-009"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "ACM_ISSUED"
      issuance_method                 = local.acm_issuance_method
      domain                          = "ecdsa009.${local.pub}"
      sans                            = ["ecdsa009.${local.pub}", "alt1.ecdsa009.${local.pub}", "alt2.ecdsa009.${local.pub}"]
      wildcard                        = false
      key_spec                        = "ECDSA-P256"
      signed_by                       = "AMAZON_CA_OR_AWS_PRIVATE_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 395
      attachment                      = "UNATTACHED"
      aws_service                     = "NONE"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ACM_LIST_CERTIFICATES"
      expected_renewal_type           = "ACM_AUTOMATIC_BUT_INELIGIBLE_WHILE_UNUSED"
      expected_scanner_classification = "AWS_MANAGED_ECDSA_MULTI_SAN_UNUSED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "ECDSA (EC_prime256v1) AND multi-SAN on an AWS-managed certificate. Left unattached so the ECDSA key-algorithm path is tested independently of listener compatibility."
    }

    "phase1-cert-010" = {
      certificate_id                  = "phase1-cert-010"
      category                        = "AWS_MANAGED_ACM_ISSUED"
      management_model                = "AWS_MANAGED"
      acm_presence                    = "ACM_ISSUED"
      issuance_method                 = local.acm_issuance_method
      domain                          = "*.svc010.${local.pub}"
      sans                            = ["*.svc010.${local.pub}", "svc010.${local.pub}"]
      wildcard                        = true
      key_spec                        = "RSA-2048"
      signed_by                       = "AMAZON_CA_OR_AWS_PRIVATE_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 395
      attachment                      = "UNATTACHED"
      aws_service                     = "NONE"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ACM_LIST_CERTIFICATES"
      expected_renewal_type           = "ACM_AUTOMATIC_BUT_INELIGIBLE_WHILE_UNUSED"
      expected_scanner_classification = "AWS_MANAGED_WILDCARD_UNUSED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "Second WILDCARD AWS-managed certificate, unattached. Pairs with phase1-cert-002 to test attached-vs-unattached wildcard classification."
    }
  }
}

# ===========================================================================
# 011-020  CUSTOMER-MANAGED, IMPORTED INTO ACM (018 = IAM instead)
#
# Every one of these is ISSUED the moment it is imported, which is why they
# - not the ACM-issued certificates - carry the listener relationships by
# default. ACM never auto-renews an import: RenewalEligibility = INELIGIBLE.
# ===========================================================================
locals {
  certificate_scenarios_imported = {
    "phase1-cert-011" = {
      certificate_id                  = "phase1-cert-011"
      category                        = "CUSTOMER_MANAGED_IMPORTED_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "ACM_IMPORTED"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "nlb011.${local.int}"
      sans                            = ["nlb011.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 365
      attachment                      = "ATTACHED"
      aws_service                     = "ELBV2_NLB_TLS_LISTENER_DEFAULT"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ELBV2_DESCRIBE_LISTENERS"
      expected_renewal_type           = "MANUAL_REIMPORT"
      expected_scanner_classification = "CUSTOMER_MANAGED_IMPORTED_ATTACHED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "DEFAULT certificate on the NLB TLS listener. Always attached, regardless of any flag - this is the guaranteed NLB-to-certificate relationship."
    }

    "phase1-cert-012" = {
      certificate_id                  = "phase1-cert-012"
      category                        = "CUSTOMER_MANAGED_IMPORTED_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "ACM_IMPORTED"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "*.alb012.${local.int}"
      sans                            = ["*.alb012.${local.int}", "alb012.${local.int}"]
      wildcard                        = true
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 365
      attachment                      = "ATTACHED"
      aws_service                     = "ELBV2_ALB_HTTPS_LISTENER_SNI"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ELBV2_DESCRIBE_LISTENER_CERTIFICATES"
      expected_renewal_type           = "MANUAL_REIMPORT"
      expected_scanner_classification = "CUSTOMER_MANAGED_IMPORTED_WILDCARD_ATTACHED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "IMPORTED WILDCARD, attached to the ALB HTTPS listener via SNI."
    }

    "phase1-cert-013" = {
      certificate_id                  = "phase1-cert-013"
      category                        = "CUSTOMER_MANAGED_IMPORTED_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "ACM_IMPORTED"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "multi013.${local.int}"
      sans                            = ["multi013.${local.int}", "www.multi013.${local.int}", "api.multi013.${local.int}", "admin.multi013.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "WARNING"
      validity_days                   = 60
      attachment                      = "ATTACHED"
      aws_service                     = "ELBV2_ALB_HTTPS_LISTENER_SNI"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ELBV2_DESCRIBE_LISTENER_CERTIFICATES"
      expected_renewal_type           = "MANUAL_REIMPORT"
      expected_scanner_classification = "CUSTOMER_MANAGED_IMPORTED_MULTI_SAN_EXPIRING_SOON"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "IMPORTED MULTI-SAN expiring in 60 days while ATTACHED to a live listener. The highest-priority finding shape: in use, expiring, and not auto-renewable."
    }

    "phase1-cert-014" = {
      certificate_id                  = "phase1-cert-014"
      category                        = "CUSTOMER_MANAGED_IMPORTED_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "ACM_IMPORTED"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "expiring014.${local.int}"
      sans                            = ["expiring014.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "CRITICAL"
      validity_days                   = 7
      attachment                      = "UNATTACHED"
      aws_service                     = "NONE"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ACM_LIST_CERTIFICATES"
      expected_renewal_type           = "MANUAL_REIMPORT"
      expected_scanner_classification = "CUSTOMER_MANAGED_IMPORTED_CRITICAL_UNUSED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "The nearest-to-expiry certificate that can exist in ACM: 7 days. ACM REFUSES to import an already-expired certificate, which is why no EXPIRED scenario lives in ACM anywhere in this lab. NOTE: this certificate genuinely expires 7 days after apply - bump openssl_generation_trigger or re-apply to refresh."
    }

    "phase1-cert-015" = {
      certificate_id                  = "phase1-cert-015"
      category                        = "CUSTOMER_MANAGED_IMPORTED_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "ACM_IMPORTED"
      issuance_method                 = "LAB_CA_SIGNED_TLS_PROVIDER"
      domain                          = "longlived015.${local.int}"
      sans                            = ["longlived015.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "CERTIFICATE_TEST_LAB_LOCAL_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 1095
      attachment                      = "UNATTACHED"
      aws_service                     = "NONE"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ACM_LIST_CERTIFICATES"
      expected_renewal_type           = "MANUAL_REIMPORT"
      expected_scanner_classification = "CUSTOMER_MANAGED_IMPORTED_LONG_LIVED_CA_SIGNED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "CA-SIGNED (not self-signed) with a 3-year validity, imported WITH its chain. Tests long-lived detection and chain parsing. A 3-year leaf would be rejected by any public CA - only an import can have this shape, which is exactly why it is worth flagging."
    }

    "phase1-cert-016" = {
      certificate_id                  = "phase1-cert-016"
      category                        = "CUSTOMER_MANAGED_IMPORTED_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "ACM_IMPORTED"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "alb016.${local.int}"
      sans                            = ["alb016.${local.int}", "www.alb016.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 730
      attachment                      = "ATTACHED"
      aws_service                     = "ELBV2_ALB_HTTPS_LISTENER_DEFAULT"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ELBV2_DESCRIBE_LISTENERS"
      expected_renewal_type           = "MANUAL_REIMPORT"
      expected_scanner_classification = "CUSTOMER_MANAGED_IMPORTED_ATTACHED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "DEFAULT certificate on the ALB HTTPS listener. Always attached, regardless of any flag - this is the guaranteed ALB-to-certificate relationship. Deliberately duplicated by phase1-cert-020."
    }

    "phase1-cert-017" = {
      certificate_id                  = "phase1-cert-017"
      category                        = "CUSTOMER_MANAGED_IMPORTED_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "ACM_IMPORTED"
      issuance_method                 = "LAB_CA_SIGNED_TLS_PROVIDER"
      domain                          = "ecdsa017.${local.int}"
      sans                            = ["ecdsa017.${local.int}"]
      wildcard                        = false
      key_spec                        = "ECDSA-P256"
      signed_by                       = "CERTIFICATE_TEST_LAB_LOCAL_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 365
      attachment                      = "ATTACHED"
      aws_service                     = "ELBV2_ALB_HTTPS_LISTENER_SNI"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ELBV2_DESCRIBE_LISTENER_CERTIFICATES"
      expected_renewal_type           = "MANUAL_REIMPORT"
      expected_scanner_classification = "CUSTOMER_MANAGED_IMPORTED_ECDSA_ATTACHED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "IMPORTED ECDSA P-256, CA-signed, attached to the ALB HTTPS listener via SNI. Proves an ALB serves RSA and ECDSA certificates from the SAME listener. ALB/NLB support P-256 and P-384 only; P-521 is not supported by ELB and is therefore not used here."
    }

    # ---------------------------------------------------------------------
    # 018 - IAM SERVER CERTIFICATE (a different API surface entirely)
    # ---------------------------------------------------------------------
    "phase1-cert-018" = {
      certificate_id                  = "phase1-cert-018"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "LAB_CA_SIGNED_TLS_PROVIDER"
      domain                          = "iam018.${local.int}"
      sans                            = ["iam018.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "CERTIFICATE_TEST_LAB_LOCAL_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 365
      attachment                      = "UNATTACHED"
      aws_service                     = "IAM_SERVER_CERTIFICATE_STORE"
      storage_location                = "IAM_SERVER_CERTIFICATE"
      expected_discovery_mechanism    = "IAM_LIST_SERVER_CERTIFICATES"
      expected_renewal_type           = "MANUAL_DELETE_AND_REUPLOAD"
      expected_scanner_classification = "LEGACY_IAM_CERTIFICATE_STORE"
      region                          = "global"
      implemented                     = true
      duplicate_of                    = null
      notes                           = "IMPLEMENTED, not faked - aws_iam_server_certificate is a real resource. IAM is GLOBAL so this certificate has no region; its ARN has an empty region field. It is left UNATTACHED because an IAM server certificate can only be consumed by a CLASSIC Load Balancer or CloudFront, and this lab does not create a CLB. Filed under the dedicated IAM path /certificate-test-lab/ so it is trivially filterable."
    }

    "phase1-cert-019" = {
      certificate_id                  = "phase1-cert-019"
      category                        = "CUSTOMER_MANAGED_IMPORTED_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "ACM_IMPORTED"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "api019.${local.pub}"
      sans                            = ["api019.${local.pub}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 365
      attachment                      = "ATTACHED"
      aws_service                     = "API_GATEWAY_REGIONAL_CUSTOM_DOMAIN"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "APIGATEWAY_GET_DOMAIN_NAMES"
      expected_renewal_type           = "MANUAL_REIMPORT"
      expected_scanner_classification = "CUSTOMER_MANAGED_IMPORTED_ATTACHED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "Carries the API Gateway custom domain relationship unconditionally, because API Gateway also rejects a PENDING_VALIDATION certificate. REGIONAL endpoint type, so the certificate is in eu-west-1 - an EDGE endpoint would require us-east-1 and is deferred to Phase 2."
    }

    "phase1-cert-020" = {
      certificate_id                  = "phase1-cert-020"
      category                        = "CUSTOMER_MANAGED_IMPORTED_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "ACM_IMPORTED"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "alb016.${local.int}"
      sans                            = ["alb016.${local.int}", "www.alb016.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 730
      attachment                      = "UNATTACHED"
      aws_service                     = "NONE"
      storage_location                = "AWS_CERTIFICATE_MANAGER"
      expected_discovery_mechanism    = "ACM_LIST_CERTIFICATES"
      expected_renewal_type           = "MANUAL_REIMPORT"
      expected_scanner_classification = "DUPLICATE_CERTIFICATE_UNUSED"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = "phase1-cert-016"
      notes                           = "NEAR-DUPLICATE of phase1-cert-016: identical subject CN and identical SAN set, but a different key pair, different serial and a different SHA-256 fingerprint. Your scanner should group these as duplicates by subject+SAN while still distinguishing them by fingerprint, and should flag that one copy is attached and the other is dead weight."
    }
  }
}

# ===========================================================================
# 021-030  CUSTOMER-MANAGED, STORED OUTSIDE ACM
#
# Secrets Manager, Parameter Store, S3 and the EC2 filesystem. None of these
# stores has any notion of certificate lifecycle, so every renewal type here
# is MANUAL/EXTERNAL and every expiry date can only be learned by PARSING
# the certificate.
# ===========================================================================
locals {
  certificate_scenarios_non_acm = {
    "phase1-cert-021" = {
      certificate_id                  = "phase1-cert-021"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "sm021.${local.int}"
      sans                            = ["sm021.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "WARNING"
      validity_days                   = 30
      attachment                      = "UNATTACHED"
      aws_service                     = "SECRETS_MANAGER"
      storage_location                = "${var.certificate_store_path_prefix}/cert-021"
      expected_discovery_mechanism    = "SECRETSMANAGER_LIST_SECRETS_AND_GET_SECRET_VALUE"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "SECRET_STORE_CERTIFICATE_EXPIRING_SOON"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "JSON secret with certificate / privateKey / certificateChain fields. Discovery must LIST secrets, GET the value, then PARSE the PEM to learn notAfter - Secrets Manager itself reports no expiry."
    }

    "phase1-cert-022" = {
      certificate_id                  = "phase1-cert-022"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "*.sm022.${local.int}"
      sans                            = ["*.sm022.${local.int}", "sm022.${local.int}"]
      wildcard                        = true
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "CRITICAL"
      validity_days                   = 1
      attachment                      = "UNATTACHED"
      aws_service                     = "SECRETS_MANAGER"
      storage_location                = "${var.certificate_store_path_prefix}/cert-022"
      expected_discovery_mechanism    = "SECRETSMANAGER_LIST_SECRETS_AND_GET_SECRET_VALUE"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "SECRET_STORE_WILDCARD_CRITICAL"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "EXPIRES WITHIN 1 DAY. Wildcard. This certificate becomes EXPIRED roughly 24 hours after apply, which makes it a free extra EXPIRED test case if you leave the lab running - and a reason to re-apply before a clean CRITICAL test."
    }

    "phase1-cert-023" = {
      certificate_id                  = "phase1-cert-023"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "ps023.${local.int}"
      sans                            = ["ps023.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "WARNING"
      validity_days                   = 90
      attachment                      = "UNATTACHED"
      aws_service                     = "SSM_PARAMETER_STORE"
      storage_location                = "${var.certificate_store_path_prefix}/cert-023/{certificate,chain,private-key}"
      expected_discovery_mechanism    = "SSM_GET_PARAMETERS_BY_PATH"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "PARAMETER_STORE_CERTIFICATE"
      region                          = var.aws_region
      implemented                     = true
      duplicate_of                    = null
      notes                           = "Split across three parameters: certificate (String), chain (String), private-key (SecureString). Tests that a scanner correlates several parameters into one logical certificate. The certificate body being a plain String is realistic AND is itself a finding worth raising."
    }

    "phase1-cert-024" = {
      certificate_id                  = "phase1-cert-024"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "OPENSSL_LAB_CA_SIGNED_BACKDATED"
      domain                          = "ps024-expired.${local.int}"
      sans                            = ["ps024-expired.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "CERTIFICATE_TEST_LAB_OPENSSL_CA"
      expiry_scenario                 = "EXPIRED"
      validity_days                   = -35
      attachment                      = "UNATTACHED"
      aws_service                     = "SSM_PARAMETER_STORE"
      storage_location                = "${var.certificate_store_path_prefix}/cert-024/{certificate,chain,private-key}"
      expected_discovery_mechanism    = "SSM_GET_PARAMETERS_BY_PATH"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "PARAMETER_STORE_CERTIFICATE_EXPIRED"
      region                          = var.aws_region
      implemented                     = local.openssl_enabled
      duplicate_of                    = null
      notes                           = "ALREADY EXPIRED: notBefore 400 days ago, notAfter 35 days ago. Produced by `openssl ca -startdate -enddate`, because the Terraform tls provider always sets notBefore to now and ACM refuses to import an expired certificate. Present only when enable_openssl_generated_certificates = true."
    }

    "phase1-cert-025" = {
      certificate_id                  = "phase1-cert-025"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "LAB_CA_SIGNED_TLS_PROVIDER"
      domain                          = "s3-025.${local.int}"
      sans                            = ["s3-025.${local.int}", "bucket.s3-025.${local.int}", "objects.s3-025.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-4096"
      signed_by                       = "CERTIFICATE_TEST_LAB_LOCAL_CA"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 730
      attachment                      = "UNATTACHED"
      aws_service                     = "S3"
      storage_location                = "s3://<bucket>/certificates/phase1-cert-025/{cert.pem,cert.crt,chain.pem,fullchain.pem,cert.key}"
      expected_discovery_mechanism    = "S3_LIST_OBJECTS_AND_GET_OBJECT"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "OBJECT_STORE_CERTIFICATE_RSA4096"
      region                          = var.aws_region
      implemented                     = var.enable_s3
      duplicate_of                    = null
      notes                           = "RSA-4096, CA-signed, multi-SAN, stored as PEM + CRT + chain + fullchain + private key. Covers both the RSA-4096 key-size case and the 'certificate files sitting in a bucket' case, including a private key in object storage."
    }

    "phase1-cert-026" = {
      certificate_id                  = "phase1-cert-026"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "OPENSSL_LAB_CA_SIGNED"
      domain                          = "s3-026.${local.int}"
      sans                            = ["s3-026.${local.int}", "keystore026.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "CERTIFICATE_TEST_LAB_OPENSSL_CA"
      expiry_scenario                 = "WARNING"
      validity_days                   = 30
      attachment                      = "UNATTACHED"
      aws_service                     = "S3"
      storage_location                = "s3://<bucket>/certificates/phase1-cert-026/{cert.crt,test.p12,test.pfx}"
      expected_discovery_mechanism    = "S3_LIST_OBJECTS_AND_GET_OBJECT"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "OBJECT_STORE_PKCS12_KEYSTORE"
      region                          = var.aws_region
      implemented                     = local.openssl_enabled && var.enable_s3
      duplicate_of                    = null
      notes                           = "BINARY certificate container: PKCS#12, uploaded twice under both the .p12 and .pfx extensions. No Terraform resource can emit a PKCS#12 file, so it comes from the OpenSSL helper. The keystore password is var.pkcs12_password. Tests that a scanner handles binary keystores, not just PEM."
    }

    "phase1-cert-027" = {
      certificate_id                  = "phase1-cert-027"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "nginx027.${local.int}"
      sans                            = ["nginx027.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "CRITICAL"
      validity_days                   = 7
      attachment                      = "ATTACHED"
      aws_service                     = "EC2_FILESYSTEM_NGINX"
      storage_location                = "/etc/nginx/ssl/nginx-cert-lab.{pem,key} (+ fullchain)"
      expected_discovery_mechanism    = "SSM_RUN_COMMAND_FILESYSTEM_SCAN"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "FILESYSTEM_CERTIFICATE_IN_USE_CRITICAL"
      region                          = var.aws_region
      implemented                     = var.enable_ec2
      duplicate_of                    = null
      notes                           = "IN ACTIVE USE by nginx on port 443 - /etc/nginx/conf.d/certificate-lab.conf references it, so the certificate-to-service relationship is real and not just a file on disk. 7 days to expiry."
    }

    "phase1-cert-028" = {
      certificate_id                  = "phase1-cert-028"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "apache028.${local.int}"
      sans                            = ["apache028.${local.int}"]
      wildcard                        = false
      key_spec                        = "ECDSA-P384"
      signed_by                       = "SELF"
      expiry_scenario                 = "HEALTHY"
      validity_days                   = 365
      attachment                      = "ATTACHED"
      aws_service                     = "EC2_FILESYSTEM_APACHE"
      storage_location                = "/etc/httpd/conf/ssl/apache-cert-lab.{crt,key} and /etc/apache2/ssl/apache-cert-lab.{crt,key}"
      expected_discovery_mechanism    = "SSM_RUN_COMMAND_FILESYSTEM_SCAN"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "FILESYSTEM_CERTIFICATE_IN_USE_ECDSA"
      region                          = var.aws_region
      implemented                     = var.enable_ec2
      duplicate_of                    = null
      notes                           = "IN ACTIVE USE by Apache on port 8443 via /etc/httpd/conf.d/certificate-lab-ssl.conf. ECDSA P-384 and CRT/KEY file extensions. Deployed to BOTH the RHEL-style (/etc/httpd/conf/ssl) and Debian-style (/etc/apache2/ssl) paths so a path-based scanner is exercised on both conventions. Apache runs on 8443 because nginx already owns 443."
    }

    "phase1-cert-029" = {
      certificate_id                  = "phase1-cert-029"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "SELF_SIGNED_TLS_PROVIDER"
      domain                          = "springboot029.${local.int}"
      sans                            = ["springboot029.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "SELF"
      expiry_scenario                 = "WARNING"
      validity_days                   = 60
      attachment                      = "REFERENCED_BY_CONFIGURATION"
      aws_service                     = "EC2_FILESYSTEM_JAVA_SPRING_BOOT"
      storage_location                = "/opt/application/certs/{application.pem,application.key,keystore.p12,keystore.pfx,keystore.jks,truststore.jks}"
      expected_discovery_mechanism    = "SSM_RUN_COMMAND_FILESYSTEM_SCAN"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "JAVA_KEYSTORE_CERTIFICATE"
      region                          = var.aws_region
      implemented                     = var.enable_ec2
      duplicate_of                    = null
      notes                           = "The Java/Spring Boot shape: a PEM pair PLUS keystore.p12, keystore.pfx, keystore.jks and truststore.jks, referenced by /opt/application/config/application.properties (server.ssl.key-store=...). The JKS and P12 are built ON THE INSTANCE with keytool and openssl, so the machine running Terraform needs no Java. Keystore password is var.pkcs12_password. No Spring Boot application is installed - the objective is discovery, not running a service."
    }

    "phase1-cert-030" = {
      certificate_id                  = "phase1-cert-030"
      category                        = "CUSTOMER_MANAGED_NON_ACM"
      management_model                = "CUSTOMER_MANAGED"
      acm_presence                    = "NOT_IN_ACM"
      issuance_method                 = "OPENSSL_LAB_CA_SIGNED_BACKDATED"
      domain                          = "legacy030.${local.int}"
      sans                            = ["legacy030.${local.int}", "old.legacy030.${local.int}"]
      wildcard                        = false
      key_spec                        = "RSA-2048"
      signed_by                       = "CERTIFICATE_TEST_LAB_OPENSSL_CA"
      expiry_scenario                 = "EXPIRED"
      validity_days                   = -90
      attachment                      = "UNATTACHED"
      aws_service                     = "EC2_FILESYSTEM_LEGACY"
      storage_location                = "/etc/ssl/certs/legacy-cert-lab.crt, /etc/ssl/private/legacy-cert-lab.key, /opt/certificates/legacy-cert-lab.{pem,crt}"
      expected_discovery_mechanism    = "SSM_RUN_COMMAND_FILESYSTEM_SCAN"
      expected_renewal_type           = "MANUAL_EXTERNAL"
      expected_scanner_classification = "FILESYSTEM_CERTIFICATE_EXPIRED_ORPHANED"
      region                          = var.aws_region
      implemented                     = local.openssl_enabled && var.enable_s3 && var.enable_ec2
      duplicate_of                    = null
      notes                           = "The classic 'forgotten certificate': ALREADY EXPIRED (notAfter 90 days ago), sitting in the standard OS certificate directories, used by nothing. Back-dated by the OpenSSL helper. Reaches the instance via the S3 staging prefix, so it requires enable_s3 = true as well as enable_ec2 = true; if S3 is off, the instance generates its own material and this scenario is skipped."
    }
  }

  # The complete 30-entry matrix.
  certificate_scenarios_all = merge(
    local.certificate_scenarios,
    local.certificate_scenarios_imported,
    local.certificate_scenarios_non_acm,
  )
}

# ---------------------------------------------------------------------------
# Guard rail: the matrix must not silently change size.
# ---------------------------------------------------------------------------
check "certificate_matrix_is_complete" {
  assert {
    condition     = length(local.certificate_scenarios_all) == var.expected_certificate_scenario_count
    error_message = "The certificate test matrix has ${length(local.certificate_scenarios_all)} scenarios but expected_certificate_scenario_count is ${var.expected_certificate_scenario_count}. Either restore the missing scenario in locals.tf or change the expected count deliberately."
  }

  assert {
    condition     = length([for id, scenario in local.certificate_scenarios_all : id if scenario.management_model == "AWS_MANAGED"]) == 10
    error_message = "Exactly 10 scenarios must be AWS-managed / ACM-issued."
  }

  assert {
    condition     = length([for id, scenario in local.certificate_scenarios_all : id if scenario.management_model == "CUSTOMER_MANAGED"]) == 20
    error_message = "Exactly 20 scenarios must be customer-managed."
  }
}

# ===========================================================================
# DERIVED VALUES
# ===========================================================================

locals {
  # --- Per-scenario tags ---------------------------------------------------
  # AWS tag values only allow letters, numbers, spaces and + - = . _ : / @.
  # Wildcard domains contain '*', so the domain is deliberately NOT a tag; it
  # is exposed through the inventory output instead.
  certificate_tags = {
    for id, scenario in local.certificate_scenarios_all : id => {
      CertificateTestId       = scenario.certificate_id
      CertificateCategory     = scenario.category
      CertificateManagement   = scenario.management_model
      CertificateType         = scenario.key_spec
      CertificateScenario     = scenario.expected_scanner_classification
      CertificateWildcard     = tostring(scenario.wildcard)
      ExpiryScenario          = scenario.expiry_scenario
      ExpectedDiscoveryMethod = scenario.expected_discovery_mechanism
      ExpectedRenewalType     = scenario.expected_renewal_type
    }
  }

  certificate_domains = { for id, scenario in local.certificate_scenarios_all : id => scenario.domain }

  # --- Paths used by the OpenSSL helper -----------------------------------
  generated_certificates_dir = abspath("${path.root}/.generated-certificates")
  openssl_script_path        = abspath("${path.root}/scripts/generate-test-certificates.sh")

  # --- Certificate-to-listener wiring -------------------------------------
  #
  # The DEFAULT certificates are ACM imports, which are ISSUED the moment
  # they are created, so the ALB/NLB relationships always exist. The
  # ACM-ISSUED certificates are layered on as extra SNI certificates only
  # when they are actually attachable.
  alb_default_certificate_arn = try(module.certificates.acm_imported_certificate_arns["phase1-cert-016"], null)

  alb_sni_certificate_arns = compact(concat(
    [
      try(module.certificates.acm_imported_certificate_arns["phase1-cert-012"], null),
      try(module.certificates.acm_imported_certificate_arns["phase1-cert-013"], null),
      try(module.certificates.acm_imported_certificate_arns["phase1-cert-017"], null),
    ],
    local.acm_issued_are_attachable ? [
      try(module.certificates.acm_issued_certificate_arns["phase1-cert-001"], null),
      try(module.certificates.acm_issued_certificate_arns["phase1-cert-002"], null),
      try(module.certificates.acm_issued_certificate_arns["phase1-cert-003"], null),
      try(module.certificates.acm_issued_certificate_arns["phase1-cert-007"], null),
    ] : [],
  ))

  nlb_default_certificate_arn = try(module.certificates.acm_imported_certificate_arns["phase1-cert-011"], null)

  nlb_sni_certificate_arns = compact(
    local.acm_issued_are_attachable ? [
      try(module.certificates.acm_issued_certificate_arns["phase1-cert-006"], null),
    ] : [],
  )

  # --- API Gateway wiring -------------------------------------------------
  api_gateway_imported_certificate_arn = try(module.certificates.acm_imported_certificate_arns["phase1-cert-019"], null)
  api_gateway_imported_domain_name     = local.certificate_scenarios_all["phase1-cert-019"].domain

  api_gateway_acm_issued_certificate_arn = local.acm_issued_are_attachable ? try(module.certificates.acm_issued_certificate_arns["phase1-cert-004"], null) : null
  api_gateway_acm_issued_domain_name     = local.acm_issued_are_attachable ? local.certificate_scenarios_all["phase1-cert-004"].domain : null

  # --- Abbreviated load balancer naming -----------------------------------
  # AWS caps ELB and target group names at 32 characters. The full lab
  # prefix is 27 characters, which cannot accommodate "-nlb-tls-tg", so load
  # balancer resources get a short prefix while keeping the full tag set.
  load_balancer_name_prefix = var.load_balancer_name_prefix != "" ? var.load_balancer_name_prefix : "certlab-${var.environment_name}"

  # --- S3 bucket name -----------------------------------------------------
  s3_bucket_name = var.s3_bucket_name_override != "" ? var.s3_bucket_name_override : "${module.common.name_prefix}-certs-${random_id.bucket_suffix.hex}"

  # --- Expected-answer manifest written to the EC2 instance ---------------
  filesystem_scenario_ids = [
    "phase1-cert-027",
    "phase1-cert-028",
    "phase1-cert-029",
    "phase1-cert-030",
  ]

  filesystem_inventory_json = jsonencode({
    generatedBy = "terraform-certificate-test-lab/phases/phase-1"
    phase       = "phase-1"
    region      = var.aws_region
    warning     = "Throwaway TEST certificates only. Never trust, never reuse."
    certificates = [
      for id in local.filesystem_scenario_ids : {
        certificateId          = id
        domain                 = local.certificate_scenarios_all[id].domain
        sans                   = local.certificate_scenarios_all[id].sans
        keySpec                = local.certificate_scenarios_all[id].key_spec
        expiryScenario         = local.certificate_scenarios_all[id].expiry_scenario
        validityDays           = local.certificate_scenarios_all[id].validity_days
        awsService             = local.certificate_scenarios_all[id].aws_service
        storageLocation        = local.certificate_scenarios_all[id].storage_location
        attachment             = local.certificate_scenarios_all[id].attachment
        expectedRenewalType    = local.certificate_scenarios_all[id].expected_renewal_type
        expectedClassification = local.certificate_scenarios_all[id].expected_scanner_classification
        implemented            = local.certificate_scenarios_all[id].implemented
      }
    ]
  })
}

# ---------------------------------------------------------------------------
# Guard rail: catch flag combinations that would fail at apply time rather
# than letting AWS reject them halfway through.
# ---------------------------------------------------------------------------
check "phase_1_flags_are_coherent" {
  assert {
    condition     = !(var.attach_acm_issued_certificates && var.acm_issuance_mode == "public_dns_validation" && !var.wait_for_acm_dns_validation)
    error_message = "attach_acm_issued_certificates = true requires certificates that will actually reach ISSUED. Either set acm_issuance_mode = \"private_ca\" (with enable_private_ca = true), or delegate the test zone and set create_route53_test_zone = true together with wait_for_acm_dns_validation = true. A listener rejects a PENDING_VALIDATION certificate."
  }

  assert {
    condition     = !(var.enable_cloudfront && !var.enable_alb)
    error_message = "enable_cloudfront = true needs an origin, and the lab uses the ALB DNS name. Set enable_alb = true or leave CloudFront disabled."
  }

  assert {
    condition     = !((var.enable_alb || var.enable_nlb) && var.subnet_count < 2)
    error_message = "An ALB or NLB requires subnets in at least two Availability Zones. Set subnet_count >= 2."
  }
}
