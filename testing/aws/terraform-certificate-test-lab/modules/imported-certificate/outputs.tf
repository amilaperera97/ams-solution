output "name" {
  description = "Logical scenario name."
  value       = var.name
}

output "certificate_pem" {
  description = "PEM encoded leaf certificate."
  value       = local.certificate_pem
}

output "fullchain_pem" {
  description = "Leaf certificate followed by the lab CA certificate when CA-signed."
  value       = local.fullchain_pem
}

output "chain_pem" {
  description = "PEM encoded chain (lab CA certificate) or empty string for self-signed leaves."
  value       = local.chain_pem
}

output "private_key_pem" {
  description = "PEM encoded throwaway test private key."
  value       = tls_private_key.this.private_key_pem
  sensitive   = true
}

output "public_key_pem" {
  description = "PEM encoded public key."
  value       = tls_private_key.this.public_key_pem
}

output "is_ca_signed" {
  description = "True when the leaf was signed by the lab test CA."
  value       = local.ca_signed
}

output "not_before" {
  description = "Certificate notBefore in RFC3339."
  value       = local.validity_start_time
}

output "not_after" {
  description = "Certificate notAfter in RFC3339. Used by the inventory to assert the expiry scenario."
  value       = local.validity_end_time
}

output "acm_arn" {
  description = "ARN of the ACM import, or null when import_to_acm is false."
  value       = var.import_to_acm ? aws_acm_certificate.imported[0].arn : null
}

output "acm_id" {
  description = "ID of the ACM import, or null when import_to_acm is false."
  value       = var.import_to_acm ? aws_acm_certificate.imported[0].id : null
}

output "acm_status" {
  description = "ACM status of the import. Imports are ISSUED immediately."
  value       = var.import_to_acm ? aws_acm_certificate.imported[0].status : null
}

output "acm_renewal_eligibility" {
  description = "ACM renewal eligibility. Always INELIGIBLE for imported certificates - they require manual re-import."
  value       = var.import_to_acm ? aws_acm_certificate.imported[0].renewal_eligibility : null
}
