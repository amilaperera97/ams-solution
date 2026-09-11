# ---------------------------------------------------------------------------
# modules/common - the single source of truth for naming and tagging.
# ---------------------------------------------------------------------------

locals {
  # Resulting resources look like: certificate-test-lab-phase1-<suffix>
  name_prefix = "${var.project_name}-${var.environment_name}"

  common_tags = merge(
    {
      Project     = var.project_name
      Environment = var.environment
      Phase       = var.phase
      ManagedBy   = "terraform"
      Purpose     = var.purpose
    },
    var.additional_tags,
  )
}
