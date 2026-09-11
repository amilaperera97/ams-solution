# ===========================================================================
# THE MACHINE-READABLE CERTIFICATE INVENTORY
#
# Joins the static test matrix (locals.tf) with the facts that only exist
# after apply: ARNs, resource IDs, real notAfter values and the concrete
# store locations.
#
# Consume it with:
#     terraform output -json certificate_inventory
# or read the file written next to the root module:
#     phases/phase-1/certificate-inventory.json
#
# This is the file to diff against whatever your Spring Boot discovery
# service reports. Every "expected*" field is an assertion.
# ===========================================================================

locals {
  # --- Where each certificate's ARN comes from ----------------------------
  runtime_arn = {
    for id, scenario in local.certificate_scenarios_all : id => (
      scenario.acm_presence == "ACM_ISSUED" ? try(module.certificates.acm_issued_certificate_arns[id], null) :
      scenario.acm_presence == "ACM_IMPORTED" ? try(module.certificates.acm_imported_certificate_arns[id], null) :
      id == "phase1-cert-018" ? module.iam_certificates.server_certificate_arn :
      null
    )
  }

  # --- Real expiry, as reported by the provider ---------------------------
  runtime_not_after = {
    for id, scenario in local.certificate_scenarios_all : id => (
      scenario.acm_presence == "ACM_ISSUED" ? try(module.certificates.acm_issued_certificates[id].not_after, null) :
      try(module.certificates.certificate_expiry[id], null)
    )
  }

  # --- Concrete store location per certificate ----------------------------
  runtime_store_reference = merge(
    # Secrets Manager
    {
      for id in local.secrets_manager_certificate_ids : id => {
        store     = "SECRETS_MANAGER"
        reference = try(module.secrets.secrets_manager_secret_names[id], null)
        arn       = try(module.secrets.secrets_manager_secret_arns[id], null)
      } if var.enable_secrets_manager
    },
    # Parameter Store
    {
      for id in concat(local.parameter_store_certificate_ids, local.parameter_store_openssl_certificate_ids) : id => {
        store     = "SSM_PARAMETER_STORE"
        reference = try(join(",", values(module.secrets.parameter_store_paths[id])), null)
        arn       = null
      } if var.enable_parameter_store
    },
    # S3
    {
      for id in concat(local.s3_store_certificate_ids, local.s3_store_openssl_certificate_ids) : id => {
        store     = "S3"
        reference = try(join(",", one(module.certificate_store[*].certificate_object_keys)[id]), null)
        arn       = one(module.certificate_store[*].bucket_arn)
      } if var.enable_s3
    },
    # EC2 filesystem
    {
      for id in local.filesystem_scenario_ids : id => {
        store     = "EC2_FILESYSTEM"
        reference = try(join(",", module.compute.filesystem_certificate_paths[id]), null)
        arn       = module.compute.instance_id
      } if var.enable_ec2
    },
    # IAM
    {
      "phase1-cert-018" = {
        store     = "IAM_SERVER_CERTIFICATE"
        reference = module.iam_certificates.server_certificate_name
        arn       = module.iam_certificates.server_certificate_arn
      }
    },
  )

  # --- The inventory itself -----------------------------------------------
  certificate_inventory = {
    for id, scenario in local.certificate_scenarios_all : id => {
      # identity
      certificateId = scenario.certificate_id
      category      = scenario.category
      management    = scenario.management_model
      acmPresence   = scenario.acm_presence
      issuanceType  = scenario.issuance_method
      signedBy      = scenario.signed_by
      duplicateOf   = scenario.duplicate_of

      # naming
      domain   = scenario.domain
      sans     = scenario.sans
      wildcard = scenario.wildcard
      multiSan = length(scenario.sans) > 1

      # crypto
      certificateType = scenario.key_spec

      # lifecycle
      expiryScenario      = scenario.expiry_scenario
      plannedValidityDays = scenario.validity_days
      actualNotAfter      = lookup(local.runtime_not_after, id, null)
      expectedRenewalType = scenario.expected_renewal_type
      renewalEligibility = (
        scenario.acm_presence == "ACM_IMPORTED" ? "INELIGIBLE" :
        scenario.acm_presence == "ACM_ISSUED" ? (scenario.attachment == "ATTACHED" ? "ELIGIBLE" : "INELIGIBLE_WHILE_UNUSED") :
        "NOT_APPLICABLE"
      )

      # location
      awsAccountId   = data.aws_caller_identity.current.account_id
      awsRegion      = scenario.region
      certificateArn = lookup(local.runtime_arn, id, null)
      resourceId     = lookup(local.runtime_arn, id, null)

      # relationships
      attachment      = scenario.attachment
      awsService      = scenario.aws_service
      storageLocation = scenario.storage_location
      storeReference  = lookup(local.runtime_store_reference, id, null)

      # assertions for the scanner
      expectedDiscoveryMechanism    = scenario.expected_discovery_mechanism
      expectedScannerClassification = scenario.expected_scanner_classification
      expectedAcmStatus = (
        scenario.acm_presence == "ACM_ISSUED" ? local.acm_issued_expected_status :
        scenario.acm_presence == "ACM_IMPORTED" ? "ISSUED" :
        "NOT_IN_ACM"
      )

      # deployment reality
      implemented = scenario.implemented
      notes       = scenario.notes
    }
  }

  # --- Summary counters ---------------------------------------------------
  inventory_summary = {
    totalScenarios  = length(local.certificate_inventory)
    implemented     = length([for id, entry in local.certificate_inventory : id if entry.implemented])
    notImplemented  = length([for id, entry in local.certificate_inventory : id if !entry.implemented])
    awsManaged      = length([for id, entry in local.certificate_inventory : id if entry.management == "AWS_MANAGED"])
    customerManaged = length([for id, entry in local.certificate_inventory : id if entry.management == "CUSTOMER_MANAGED"])
    acmIssued       = length([for id, entry in local.certificate_inventory : id if entry.acmPresence == "ACM_ISSUED"])
    acmImported     = length([for id, entry in local.certificate_inventory : id if entry.acmPresence == "ACM_IMPORTED"])
    outsideAcm      = length([for id, entry in local.certificate_inventory : id if entry.acmPresence == "NOT_IN_ACM"])
    attached        = length([for id, entry in local.certificate_inventory : id if entry.attachment == "ATTACHED"])
    unattached      = length([for id, entry in local.certificate_inventory : id if entry.attachment == "UNATTACHED"])
    wildcard        = length([for id, entry in local.certificate_inventory : id if entry.wildcard])
    multiSan        = length([for id, entry in local.certificate_inventory : id if entry.multiSan])
    duplicates      = length([for id, entry in local.certificate_inventory : id if entry.duplicateOf != null])
    byExpiryScenario = {
      for scenario in distinct([for id, entry in local.certificate_inventory : entry.expiryScenario]) :
      scenario => length([for id, entry in local.certificate_inventory : id if entry.expiryScenario == scenario])
    }
  }
}

# ---------------------------------------------------------------------------
# Write the inventory to disk so a test harness can pick it up without
# shelling out to Terraform. Gitignored.
# ---------------------------------------------------------------------------
resource "local_file" "certificate_inventory" {
  count = var.enable_inventory_file ? 1 : 0

  filename        = "${path.root}/certificate-inventory.json"
  file_permission = "0644"

  content = jsonencode({
    phase       = "phase-1"
    project     = var.project_name
    environment = var.environment_name
    region      = var.aws_region
    accountId   = data.aws_caller_identity.current.account_id
    generatedBy = "terraform-certificate-test-lab"
    warning     = "Every certificate referenced here is throwaway TEST material."

    configuration = {
      acmIssuanceMode             = var.acm_issuance_mode
      acmIssuedAreAttachable      = local.acm_issued_are_attachable
      acmIssuedExpectedStatus     = local.acm_issued_expected_status
      privateCaEnabled            = var.enable_private_ca
      route53TestZoneCreated      = var.create_route53_test_zone
      opensslScenariosEnabled     = var.enable_openssl_generated_certificates
      albEnabled                  = var.enable_alb
      nlbEnabled                  = var.enable_nlb
      apiGatewayEnabled           = var.enable_api_gateway
      cloudfrontEnabled           = var.enable_cloudfront
      ec2Enabled                  = var.enable_ec2
      s3Enabled                   = var.enable_s3
      secretsManagerEnabled       = var.enable_secrets_manager
      parameterStoreEnabled       = var.enable_parameter_store
      iamServerCertificateEnabled = var.enable_iam_server_certificate
      sshFallbackEnabled          = var.enable_ssh_fallback
    }

    summary      = local.inventory_summary
    certificates = values(local.certificate_inventory)

    serviceRelationships = {
      loadBalancerListeners = module.load_balancers.listener_certificate_relationships
      apiGatewayDomains     = module.api_gateway.custom_domains
      cloudfront = {
        distributionId         = module.cloudfront.distribution_id
        usesDefaultCertificate = module.cloudfront.uses_default_certificate
        phase1Limitation       = module.cloudfront.phase_1_limitation
      }
    }

    scanTargets = {
      ssmInstanceId           = module.compute.instance_id
      ssmSendCommandExample   = module.compute.certificate_scan_command
      filesystemScannerScript = "/usr/local/bin/certificate-lab-scan"
      s3Bucket                = one(module.certificate_store[*].bucket_name)
      storePathPrefix         = var.certificate_store_path_prefix
      iamPath                 = module.iam_certificates.iam_path
    }
  })
}
