output "secrets_manager_secret_arns" {
  description = "Secrets Manager secret ARNs keyed by certificate id."
  value       = { for id, secret in aws_secretsmanager_secret.certificate : id => secret.arn }
}

output "secrets_manager_secret_names" {
  description = "Secrets Manager secret names keyed by certificate id."
  value       = { for id, secret in aws_secretsmanager_secret.certificate : id => secret.name }
}

output "parameter_store_paths" {
  description = "Parameter Store parameter names keyed by certificate id."
  value = {
    for id, path in local.parameter_path : id => {
      certificate = "${path}/certificate"
      chain       = "${path}/chain"
      private_key = "${path}/private-key"
    }
  }
}

output "store_path_prefix" {
  description = "Prefix the discovery application should list for both stores."
  value       = var.store_path_prefix
}
