# ---------------------------------------------------------------------------
# Phase 1 is an INDEPENDENT Terraform root module.
#
#   cd phases/phase-1
#   terraform init
#   terraform plan
#
# Nothing in phases/phase-2, phase-3 or phase-4 is referenced, so applying
# Phase 1 can never provision a future phase.
# ---------------------------------------------------------------------------

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.60.0, < 7.0.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # OPTIONAL BUT RECOMMENDED
  # Terraform state for this lab contains THROWAWAY TEST private keys.
  # They are worthless, but treat the state file as a secret anyway and
  # prefer an encrypted remote backend:
  #
  # backend "s3" {
  #   bucket       = "my-terraform-state"
  #   key          = "certificate-test-lab/phase-1/terraform.tfstate"
  #   region       = "eu-west-1"
  #   encrypt      = true
  #   use_lockfile = true
  # }
}
