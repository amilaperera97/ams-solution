output "name_prefix" {
  description = "Prefix that every lab resource name must start with, e.g. certificate-test-lab-phase1."
  value       = local.name_prefix
}

output "common_tags" {
  description = "Common tag map applied to every resource in the lab."
  value       = local.common_tags
}
