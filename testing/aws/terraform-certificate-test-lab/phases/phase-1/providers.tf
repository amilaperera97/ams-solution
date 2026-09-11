# ---------------------------------------------------------------------------
# Phase 1 uses a SINGLE provider in a SINGLE region.
#
# There is deliberately NO us-east-1 provider alias. That makes it
# structurally impossible for Phase 1 to create a resource outside
# var.aws_region, which is what keeps the "eu-west-1 only" guarantee real
# rather than aspirational.
#
# default_tags applies the common tag set to every taggable resource,
# including ones created inside child modules.
# ---------------------------------------------------------------------------

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = module.common.common_tags
  }
}
