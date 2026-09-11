output "server_certificate_arn" {
  description = "ARN of the IAM server certificate, or null when disabled. Note the ARN has no region component - IAM is global."
  value       = local.create_iam_certificate ? aws_iam_server_certificate.legacy[0].arn : null
}

output "server_certificate_name" {
  description = "Generated IAM server certificate name."
  value       = local.create_iam_certificate ? aws_iam_server_certificate.legacy[0].name : null
}

output "server_certificate_id" {
  description = "IAM server certificate ID."
  value       = local.create_iam_certificate ? aws_iam_server_certificate.legacy[0].id : null
}

output "iam_path" {
  description = "IAM path the lab certificate is filed under."
  value       = var.iam_path
}

output "implemented" {
  description = "Whether the IAM server certificate scenario exists in this deployment."
  value       = local.create_iam_certificate
}
