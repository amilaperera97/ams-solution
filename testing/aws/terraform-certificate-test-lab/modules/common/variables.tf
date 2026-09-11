# ---------------------------------------------------------------------------
# modules/common - inputs
#
# TESTING NOTE: this module creates NO AWS resources. It exists purely to
# centralise the naming convention and the common tag set so that every
# resource in every phase of the certificate test lab is labelled
# identically. This is what makes the Java discovery application able to
# reliably filter "lab" resources away from anything else in the account.
# ---------------------------------------------------------------------------

variable "project_name" {
  description = "Project identifier used as the first component of every resource name and as the Project tag."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{2,40}$", var.project_name))
    error_message = "project_name must be lowercase alphanumeric/hyphen, 3-41 characters."
  }
}

variable "environment_name" {
  description = "Short environment/phase identifier used as the second component of every resource name (for example 'phase1')."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{1,20}$", var.environment_name))
    error_message = "environment_name must be lowercase alphanumeric/hyphen, 2-21 characters."
  }
}

variable "phase" {
  description = "Human readable phase label applied as the Phase tag (for example 'phase-1')."
  type        = string
}

variable "environment" {
  description = "Value for the Environment tag. This lab is always a disposable test environment."
  type        = string
  default     = "test"
}

variable "purpose" {
  description = "Value for the Purpose tag."
  type        = string
  default     = "certificate-discovery-testing"
}

variable "additional_tags" {
  description = "Extra tags merged into the common tag set for every resource."
  type        = map(string)
  default     = {}
}
