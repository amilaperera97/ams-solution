output "bucket_name" {
  description = "Name of the lab certificate bucket."
  value       = aws_s3_bucket.certificates.id
}

output "bucket_arn" {
  description = "ARN of the lab certificate bucket."
  value       = aws_s3_bucket.certificates.arn
}

output "certificate_prefix" {
  description = "Prefix holding the S3 certificate-store discovery targets."
  value       = var.certificate_prefix
}

output "ec2_staging_prefix" {
  description = "Prefix holding EC2 bootstrap material."
  value       = var.ec2_staging_prefix
}

output "certificate_object_keys" {
  description = "Every S3 key that contains certificate material, grouped by certificate id."
  value = {
    for id in setunion(local.store_ids, local.store_openssl_ids) : id => compact(concat(
      contains(local.store_ids, id) ? [
        "${var.certificate_prefix}/${id}/cert.pem",
        "${var.certificate_prefix}/${id}/cert.crt",
        "${var.certificate_prefix}/${id}/fullchain.pem",
        "${var.certificate_prefix}/${id}/cert.key",
      ] : [],
      contains(local.store_ids, id) && contains(local.chain_ids, id) ? ["${var.certificate_prefix}/${id}/chain.pem"] : [],
      contains(local.store_openssl_ids, id) ? ["${var.certificate_prefix}/${id}/cert.crt"] : [],
      var.pkcs12_certificate_id == id ? [
        "${var.certificate_prefix}/${id}/test.p12",
        "${var.certificate_prefix}/${id}/test.pfx",
      ] : [],
    ))
  }
}
