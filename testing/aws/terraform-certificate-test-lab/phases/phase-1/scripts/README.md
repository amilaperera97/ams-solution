# Phase 1 helper scripts

## `generate-test-certificates.sh`

Produces the three test artefacts that **no Terraform provider can create**.

### Why it exists

| Artefact | Why Terraform cannot do it |
| --- | --- |
| Back-dated / already-EXPIRED certificates | `hashicorp/tls` always sets `notBefore` to the moment of apply. There is no way to ask it for a certificate that expired last month. `openssl ca -startdate -enddate` can do exactly that. |
| PKCS#12 / PFX keystores | No Terraform resource emits a PKCS#12 container. |

ACM refuses to import an expired certificate, so the expired scenarios could
never have lived in ACM regardless — they belong in Parameter Store and on the
EC2 filesystem, which is where this script's output ends up.

### What it generates

```
<output-dir>/
├── ca/
│   ├── openssl-lab-ca.crt          self-signed test CA, 10 years
│   └── openssl-lab-ca.key
├── phase1-cert-024/                EXPIRED  -> SSM Parameter Store
│   ├── cert.crt  cert.pem  cert.key  chain.pem  fullchain.pem
├── phase1-cert-026/                WARNING (30 days) -> S3 as PKCS#12
│   ├── cert.crt  cert.pem  cert.key  chain.pem  fullchain.pem
│   ├── cert.p12
│   └── cert.pfx
├── phase1-cert-030/                EXPIRED  -> EC2 filesystem
│   └── cert.crt  cert.pem  cert.key  chain.pem  fullchain.pem
└── manifest.json
```

| Certificate | notBefore | notAfter | Scenario |
| --- | --- | --- | --- |
| `phase1-cert-024` | −400 days | **−35 days** | `EXPIRED` |
| `phase1-cert-026` | now | +30 days | `WARNING` |
| `phase1-cert-030` | −730 days | **−90 days** | `EXPIRED` |

### How it is invoked

**Automatically, during `terraform apply`** (the default). `acm/openssl_generated.tf`
runs it from a `terraform_data` resource with a `local-exec` provisioner, and
the `local_file` / `local_sensitive_file` data sources that read the output
`depends_on` it — which defers their read to apply time. That is what lets a
clean checkout run `terraform plan` before the files exist.

**Manually**, if you prefer to keep `local-exec` out of your pipeline:

```bash
cd phases/phase-1
./scripts/generate-test-certificates.sh \
  --output-dir "$PWD/.generated-certificates" \
  --internal-domain cert-lab.internal \
  --p12-password certificate-test-lab
```

then set `enable_openssl_generated_certificates` to keep reading the files but
stop Terraform re-running the script — or simply leave it `true`, since the
script is idempotent without `--force`.

### Options

| Flag | Default | Meaning |
| --- | --- | --- |
| `--output-dir <dir>` | *required* | Where to write. Terraform passes `phases/phase-1/.generated-certificates`. |
| `--internal-domain <domain>` | `cert-lab.internal` | Must match `var.certificate_internal_domain`. |
| `--p12-password <pw>` | `certificate-test-lab` | Must match `var.pkcs12_password`. |
| `--force` | off | Regenerate everything, ignoring existing files. Terraform always passes this. |

### Requirements

* `bash` 4+
* `openssl` 1.1.1 or 3.x
* GNU `date` or BSD/macOS `date` (both are handled)

No Java is needed. The JKS keystore for `phase1-cert-029` is built **on the EC2
instance** with `keytool`, which the instance already has.

### Refreshing drifted certificates

`phase1-cert-026` is only valid for 30 days and `phase1-cert-022` for 1 day.
To regenerate:

```bash
terraform apply -var 'openssl_generation_trigger=v2'
```

Changing that value replaces the `terraform_data` resource, which re-runs the
script with `--force` and re-uploads everything downstream.

### Security

* Every key is generated locally, used only by this lab, and is worthless.
* The output directory is **gitignored** and must stay that way.
* Nothing here is ever a real certificate or a real private key.
* `terraform destroy` does **not** delete the generated directory. Remove it
  yourself when you are finished:

  ```bash
  rm -rf phases/phase-1/.generated-certificates
  ```
