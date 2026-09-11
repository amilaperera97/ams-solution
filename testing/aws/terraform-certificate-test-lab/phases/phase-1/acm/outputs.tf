# ---------------------------------------------------------------------------
# Outputs consumed by the other Phase 1 category modules.
# Anything containing key material is marked sensitive.
# ---------------------------------------------------------------------------

output "acm_issued_certificates" {
  description = "ACM-issued certificate facts, keyed by certificate id."
  value = {
    for id, certificate in module.acm_issued : id => {
      arn                 = certificate.arn
      domain_name         = certificate.domain_name
      sans                = certificate.subject_alternative_names
      status              = certificate.status
      type                = certificate.type
      not_after           = certificate.not_after
      renewal_eligibility = certificate.renewal_eligibility
    }
  }
}

output "acm_issued_certificate_arns" {
  description = "Map of certificate id to ACM ARN for the ACM-issued scenarios."
  value       = { for id, certificate in module.acm_issued : id => certificate.arn }
}

output "acm_issued_validation_records" {
  description = "DNS validation records required to move each public certificate to ISSUED. Empty when acm_issuance_mode = private_ca."
  value = {
    for id, certificate in module.acm_issued : id => [
      for option in certificate.domain_validation_options : {
        domain_name = option.domain_name
        record_name = option.resource_record_name
        record_type = option.resource_record_type
        record_data = option.resource_record_value
      }
    ]
  }
}

output "acm_imported_certificates" {
  description = "ACM-imported certificate facts, keyed by certificate id."
  value = {
    for id, certificate in module.acm_imported : id => {
      arn                 = certificate.acm_arn
      status              = certificate.acm_status
      not_before          = certificate.not_before
      not_after           = certificate.not_after
      renewal_eligibility = certificate.acm_renewal_eligibility
      ca_signed           = certificate.is_ca_signed
    }
  }
}

output "acm_imported_certificate_arns" {
  description = "Map of certificate id to ACM ARN for the imported scenarios."
  value       = { for id, certificate in module.acm_imported : id => certificate.acm_arn }
}

output "certificate_expiry" {
  description = "notAfter for every locally generated certificate (ACM-imported and non-ACM), keyed by certificate id."
  value = merge(
    { for id, certificate in module.acm_imported : id => certificate.not_after },
    { for id, certificate in module.non_acm_certificates : id => certificate.not_after },
  )
}

output "pem_material" {
  description = "Generated PEM material for the non-ACM stores, keyed by certificate id. SENSITIVE - contains throwaway private keys."
  value = {
    for id, certificate in module.non_acm_certificates : id => {
      certificate_pem = certificate.certificate_pem
      fullchain_pem   = certificate.fullchain_pem
      chain_pem       = certificate.chain_pem
      private_key_pem = certificate.private_key_pem
      not_after       = certificate.not_after
      ca_signed       = certificate.is_ca_signed
    }
  }
  sensitive = true
}

output "openssl_material" {
  description = "OpenSSL-generated artefacts (EXPIRED certificates and the PKCS#12 keystore), keyed by certificate id. SENSITIVE."
  value = {
    for id in local.openssl_certificate_ids : id => {
      certificate_pem = data.local_file.openssl_certificate[id].content
      private_key_pem = data.local_sensitive_file.openssl_private_key[id].content
      chain_pem       = one(data.local_file.openssl_ca_certificate[*].content)
    }
  }
  sensitive = true
}

output "openssl_pkcs12_base64" {
  description = "Base64 of the PKCS#12 keystore for phase1-cert-026, or null when the OpenSSL helper is disabled. SENSITIVE."
  value       = one(data.local_sensitive_file.openssl_pkcs12[*].content_base64)
  sensitive   = true
}

output "openssl_enabled" {
  description = "Whether the OpenSSL-generated scenarios (024, 026, 030) exist in this deployment."
  value       = var.enable_openssl_generated_certificates
}

output "lab_ca_certificate_pem" {
  description = "PEM of the LOCAL test CA that signed the CA-signed scenarios. Public material, safe to output."
  value       = tls_self_signed_cert.lab_ca.cert_pem
}

output "private_ca_arn" {
  description = "ARN of the AWS Private CA root, or null when disabled."
  value       = var.enable_private_ca ? aws_acmpca_certificate_authority.root[0].arn : null
}

output "route53_test_zone_id" {
  description = "Zone ID of the dedicated test hosted zone, or null when not created."
  value       = local.manage_test_zone ? aws_route53_zone.test[0].zone_id : null
}

output "route53_test_zone_name_servers" {
  description = "Name servers you must delegate to at your registrar for ACM DNS validation to succeed."
  value       = local.manage_test_zone ? aws_route53_zone.test[0].name_servers : []
}
