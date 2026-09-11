resource "aws_instance" "this" {
  ami                         = var.ami_id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = var.security_group_ids
  iam_instance_profile        = var.iam_instance_profile
  key_name                    = var.key_name
  associate_public_ip_address = var.associate_public_ip_address
  user_data                   = var.user_data

  # Re-run the bootstrap when the certificate layout changes.
  user_data_replace_on_change = true

  # Security baseline: IMDSv2 only, no instance metadata tags exposure needed.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true

    tags = merge(var.tags, { Name = "${var.name}-root" })
  }

  tags = merge(var.tags, { Name = var.name })
}
