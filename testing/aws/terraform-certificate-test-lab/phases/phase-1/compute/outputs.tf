output "instance_id" {
  description = "EC2 instance ID. Pass this as the ssm:SendCommand target."
  value       = var.enable_ec2 ? module.certificate_server[0].instance_id : null
}

output "instance_ids" {
  description = "List form of the instance ID, convenient for target group registration."
  value       = var.enable_ec2 ? [module.certificate_server[0].instance_id] : []
}

output "instance_private_ip" {
  description = "Private IPv4 address."
  value       = var.enable_ec2 ? module.certificate_server[0].private_ip : null
}

output "instance_public_ip" {
  description = "Public IPv4 address, when assigned."
  value       = var.enable_ec2 ? module.certificate_server[0].public_ip : null
}

output "instance_role_arn" {
  description = "ARN of the instance role."
  value       = var.enable_ec2 ? aws_iam_role.certificate_server[0].arn : null
}

output "instance_profile_name" {
  description = "Instance profile name."
  value       = var.enable_ec2 ? aws_iam_instance_profile.certificate_server[0].name : null
}

output "ami_id" {
  description = "AMI the certificate server was launched from."
  value       = data.aws_ami.al2023.id
}

output "ssh_key_name" {
  description = "EC2 key pair name when the SSH fallback path is enabled, otherwise null."
  value       = length(aws_key_pair.certificate_server) > 0 ? aws_key_pair.certificate_server[0].key_name : null
}

output "certificate_scan_command" {
  description = "Ready-to-use AWS CLI equivalent of the ssm:SendCommand your Spring Boot backend will issue."
  value = var.enable_ec2 ? join(" ", [
    "aws ssm send-command",
    "--region ${var.aws_region}",
    "--document-name AWS-RunShellScript",
    "--instance-ids ${module.certificate_server[0].instance_id}",
    "--parameters 'commands=[\"/usr/local/bin/certificate-lab-scan\"]'",
  ]) : null
}

output "filesystem_certificate_paths" {
  description = "Exact on-disk paths the scanner should find, per certificate scenario."
  value = {
    (var.nginx_certificate_id) = [
      "/etc/nginx/ssl/nginx-cert-lab.pem",
      "/etc/nginx/ssl/nginx-cert-lab.key",
      "/etc/nginx/ssl/nginx-cert-lab-fullchain.pem",
    ]
    (var.apache_certificate_id) = [
      "/etc/httpd/conf/ssl/apache-cert-lab.crt",
      "/etc/httpd/conf/ssl/apache-cert-lab.key",
      "/etc/apache2/ssl/apache-cert-lab.crt",
      "/etc/apache2/ssl/apache-cert-lab.key",
    ]
    (var.java_certificate_id) = [
      "/opt/application/certs/application.pem",
      "/opt/application/certs/application.key",
      "/opt/application/certs/keystore.p12",
      "/opt/application/certs/keystore.pfx",
      "/opt/application/certs/keystore.jks",
      "/opt/application/certs/truststore.jks",
      "/opt/application/config/application.properties",
    ]
    (coalesce(var.expired_certificate_id, "not-deployed")) = var.expired_certificate_id == null ? [] : [
      "/etc/ssl/certs/legacy-cert-lab.crt",
      "/etc/ssl/private/legacy-cert-lab.key",
      "/opt/certificates/legacy-cert-lab.pem",
      "/opt/certificates/legacy-cert-lab.crt",
    ]
  }
}
