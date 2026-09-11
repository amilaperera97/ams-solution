# CloudFront in Phase 1 — what is and is not possible

## The limitation, stated plainly

**A CloudFront distribution can only use an ACM certificate that lives in `us-east-1`.**
This is not a Terraform restriction; it is how CloudFront works. The certificate
is loaded onto the global edge fleet and CloudFront only reads ACM from the
`us-east-1` control plane.

Phase 1 of this lab is **`eu-west-1` only**. Therefore:

* Scenario `phase1-cert-005` — *"ACM certificate used by CloudFront"* — is
  **NOT created in Phase 1**. It is recorded in the test matrix as
  `DEFERRED_TO_PHASE_2`.
* No `us-east-1` provider alias exists anywhere in Phase 1, so it is
  impossible for Phase 1 to accidentally create a `us-east-1` resource.

Creating an `eu-west-1` ACM certificate and pointing a CloudFront
distribution at it would fail at apply time with
`InvalidViewerCertificate: The certificate that is attached to your
distribution was not issued in us-east-1`. That configuration is deliberately
absent rather than present-and-broken.

## What Phase 1 *does* offer instead

`cloudfront.tf` can optionally create a distribution that uses the
**default CloudFront certificate** (`*.cloudfront.net`). That is a genuine,
distinct discovery scenario:

| Aspect | Value |
| --- | --- |
| `ViewerCertificate.CloudFrontDefaultCertificate` | `true` |
| `ViewerCertificate.ACMCertificateArn` | *absent* |
| Aliases | *none* |
| Management model | Fully AWS-managed, invisible, no ARN to track |
| Renewal | AWS-internal, nothing for you to do |

Your discovery application must handle exactly this: a TLS-terminating AWS
service with **no certificate ARN at all**. It is a useful negative test case
and it needs no `us-east-1` anything.

It is **disabled by default** (`enable_cloudfront = false`) because:

* a distribution takes roughly 5–15 minutes to deploy and the same again to
  delete, which makes `terraform apply`/`destroy` cycles slow, and
* CloudFront is a global resource, and this lab's default posture is to keep
  every Phase 1 resource regional.

## Phase 2 design for `phase1-cert-005`

Phase 2 adds a second provider alias and issues the certificate where
CloudFront needs it:

```hcl
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}

module "cloudfront_certificate" {
  source    = "../../modules/acm-certificate"
  providers = { aws = aws.us_east_1 }

  name        = "phase2-cert-005"
  domain_name = "cdn005.${var.certificate_test_domain}"
}

resource "aws_cloudfront_distribution" "cdn" {
  aliases = ["cdn005.${var.certificate_test_domain}"]

  viewer_certificate {
    acm_certificate_arn      = module.cloudfront_certificate.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
  # ...
}
```

Note that this still requires the certificate to reach `ISSUED`, so Phase 2
also requires either a delegated public zone or AWS Private CA. CloudFront
does **not** accept a private-CA certificate for a custom alias, so a
delegated public domain is the only complete path. That dependency is
documented in `phases/phase-2/README.md`.
