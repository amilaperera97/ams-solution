# Phase 1 — Certificate Discovery Test Lab (eu-west-1)

A disposable AWS environment containing **30 deliberately different
certificate scenarios**, built so a Java/Spring Boot certificate discovery,
renewal and management service can be tested against a *known-good answer*.

The point is not "30 certificates". The point is a **test matrix**: every
scenario differs in at least one dimension that a discovery engine has to get
right — management model, issuance method, key algorithm, wildcard vs SAN,
expiry window, attachment state, storage location, renewal path.

> **Everything here is throwaway test material.** No real certificate and no
> real private key is ever used. Nothing pre-existing in your account is read,
> imported or modified.

---

## Table of contents

1. [Architecture](#1-architecture)
2. [Directory structure](#2-directory-structure)
3. [Phase structure](#3-phase-structure)
4. [Phase 1 scope](#4-phase-1-scope)
5. [All 30 certificates](#5-all-30-certificates)
6. [Certificate test matrix](#6-certificate-test-matrix)
7. [AWS services used](#7-aws-services-used)
8. [Certificate locations](#8-certificate-locations)
9. [Expected discovery mechanism](#9-expected-discovery-mechanism)
10. [Expected renewal mechanism](#10-expected-renewal-mechanism)
11. [SSM setup](#11-ssm-setup)
12. [SSH fallback](#12-ssh-fallback)
13. [How to initialise Terraform](#13-how-to-initialise-terraform)
14. [How to validate](#14-how-to-validate)
15. [How to plan](#15-how-to-plan)
16. [How to apply Phase 1](#16-how-to-apply-phase-1)
17. [How to destroy Phase 1](#17-how-to-destroy-phase-1)
18. [Expected AWS costs](#18-expected-aws-costs)
19. [Security considerations](#19-security-considerations)
20. [Known AWS limitations](#20-known-aws-limitations)
21. [Which certificates are ACM-issued](#21-which-certificates-are-acm-issued)
22. [Which are imported](#22-which-are-imported)
23. [Which are filesystem certificates](#23-which-are-filesystem-certificates)
24. [Which are in Secrets Manager](#24-which-are-in-secrets-manager)
25. [Which are in Parameter Store](#25-which-are-in-parameter-store)
26. [Which are in S3](#26-which-are-in-s3)
27. [Which are attached to ALB / NLB / API Gateway](#27-which-are-attached-to-alb--nlb--api-gateway)
28. [Which are intentionally unused](#28-which-are-intentionally-unused)
29. [Which are expired or near expiry](#29-which-are-expired-or-near-expiry)
30. [Future Phase 2 design](#30-future-phase-2-design)

Plus: [ACM validation without a public DNS zone](#acm-validation-without-a-public-dns-zone) — read this before your first apply.

---

## 1. Architecture

```
                        ┌──────────────────────────────────────────┐
                        │ AWS Certificate Manager (eu-west-1)      │
                        │                                          │
   ACM-ISSUED  ────────▶│  001 002 003 004 006 007 008 009 010     │
   (AWS-managed)        │  (005 = CloudFront, us-east-1 only,      │
                        │         NOT created — Phase 2)           │
                        │                                          │
   ACM-IMPORTED ───────▶│  011 012 013 014 015 016 017 019 020     │
   (customer-managed)   └───────────────┬──────────────────────────┘
                                        │
        ┌───────────────────────────────┼──────────────────────────┐
        │                               │                          │
        ▼                               ▼                          ▼
┌──────────────────┐         ┌──────────────────┐      ┌────────────────────┐
│ ALB              │         │ NLB              │      │ API Gateway        │
│ :80  HTTP  (none)│         │ :80  TCP  (none) │      │ REST, REGIONAL     │
│ :443 HTTPS       │         │ :443 TLS         │      │ custom domain      │
│   default → 016  │         │   default → 011  │      │   → 019            │
│   SNI → 012 013  │         │   SNI → 006*     │      │   → 004*           │
│          017     │         └──────────────────┘      └────────────────────┘
│   SNI → 001 002* │
│          003 007*│         * only when
└────────┬─────────┘           attach_acm_issued_certificates = true
         │
         ▼ target
┌────────────────────────────────────────────────────────────────┐
│ EC2 t3.micro "certificate server"  (SSM-managed, no inbound)   │
│                                                                │
│  /etc/nginx/ssl            027  nginx :443     CRITICAL  7d    │
│  /etc/httpd/conf/ssl       028  Apache :8443   HEALTHY  ECDSA  │
│  /etc/apache2/ssl          028  (Debian-style duplicate path)  │
│  /opt/application/certs    029  P12 + JKS + truststore  60d    │
│  /etc/ssl/certs|private    030  EXPIRED, used by nothing       │
│  /opt/certificates         030  + expected-answer manifest     │
└────────────────────────────────────────────────────────────────┘

┌──────────────────┐ ┌──────────────────┐ ┌──────────────┐ ┌─────────────┐
│ Secrets Manager  │ │ Parameter Store  │ │ S3 (private) │ │ IAM (global)│
│  021  WARNING 30d│ │  023  WARNING 90d│ │  025 RSA-4096│ │  018 legacy │
│  022  CRITICAL 1d│ │  024  EXPIRED    │ │  026 PKCS#12 │ │      store  │
└──────────────────┘ └──────────────────┘ └──────────────┘ └─────────────┘
```

Everything lives in a **brand-new, dedicated VPC**. There is no NAT gateway:
the certificate server sits in a public subnet with a public IP and reaches
the SSM endpoints through the internet gateway, which is free. It accepts **no
inbound traffic from the internet**.

## 2. Directory structure

```
phases/phase-1/
├── README.md                       this document
├── certificate-test-matrix.yaml    the 30 scenarios, machine-readable
├── terraform.tfvars.example
├── versions.tf                     provider constraints, backend template
├── providers.tf                    ONE provider, ONE region, no aliases
├── variables.tf                    every switch, typed and validated
├── locals.tf                       THE TEST MATRIX (source of truth)
├── inventory.tf                    matrix ⨯ runtime facts → inventory
├── main.tf                         module wiring
├── outputs.tf
│
├── acm/                            all certificate MATERIAL
│   ├── aws_managed_certificates.tf   001–010  ACM-issued
│   ├── imported_certificates.tf      011–020  ACM imports
│   ├── custom_certificates.tf        lab test CA + non-ACM material
│   ├── private_ca.tf                 optional AWS Private CA
│   ├── validation.tf                 optional dedicated Route 53 zone
│   └── openssl_generated.tf          EXPIRED + PKCS#12 artefacts
│
├── networking/    vpc.tf  subnets.tf  security_groups.tf
├── compute/       ec2.tf  iam.tf  user_data/certificate-server.sh.tftpl
├── load-balancers/ alb.tf  nlb.tf  listeners.tf  locals.tf
├── api-gateway/   api_gateway.tf
├── cloudfront/    cloudfront.tf  README.md   (limitation documented)
├── secrets/       secrets_manager.tf  parameter_store.tf
├── s3/            s3.tf  objects.tf
├── iam/           iam_certificates.tf
└── scripts/       generate-test-certificates.sh  README.md
```

Each subdirectory is a **child module** called from `main.tf`. Terraform only
loads `.tf` files from the root directory, never recursively, so the
`PHASE → CATEGORY → RESOURCES` layout is implemented with module boundaries
rather than bare files in subfolders.

Shared, reusable modules live one level up in `../../modules/`:
`common`, `acm-certificate`, `imported-certificate`, `ec2-certificate-server`,
`alb`.

## 3. Phase structure

| Phase | Scope | Status |
| --- | --- | --- |
| **1** | eu-west-1 certificate discovery and service attachment | **Implemented** |
| 2 | Additional AWS regions, including the us-east-1 / CloudFront scenarios | Placeholder (`phases/phase-2/README.md`) |
| 3 | ECS / EKS / container / Kubernetes certificate scenarios | Placeholder |
| 4 | Advanced edge cases, hybrid / on-prem style scenarios, extra stores | Placeholder |

Phase 1 is a **self-contained Terraform root module**. It references nothing
in `phases/phase-2`, `phase-3` or `phase-4`, so applying Phase 1 can never
provision a future phase. Each future phase gets its own root module and its
own state.

## 4. Phase 1 scope

**In scope**

* 30 certificate scenarios in `eu-west-1` (plus one global IAM certificate).
* ACM-issued and ACM-imported certificates.
* Certificates outside ACM: Secrets Manager, Parameter Store, S3, EC2
  filesystem, IAM server certificate store.
* Certificate-to-service relationships: ALB, NLB, API Gateway.
* Filesystem scanning over SSM Run Command, with SSH as an optional fallback.
* A machine-readable inventory that acts as the expected answer.

**Deliberately out of scope**

* Any resource outside `var.aws_region`. There is no second provider alias, so
  this is structurally guaranteed, not merely intended.
* The ACM-backed CloudFront scenario (needs us-east-1 → Phase 2).
* Containers (Phase 3).
* Any Java/Spring Boot application code. This repository builds the
  environment only.

## 5. All 30 certificates

`CRITICAL` = ≤7 days · `WARNING` = ≤90 days · `HEALTHY` = >90 days

### AWS-managed / ACM-issued (10)

| ID | Domain | Type | Expiry | Attached to | Notes |
| --- | --- | --- | --- | --- | --- |
| 001 | `cert001.<pub>` | RSA-2048 | HEALTHY | ALB HTTPS (SNI)\* | plain single domain |
| 002 | `*.app002.<pub>` | RSA-2048 | HEALTHY | ALB HTTPS (SNI)\* | **wildcard** + apex SAN |
| 003 | `cert003.<pub>` | RSA-2048 | HEALTHY | ALB HTTPS (SNI)\* | **4 SANs** |
| 004 | `api004.<pub>` | RSA-2048 | HEALTHY | API Gateway\* | REGIONAL custom domain |
| 005 | `cdn005.<pub>` | RSA-2048 | n/a | — | **NOT CREATED** — CloudFront needs us-east-1 |
| 006 | `nlb006.<pub>` | RSA-2048 | HEALTHY | NLB TLS (SNI)\* | |
| 007 | `sni007.<pub>` | RSA-2048 | HEALTHY | ALB HTTPS (SNI)\* | third cert on the same listener |
| 008 | `unused008.<pub>` | RSA-2048 | HEALTHY | **nothing** | unused → renewal INELIGIBLE |
| 009 | `ecdsa009.<pub>` | **ECDSA P-256** | HEALTHY | **nothing** | ECDSA + 3 SANs |
| 010 | `*.svc010.<pub>` | RSA-2048 | HEALTHY | **nothing** | **wildcard**, unused |

\* attached only when `attach_acm_issued_certificates = true`. See
[ACM validation without a public DNS zone](#acm-validation-without-a-public-dns-zone).

### Customer-managed, imported into ACM (9) + IAM (1)

| ID | Domain | Type | Signed by | Expiry | Attached to |
| --- | --- | --- | --- | --- | --- |
| 011 | `nlb011.<int>` | RSA-2048 | self | HEALTHY 365d | **NLB TLS default** |
| 012 | `*.alb012.<int>` | RSA-2048 | self | HEALTHY 365d | **ALB HTTPS (SNI)** |
| 013 | `multi013.<int>` +3 SANs | RSA-2048 | self | **WARNING 60d** | **ALB HTTPS (SNI)** |
| 014 | `expiring014.<int>` | RSA-2048 | self | **CRITICAL 7d** | nothing |
| 015 | `longlived015.<int>` | RSA-2048 | lab CA | HEALTHY **1095d** | nothing |
| 016 | `alb016.<int>` | RSA-2048 | self | HEALTHY 730d | **ALB HTTPS default** |
| 017 | `ecdsa017.<int>` | **ECDSA P-256** | lab CA | HEALTHY 365d | **ALB HTTPS (SNI)** |
| 018 | `iam018.<int>` | RSA-2048 | lab CA | HEALTHY 365d | nothing — **IAM store, global** |
| 019 | `api019.<pub>` | RSA-2048 | self | HEALTHY 365d | **API Gateway custom domain** |
| 020 | `alb016.<int>` | RSA-2048 | self | HEALTHY 730d | nothing — **duplicate of 016** |

### Customer-managed, outside ACM (10)

| ID | Domain | Type | Expiry | Store | Used by |
| --- | --- | --- | --- | --- | --- |
| 021 | `sm021.<int>` | RSA-2048 | **WARNING 30d** | Secrets Manager | — |
| 022 | `*.sm022.<int>` | RSA-2048 | **CRITICAL 1d** | Secrets Manager | — |
| 023 | `ps023.<int>` | RSA-2048 | **WARNING 90d** | Parameter Store | — |
| 024 | `ps024-expired.<int>` | RSA-2048 | **EXPIRED** | Parameter Store | — |
| 025 | `s3-025.<int>` +2 SANs | **RSA-4096** | HEALTHY 730d | S3 (PEM/CRT/chain/key) | — |
| 026 | `s3-026.<int>` | RSA-2048 | **WARNING 30d** | S3 (**PKCS#12 / PFX**) | — |
| 027 | `nginx027.<int>` | RSA-2048 | **CRITICAL 7d** | EC2 `/etc/nginx/ssl` | **nginx :443** |
| 028 | `apache028.<int>` | **ECDSA P-384** | HEALTHY 365d | EC2 Apache paths | **httpd :8443** |
| 029 | `springboot029.<int>` | RSA-2048 | **WARNING 60d** | EC2 `/opt/application/certs` | **JKS + P12**, `application.properties` |
| 030 | `legacy030.<int>` | RSA-2048 | **EXPIRED** | EC2 `/etc/ssl`, `/opt/certificates` | nothing — orphan |

`<pub>` = `var.certificate_test_domain` (default `example.com`) ·
`<int>` = `var.certificate_internal_domain` (default `cert-lab.internal`)

## 6. Certificate test matrix

Three representations, all consistent with each other:

| Artefact | Use |
| --- | --- |
| `locals.tf` → `local.certificate_scenarios_all` | Terraform's source of truth |
| `certificate-test-matrix.yaml` | human/CI readable, no Terraform needed |
| `terraform output -json certificate_inventory` | matrix **+ real ARNs, IDs and notAfter values** |

A `check` block asserts the matrix holds exactly 30 scenarios, 10 AWS-managed
and 20 customer-managed, so an accidental edit fails `terraform validate`
instead of silently shrinking your coverage.

Each entry carries:

```
certificateId  category  management  acmPresence  issuanceType  signedBy
domain  sans  wildcard  multiSan  certificateType  duplicateOf
expiryScenario  plannedValidityDays  actualNotAfter  renewalEligibility
awsAccountId  awsRegion  certificateArn  resourceId
attachment  awsService  storageLocation  storeReference
expectedDiscoveryMechanism  expectedRenewalType
expectedScannerClassification  expectedAcmStatus
implemented  notes
```

Coverage the matrix guarantees:

| Dimension | Covered by |
| --- | --- |
| RSA-2048 | 25 scenarios |
| RSA-4096 | 025 |
| ECDSA P-256 | 009 (ACM-issued), 017 (imported) |
| ECDSA P-384 | 028 |
| Wildcard | 002, 010, 012, 022 |
| Multi-SAN | 003, 009, 013, 025, and every 2-name certificate |
| Single domain | 001, 004, 006, 007, 008, 011, 014, 015, 018–024, 027–029 |
| Self-signed | 011, 012, 013, 014, 016, 019, 020, 021, 022, 023, 027, 028, 029 |
| CA-signed | 015, 017, 018, 025 (lab CA) · 024, 026, 030 (OpenSSL lab CA) |
| ACM-issued | 001–004, 006–010 |
| ACM-imported | 011–017, 019, 020 |
| Outside ACM | 018, 021–030 |
| EXPIRED | 024, 030 |
| ≤1 day | 022 |
| ≤7 days | 014, 027 |
| ≤30 days | 021, 026 |
| ≤60 days | 013, 029 |
| ≤90 days | 023 |
| >1 year | 015, 016, 020, 025 |
| Attached | 011, 012, 013, 016, 017, 019, 027, 028 (+ ACM-issued when enabled) |
| Unattached | 008, 009, 010, 014, 015, 018, 020, 021–026, 030 |
| Duplicate | 020 duplicates 016 |
| Multiple certs on one listener | ALB HTTPS carries 4–8; NLB TLS carries 1–2 |

## 7. AWS services used

| Service | Purpose | Cost impact |
| --- | --- | --- |
| ACM | 19 certificates (9 issued + 10 imported) | free |
| ELBv2 — ALB | HTTP + HTTPS/SNI listener relationships | ~18 USD/month |
| ELBv2 — NLB | TCP + TLS/SNI listener relationships | ~20 USD/month |
| API Gateway (REST) | REGIONAL custom domain relationships | no hourly charge |
| EC2 | filesystem certificate host + SSM target | ~9 USD/month |
| Systems Manager | Run Command scanning path | free |
| Secrets Manager | 2 certificate secrets | 0.40 USD/secret/month |
| SSM Parameter Store | 6 parameters (Standard tier) | free |
| S3 | private certificate bucket + EC2 staging | pennies |
| IAM | instance role, and the server certificate store | free |
| VPC / IGW / SG | dedicated network, **no NAT gateway** | free |
| CloudFront *(optional)* | default-certificate scenario | ~0 without traffic |
| Route 53 *(optional)* | dedicated test zone for DNS validation | 0.50 USD/month |
| AWS Private CA *(optional)* | real ISSUED ACM-managed certificates | **~400 USD/month** |

## 8. Certificate locations

| Store | Path / key pattern |
| --- | --- |
| ACM | `arn:aws:acm:eu-west-1:<account>:certificate/<uuid>` |
| IAM | `arn:aws:iam::<account>:server-certificate/certificate-test-lab/...` |
| Secrets Manager | `/certificate-test/phase1/cert-021`, `.../cert-022` |
| Parameter Store | `/certificate-test/phase1/cert-0NN/{certificate,chain,private-key}` |
| S3 | `s3://<bucket>/certificates/phase1-cert-0NN/...` |
| S3 (staging) | `s3://<bucket>/ec2-staging/phase1-cert-0NN/...` |
| EC2 | `/etc/ssl/certs`, `/etc/ssl/private`, `/etc/nginx/ssl`, `/etc/apache2/ssl`, `/etc/httpd/conf/ssl`, `/opt/certificates`, `/opt/application/certs` |

The exact paths and keys for your deployment are in
`terraform output -json certificate_stores`.

## 9. Expected discovery mechanism

| Mechanism | Scenarios | API calls |
| --- | --- | --- |
| `ACM_LIST_CERTIFICATES` | 001–004, 006–017, 019, 020 | `acm:ListCertificates` → `acm:DescribeCertificate` |
| `ELBV2_DESCRIBE_LISTENERS` | 011, 016 | `elasticloadbalancing:DescribeLoadBalancers` → `DescribeListeners` |
| `ELBV2_DESCRIBE_LISTENER_CERTIFICATES` | 006, 007, 012, 013, 017 | → `DescribeListenerCertificates` (returns **all** certs, with `IsDefault`) |
| `APIGATEWAY_GET_DOMAIN_NAMES` | 004, 019 | `apigateway:GetDomainNames` → `GetBasePathMappings` |
| `IAM_LIST_SERVER_CERTIFICATES` | 018 | `iam:ListServerCertificates` → `iam:GetServerCertificate` |
| `SECRETSMANAGER_LIST_SECRETS_AND_GET_SECRET_VALUE` | 021, 022 | list by prefix → `GetSecretValue` → **parse PEM for notAfter** |
| `SSM_GET_PARAMETERS_BY_PATH` | 023, 024 | `ssm:GetParametersByPath` → **parse PEM** |
| `S3_LIST_OBJECTS_AND_GET_OBJECT` | 025, 026 | `s3:ListObjectsV2` → `GetObject` → parse PEM **or PKCS#12** |
| `SSM_RUN_COMMAND_FILESYSTEM_SCAN` | 027–030 | `ssm:SendCommand` (`AWS-RunShellScript`) → `GetCommandInvocation` |
| `CLOUDFRONT_LIST_DISTRIBUTIONS` | 005 (Phase 2) | `cloudfront:ListDistributions` |

Note the asymmetry that matters: ACM and IAM **report** `NotAfter` directly.
Secrets Manager, Parameter Store, S3 and the filesystem do not — you must
retrieve and parse the certificate. Four of the 30 scenarios exist purely to
make sure you do.

## 10. Expected renewal mechanism

| Renewal type | Scenarios | Reality |
| --- | --- | --- |
| `ACM_AUTOMATIC` | 001–004, 006, 007 | ACM renews automatically **only while the certificate is in use** |
| `ACM_AUTOMATIC_BUT_INELIGIBLE_WHILE_UNUSED` | 008, 009, 010 | ACM-issued but unattached → `RenewalEligibility = INELIGIBLE` |
| `MANUAL_REIMPORT` | 011–017, 019, 020 | ACM **never** renews an import. You must re-import. |
| `MANUAL_DELETE_AND_REUPLOAD` | 018 | IAM has no renewal mechanism at all |
| `MANUAL_EXTERNAL` | 021–030 | the store has no certificate lifecycle whatsoever |

The renewal classifications your engine must produce are in the inventory as
`expectedRenewalType` and `renewalEligibility`.

## ACM validation without a public DNS zone

**This is the single most important design constraint in Phase 1, so read it
before your first apply.**

A **public ACM certificate stays in `PENDING_VALIDATION`** until its DNS
validation `CNAME` resolves in the *public* DNS hierarchy. That requires you
to actually control the domain. And an **ALB, NLB or API Gateway will reject a
certificate that is not `ISSUED`**.

Those two facts together mean a naive lab cannot have "ACM-issued certificates
attached to an ALB". Rather than shipping Terraform that looks right and fails
at apply, Phase 1 gives you three honest options.

### Option A — default, free, zero prerequisites

```hcl
acm_issuance_mode              = "public_dns_validation"  # default
create_route53_test_zone       = false                    # default
attach_acm_issued_certificates = false                    # default
```

* Scenarios 001–010 are created and sit in `PENDING_VALIDATION`.
  **This is intended.** `PENDING_VALIDATION` is a real state your scanner must
  detect and classify, and most estates contain a few.
* Every **attached** certificate comes from an **ACM import**, which is
  `ISSUED` the instant it is created. So the ALB, NLB and API Gateway
  relationships all work out of the box.
* `terraform output acm_dns_validation_records` gives you the exact records
  needed if you later want to validate them by hand.

### Option B — you own a public domain

```hcl
certificate_test_domain        = "certlab.mycompany.com"
create_route53_test_zone       = true
wait_for_acm_dns_validation    = true
attach_acm_issued_certificates = true
```

1. Apply once with `create_route53_test_zone = true` and the other two `false`.
2. Read `terraform output route53_test_zone_name_servers`.
3. Create the `NS` delegation for that subdomain at your registrar.
4. Apply again with `wait_for_acm_dns_validation` and
   `attach_acm_issued_certificates` set to `true`.

The zone created is **brand new and dedicated**. This lab never reads,
imports or modifies an existing hosted zone. Cost: 0.50 USD/month.

> If you set `wait_for_acm_dns_validation = true` **without** a working
> delegation, `terraform apply` will block until the validation times out.
> A `check` block warns you about the incoherent combination up front.

### Option C — AWS Private CA (the technically complete answer)

```hcl
acm_issuance_mode              = "private_ca"
enable_private_ca              = true
attach_acm_issued_certificates = true
```

AWS Private CA is the **only** way to obtain genuinely ACM-managed,
auto-renewing, `ISSUED`, attachable certificates with no DNS dependency
whatsoever. ACM issues them from your own CA in seconds.

> **Cost warning.** A `GENERAL_PURPOSE` CA is roughly **400 USD/month**
> (pro-rated), `SHORT_LIVED_CERTIFICATE` mode roughly **50 USD/month**, plus a
> per-certificate charge. It is disabled by default and is by far the most
> expensive switch in this repository. `SHORT_LIVED_CERTIFICATE` caps
> certificate validity at 7 days, which changes the expiry scenarios.

## 11. SSM setup

The instance is a fully managed SSM node, which is the **primary** scanning
path:

```
Spring Boot → AWS SDK → ssm:SendCommand → EC2 → certificate scanner
```

What Terraform builds for you:

* IAM role `certificate-test-lab-phase1-cert-server-role`
* `AmazonSSMManagedInstanceCore` attached
* a narrow inline policy: `s3:GetObject` on **only** the
  `ec2-staging/*` prefix of the lab bucket
* instance profile `certificate-test-lab-phase1-cert-server-profile`
* outbound HTTPS to the SSM endpoints through the internet gateway
  (no NAT gateway, no interface endpoint charges)
* the SSM agent enabled at boot (it ships with Amazon Linux 2023)
* a ready-made scanner at `/usr/local/bin/certificate-lab-scan` that emits
  one JSON object per certificate found, including SHA-256 fingerprints

Drive it from the CLI exactly as your backend will:

```bash
INSTANCE_ID=$(terraform output -raw -json ssm_scan_target | jq -r .instance_id)

CMD=$(aws ssm send-command \
  --region eu-west-1 \
  --document-name AWS-RunShellScript \
  --instance-ids "$INSTANCE_ID" \
  --parameters 'commands=["/usr/local/bin/certificate-lab-scan"]' \
  --query Command.CommandId --output text)

aws ssm get-command-invocation --region eu-west-1 \
  --command-id "$CMD" --instance-id "$INSTANCE_ID" \
  --query StandardOutputContent --output text
```

`terraform output -json ssm_scan_target` also gives you
`example_cli_command` pre-filled with the instance ID.

The instance writes the **expected answer** for the filesystem scenarios to
`/opt/certificates/certificate-inventory.json`, so you can diff your scanner's
output against it. Bootstrap progress is in
`/var/log/certificate-lab-bootstrap.log`, and
`/opt/certificates/BOOTSTRAP_COMPLETE` appears when the instance is ready
(allow 2–4 minutes after apply).

If `enable_vpc_endpoints = true`, SSM interface endpoints are created so the
instance can live in a private subnet instead — that costs about
7.50 USD/month per endpoint per AZ and is off by default.

## 12. SSH fallback

**Disabled by default, and when disabled no port 22 rule exists at all.**

SSM Run Command needs no inbound port, is audited in CloudTrail, and requires
no key management, so it is strictly better for this purpose. SSH exists only
so you can test your fallback code path.

```hcl
enable_ssh_fallback = true
ssh_allowed_cidrs   = ["203.0.113.10/32"]   # your egress IP, narrowly scoped
ssh_public_key      = "ssh-ed25519 AAAAC3... you@example"
```

Security controls built in:

* `ssh_allowed_cidrs` is empty by default — enabling the feature without
  naming a CIDR trips a `check` block rather than silently opening nothing.
* `0.0.0.0/0` in `ssh_allowed_cidrs` is **refused** unless you also set
  `allow_ssh_from_anywhere = true`. Two deliberate opt-ins, not one.
* Only the **public** half of your key is ever handled. This lab never
  generates, stores or outputs an SSH private key.
* If you enable it, understand the implication: you are exposing a shell
  listener to whatever range you named. Keep it to a `/32`, and turn it off
  again when you are done.

## 13. How to initialise Terraform

```bash
cd phases/phase-1
terraform init
```

Requirements: Terraform ≥ 1.5, AWS credentials for a **non-production**
account, `bash` + `openssl` locally (for scenarios 024/026/030 — set
`enable_openssl_generated_certificates = false` if you do not have them).

## 14. How to validate

```bash
terraform fmt -recursive ../..
terraform validate
```

`terraform validate` also runs the `check` blocks' static assertions, so it
catches an incoherent flag combination, an SSH rule that is too wide, and a
test matrix that has drifted from 30 scenarios.

## 15. How to plan

```bash
cp terraform.tfvars.example terraform.tfvars   # optional
terraform plan -out=phase1.tfplan
```

Expect roughly **120–140 resources** with the defaults. Several values show as
`(known after apply)` — that is correct: the OpenSSL-generated files are read
during apply, so anything derived from them cannot be known at plan time.

Review the plan before applying. Things worth checking:

* no `aws_acmpca_*` resources unless you deliberately enabled Private CA
* no `aws_route53_zone` unless you deliberately enabled the test zone
* no `aws_vpc_security_group_ingress_rule` on port 22 unless you enabled SSH
* every resource name starts with `certificate-test-lab-phase1` (load
  balancers use the abbreviated `certlab-phase1` prefix — see
  [Known AWS limitations](#20-known-aws-limitations))

## 16. How to apply Phase 1

```bash
terraform apply phase1.tfplan
```

Then:

```bash
# the 30-scenario inventory with real ARNs
terraform output -json certificate_inventory | jq

# coverage counters
terraform output -json certificate_inventory_summary | jq

# certificate → service relationships
terraform output -json service_relationships | jq

# where every non-ACM certificate lives
terraform output -json certificate_stores | jq

# what this deployment costs you per month
terraform output -json ongoing_cost_resources | jq
```

The same inventory is written to `phases/phase-1/certificate-inventory.json`
(gitignored) so a test harness can read it without invoking Terraform.

Wait 2–4 minutes for the EC2 bootstrap, then confirm:

```bash
aws ssm describe-instance-information --region eu-west-1 \
  --query 'InstanceInformationList[].{Id:InstanceId,Ping:PingStatus}'
```

## 17. How to destroy Phase 1

```bash
cd phases/phase-1
terraform destroy
```

Everything is disposable by design:

* `s3_force_destroy = true` so the bucket empties itself
* `secrets_recovery_window_days = 0` so secret names are released immediately
  (with the 7–30 day default, a re-apply would fail on a name collision)
* no deletion protection on either load balancer
* `force_destroy = true` on the optional Route 53 zone

Two things `terraform destroy` does **not** clean up:

```bash
# locally generated test certificates (never committed, but still on disk)
rm -rf phases/phase-1/.generated-certificates

# the generated inventory file
rm -f phases/phase-1/certificate-inventory.json
```

If you enabled **AWS Private CA**, destroy disables the CA and schedules
deletion with a restore window of `private_ca_permanent_deletion_time_in_days`
(7 by default). You are not billed for a CA in `DISABLED` or
`PENDING_DELETION` state, but confirm it in the console — this is the one
resource where a mistake is genuinely expensive.

Partial teardown, to keep the cheap scenarios and drop the expensive ones:

```bash
terraform apply -var enable_alb=false -var enable_nlb=false -var enable_ec2=false
```

## 18. Expected AWS costs

`eu-west-1`, on-demand, no traffic. Indicative only — check AWS pricing for
current figures.

| Resource | Monthly | On by default |
| --- | --- | --- |
| ALB | ~18.00 USD + LCU | yes |
| NLB | ~20.00 USD + NLCU | yes |
| EC2 t3.micro | ~8.00 USD | yes |
| EBS 10 GiB gp3 | ~0.90 USD | yes |
| Secrets Manager × 2 | ~0.80 USD | yes |
| S3 bucket | <0.10 USD | yes |
| ACM (19 certificates) | 0.00 USD | yes |
| Parameter Store (Standard) | 0.00 USD | yes |
| IAM server certificate | 0.00 USD | yes |
| VPC, IGW, subnets, SGs | 0.00 USD | yes |
| API Gateway custom domain | 0.00 USD without requests | yes |
| **Default total** | **≈ 48 USD/month** | |
| Route 53 hosted zone | +0.50 USD | no |
| CloudFront distribution | ~0 without traffic | no |
| SSM interface endpoints × 3 × 2 AZ | **+45 USD** | no |
| **AWS Private CA** | **+400 USD** (or +50 short-lived) | **no** |

**Resources that should be destroyed after testing:** the ALB, the NLB, the
EC2 instance and its EBS volume, the Secrets Manager secrets, and above all
the Private CA if you enabled it. `terraform output ongoing_cost_resources`
lists exactly what *your* configuration is paying for.

Cost decisions baked into the design:

* **no NAT gateway** (~33 USD/month avoided) — public subnet + IGW instead
* **no Elastic IPs** — the auto-assigned public IP is free while attached
* **no VPC endpoints** by default (~45 USD/month avoided)
* **no databases**, no ECS, no EKS, no managed services beyond what
  certificate testing requires
* smallest practical instance and volume
* every costed resource has its own `enable_*` switch

## 19. Security considerations

**Certificate material**

* Every key is generated at apply time, exists only for this lab, and is
  worthless. No real certificate or private key is ever involved.
* Private keys **do** land in Terraform state. Treat the state file as a
  secret and use an encrypted remote backend — `versions.tf` has a commented
  S3 backend block ready to uncomment.
* Generated OpenSSL artefacts go to `.generated-certificates/`, which is
  gitignored. `.gitignore` also blocks `*.pem`, `*.key`, `*.p12`, `*.pfx`,
  `*.jks` and `*.tfstate*` repository-wide.
* `pkcs12_password` is a **test** value. It is stored in state and written to
  the instance. Never put a real password there.

**Network**

* Brand-new dedicated VPC. Nothing pre-existing is touched.
* Load balancers are **internal** by default and have **no ingress rule at
  all** until you populate `allowed_ingress_cidrs`.
* The EC2 instance accepts nothing from the internet. Its only inbound rules
  come from the load balancer security group and the VPC CIDR.
* Port 22 does not exist unless you explicitly enable the SSH fallback, and
  `0.0.0.0/0` needs a second explicit opt-in.

**Data at rest**

* S3: SSE-S3 with bucket keys, all four public-access blocks on, ACLs
  disabled (`BucketOwnerEnforced`), and a bucket policy that **denies any
  non-TLS request**.
* EBS root volume encrypted.
* Parameter Store: private keys are `SecureString`. Certificate bodies are
  plain `String` deliberately — that is realistic, and detecting it is itself
  a finding worth raising.
* Instance metadata: IMDSv2 required, hop limit 1.

**IAM**

* One new role with `AmazonSSMManagedInstanceCore` plus a single inline
  policy scoped to one S3 prefix. No wildcards on resources.
* No IAM user is created, and no existing user, role or policy is modified.
* The IAM server certificate is filed under a dedicated path
  (`/certificate-test-lab/`) so it can never be confused with anything real.

**Blast radius**

* Every resource name is prefixed. Nothing is imported. No `data` source reads
  a production resource. There is no second provider alias, so no other region
  can be touched.

## 20. Known AWS limitations

Each of these is a real AWS constraint. In every case the limitation is
documented, a valid alternative is implemented, and the unsupported variant is
recorded in the matrix rather than shipped as Terraform that fails at apply.

| # | Limitation | What Phase 1 does instead |
| --- | --- | --- |
| 1 | **CloudFront requires a us-east-1 ACM certificate.** | Scenario 005 is not created; matrix entry `DEFERRED_TO_PHASE_2`. Optional distribution using the **default CloudFront certificate** (no ACM anywhere) is offered instead. `cloudfront/README.md` has the Phase 2 design. |
| 2 | **A public ACM certificate needs DNS you control**, or it stays `PENDING_VALIDATION` forever. | Three documented options (A/B/C above). The default accepts `PENDING_VALIDATION` as a test case and uses imports for everything attached. |
| 3 | **ELB and API Gateway reject a non-`ISSUED` certificate.** | All default listener certificates are ACM imports. ACM-issued certificates are layered on only when actually issuable. |
| 4 | **ACM refuses to import an expired certificate.** | No `EXPIRED` scenario lives in ACM. 014 at 7 days is the closest possible. The two `EXPIRED` scenarios live in Parameter Store and on the filesystem. |
| 5 | **ACM never auto-renews an imported certificate.** | All 10 imports are classified `MANUAL_REIMPORT` / `RenewalEligibility = INELIGIBLE`. |
| 6 | **ACM will not auto-renew an unattached certificate**, even one it issued. | 008, 009 and 010 are classified `ACM_AUTOMATIC_BUT_INELIGIBLE_WHILE_UNUSED`. |
| 7 | **`hashicorp/tls` cannot back-date `notBefore`.** | `scripts/generate-test-certificates.sh` uses `openssl ca -startdate -enddate`. |
| 8 | **No Terraform resource emits a PKCS#12 file.** | Same script builds it with `openssl pkcs12 -export`. |
| 9 | **No Terraform resource emits a JKS keystore.** | Built **on the instance** with `keytool`, so the machine running Terraform needs no Java. |
| 10 | **ELB supports ECDSA P-256 and P-384 only** (not P-521). | 017 uses P-256 on the ALB. 009 uses ACM `EC_prime256v1` but is left unattached. 028 uses P-384 on the filesystem. P-521 is not used anywhere. |
| 11 | **ELB and target group names are capped at 32 characters.** | The 27-character lab prefix cannot fit `-nlb-tls-tg`. Load balancers use the abbreviated `certlab-phase1` prefix (`var.load_balancer_name_prefix`) while keeping the full `Project`/`Phase` tags. |
| 12 | **An IAM server certificate can only be used by a Classic Load Balancer or CloudFront.** | 018 is created for real but left unattached. No CLB is created just to consume it; that is a Phase 4 scenario. |
| 13 | **IAM is a global service.** | 018 has `region = "global"` and an ARN with no region. This does not violate the eu-west-1 rule — there is no regional IAM endpoint to choose. |
| 14 | **API Gateway EDGE custom domains need a us-east-1 certificate.** | `REGIONAL` endpoint type only, using `regional_certificate_arn` from eu-west-1. EDGE is a Phase 2 scenario. |
| 15 | **AWS tag values disallow `*` and `,`.** | Wildcard domains are not tags; they are exposed through the inventory output. A `CertificateWildcard` boolean tag is used instead. |
| 16 | **Terraform cannot use a sensitive value in `for_each`.** | Iteration is driven by plain id lists; sensitive PEM material is looked up inside the resource body. |
| 17 | **An NLB has no security group by default.** | The instance security group allows the VPC CIDR on 80/443/8443 so NLB health checks succeed. |
| 18 | **nginx and Apache cannot both bind :443.** | nginx owns 443, Apache is configured on 8443, and the distro `ssl.conf` is moved aside. |
| 19 | **Secrets Manager reserves a deleted secret's name for 7–30 days.** | `secrets_recovery_window_days = 0` so destroy-then-apply cycles work. |
| 20 | **ACM requires a chain for a CA-signed import.** | CA-signed imports send the lab CA as `certificate_chain`. `acm_include_chain` exists as an escape hatch if ACM ever rejects a self-signed root in the chain. |

## 21. Which certificates are ACM-issued

`001, 002, 003, 004, 006, 007, 008, 009, 010` — nine created; `005` is
documented but not created (see limitation 1). ACM `Type` is `AMAZON_ISSUED`
in `public_dns_validation` mode, or `PRIVATE` in `private_ca` mode.

```bash
terraform output -json acm_issued_certificates | jq
```

## 22. Which are imported

`011, 012, 013, 014, 015, 016, 017, 019, 020` — ACM `Type = IMPORTED`,
`RenewalEligibility = INELIGIBLE`, `ISSUED` immediately.

```bash
terraform output -json acm_imported_certificates | jq
```

## 23. Which are filesystem certificates

`027` (nginx), `028` (Apache), `029` (Java/Spring Boot), `030` (expired
orphan). All on the single EC2 instance.

```bash
terraform output -json certificate_stores | jq .ec2_filesystem
```

## 24. Which are in Secrets Manager

`021` (WARNING, 30 days) and `022` (CRITICAL, 1 day, wildcard), as JSON
secrets with `certificate` / `privateKey` / `certificateChain` fields under
`/certificate-test/phase1/`.

## 25. Which are in Parameter Store

`023` (WARNING, 90 days) and `024` (**EXPIRED**), each split across three
parameters: `certificate` (String), `chain` (String) and `private-key`
(SecureString).

Parameter Store is included **because** real estates use it for certificates,
even though ACM or Secrets Manager are the better choice for production
certificate lifecycle management. Discovery tooling has to cope with it.

## 26. Which are in S3

`025` (RSA-4096, CA-signed, multi-SAN — `cert.pem`, `cert.crt`, `chain.pem`,
`fullchain.pem`, `cert.key`) and `026` (**PKCS#12** — `test.p12`, `test.pfx`,
`cert.crt`), under `s3://<bucket>/certificates/`.

The `ec2-staging/` prefix also holds bootstrap copies of 027–030. Those are
not primary discovery targets, but a thorough scanner will find them, which is
a useful "same certificate in two stores" case.

## 27. Which are attached to ALB / NLB / API Gateway

| Service | Listener | Default certificate | Additional (SNI) |
| --- | --- | --- | --- |
| ALB | `HTTPS:443` | **016** | 012, 013, 017 (+ 001, 002, 003, 007 when enabled) |
| ALB | `HTTP:80` | *none* — negative test case | — |
| NLB | `TLS:443` | **011** | 006 when enabled |
| NLB | `TCP:80` | *none* — negative test case | — |
| API Gateway | `api019.<pub>` | **019** | — |
| API Gateway | `api004.<pub>` | **004** when enabled | — |

```bash
terraform output -json service_relationships | jq .load_balancer_listeners
```

With the defaults, the ALB HTTPS listener carries **four** certificates and
the NLB TLS listener **one**. Enable `attach_acm_issued_certificates` and
those become **eight** and **two**.

## 28. Which are intentionally unused

`008, 009, 010` (ACM-issued, unused) · `014, 015, 020` (imported, unused) ·
`018` (IAM, unattachable without a CLB) · `021–026` (secret/parameter/object
stores) · `030` (expired orphan on disk).

`020` is the most interesting: an exact subject+SAN duplicate of the
**attached** `016`. Your engine should group them as duplicates by
subject+SAN, distinguish them by SHA-256 fingerprint, and flag the unattached
copy as dead weight.

## 29. Which are expired or near expiry

| Scenario | Certificates |
| --- | --- |
| **EXPIRED** | `024` (Parameter Store, −35 days) · `030` (filesystem, −90 days) |
| ≤1 day | `022` |
| ≤7 days | `014`, `027` |
| ≤30 days | `021`, `026` |
| ≤60 days | `013`, `029` |
| ≤90 days | `023` |
| >1 year | `015` (3y), `016` (2y), `020` (2y), `025` (2y) |

Because several of these are genuinely short-lived, the lab **drifts**. After
a day `022` is expired; after a week `014` and `027` are too. That is often
useful. To reset:

```bash
terraform apply -var 'openssl_generation_trigger=v2' -replace='module.certificates.module.acm_imported["phase1-cert-014"].tls_self_signed_cert.this[0]'
```

or simply destroy and re-apply for a clean matrix.

## 30. Future Phase 2 design

Phase 2 adds regions. Its full design is in `phases/phase-2/README.md`;
the headline items:

* a `us-east-1` provider alias, and the **CloudFront** scenario 005 finally
  built properly
* API Gateway **EDGE** custom domains (also us-east-1)
* the same certificate subject in two regions, to test cross-region duplicate
  detection
* multi-region ALBs to test that region is part of your certificate identity
* a shared-module refactor so region becomes a parameter rather than a copy

Phase 2 will be a **separate root module with separate state**. Nothing in
Phase 1 will need restructuring: the category modules and
`../../modules/*` are already region-agnostic, and Phase 1's single-provider
`providers.tf` is exactly the file Phase 2 replaces.
