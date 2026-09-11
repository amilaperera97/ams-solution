#!/usr/bin/env bash
#
# generate-test-certificates.sh
#
# Produces the TEST certificate artefacts that the Terraform providers cannot
# create natively:
#
#   * BACK-DATED, ALREADY-EXPIRED certificates
#     hashicorp/tls always sets notBefore = now, so it can never emit an
#     expired certificate. `openssl ca -startdate/-enddate` can.
#
#   * PKCS#12 (.p12 / .pfx) keystores
#     No Terraform resource emits a PKCS#12 container.
#
# EVERYTHING generated here is a throwaway self-signed-CA test artefact.
# No real certificate and no real private key is ever involved.
#
# The output directory is gitignored. Do not commit anything it contains.
#
# Usage:
#   ./generate-test-certificates.sh --output-dir <dir> \
#        [--internal-domain <domain>] [--p12-password <pw>] [--force]
#
# Requirements: bash 4+, openssl 1.1.1+ (or 3.x), GNU or BSD date.
#
set -euo pipefail

OUTPUT_DIR=""
INTERNAL_DOMAIN="cert-lab.internal"
P12_PASSWORD="certificate-test-lab"
FORCE="false"

usage() {
  sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir)      OUTPUT_DIR="${2:?--output-dir requires a value}"; shift 2 ;;
    --internal-domain) INTERNAL_DOMAIN="${2:?--internal-domain requires a value}"; shift 2 ;;
    --p12-password)    P12_PASSWORD="${2:?--p12-password requires a value}"; shift 2 ;;
    --force)           FORCE="true"; shift ;;
    -h|--help)         usage 0 ;;
    *) echo "ERROR: unknown argument: $1" >&2; usage 1 ;;
  esac
done

if [[ -z "$OUTPUT_DIR" ]]; then
  echo "ERROR: --output-dir is required" >&2
  exit 1
fi

command -v openssl >/dev/null 2>&1 || { echo "ERROR: openssl not found on PATH" >&2; exit 1; }

echo "openssl: $(openssl version)"

# --- Portable UTC date arithmetic (GNU date and BSD/macOS date) ------------
utc_offset_days() {
  local days="$1"
  if date -u -d "now" +%Y >/dev/null 2>&1; then
    date -u -d "${days} days" +%Y%m%d%H%M%SZ            # GNU
  else
    local sign="+"
    [[ "$days" == -* ]] && { sign="-"; days="${days#-}"; }
    date -u -v"${sign}${days}d" +%Y%m%d%H%M%SZ          # BSD / macOS
  fi
}

CA_DIR="$OUTPUT_DIR/ca"
CA_KEY="$CA_DIR/openssl-lab-ca.key"
CA_CRT="$CA_DIR/openssl-lab-ca.crt"

mkdir -p "$CA_DIR/newcerts"
chmod 700 "$CA_DIR"

# --- The OpenSSL lab CA -----------------------------------------------------
if [[ "$FORCE" == "true" || ! -f "$CA_CRT" || ! -f "$CA_KEY" ]]; then
  echo "==> generating OpenSSL lab CA"
  openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 -nodes \
    -keyout "$CA_KEY" -out "$CA_CRT" \
    -subj "/C=IE/O=Certificate Test Lab/OU=Phase 1/CN=Certificate Test Lab OpenSSL CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" >/dev/null 2>&1
  chmod 600 "$CA_KEY"
else
  echo "==> reusing existing OpenSSL lab CA"
fi

# `openssl ca` needs a database; recreate it on every run so serials and the
# index never drift out of sync with the files on disk.
: > "$CA_DIR/index.txt"
echo "unique_subject = no" > "$CA_DIR/index.txt.attr"
echo "1000" > "$CA_DIR/serial"

cat > "$CA_DIR/openssl.cnf" <<CNF
[ ca ]
default_ca = CA_default

[ CA_default ]
dir               = $CA_DIR
database          = \$dir/index.txt
new_certs_dir     = \$dir/newcerts
serial            = \$dir/serial
certificate       = $CA_CRT
private_key       = $CA_KEY
default_md        = sha256
default_days      = 365
preserve          = no
email_in_dn       = no
rand_serial       = no
unique_subject    = no
policy            = policy_any

[ policy_any ]
countryName            = optional
stateOrProvinceName    = optional
localityName           = optional
organizationName       = optional
organizationalUnitName = optional
commonName             = supplied
emailAddress           = optional

[ req ]
default_md         = sha256
prompt             = no
distinguished_name = req_distinguished_name

[ req_distinguished_name ]
CN = placeholder
CNF

# ---------------------------------------------------------------------------
# issue_certificate <cert-id> <common-name> <san-list> <start-days> <end-days> <key-spec>
#   start/end days are offsets from now; NEGATIVE values back-date, which is
#   how the EXPIRED scenarios are produced.
# ---------------------------------------------------------------------------
issue_certificate() {
  local cert_id="$1" cn="$2" sans="$3" start_days="$4" end_days="$5" key_spec="$6"
  local dir="$OUTPUT_DIR/$cert_id"

  if [[ "$FORCE" != "true" && -f "$dir/cert.crt" && -f "$dir/cert.key" ]]; then
    echo "==> $cert_id already present, skipping"
    return 0
  fi

  echo "==> issuing $cert_id  CN=$cn  notBefore=${start_days}d  notAfter=${end_days}d"
  mkdir -p "$dir"
  chmod 700 "$dir"

  # SAN extension file
  {
    echo "basicConstraints = critical,CA:FALSE"
    echo "keyUsage = critical,digitalSignature,keyEncipherment"
    echo "extendedKeyUsage = serverAuth,clientAuth"
    echo "subjectKeyIdentifier = hash"
    echo "subjectAltName = @alt_names"
    echo ""
    echo "[ alt_names ]"
    local index=1
    local san
    for san in $sans; do
      echo "DNS.$index = $san"
      index=$((index + 1))
    done
  } > "$dir/ext.cnf"

  openssl req -new -newkey "$key_spec" -nodes -sha256 \
    -keyout "$dir/cert.key" -out "$dir/cert.csr" \
    -subj "/C=IE/O=Certificate Test Lab/OU=Phase 1/CN=$cn" >/dev/null 2>&1

  openssl ca -batch -notext -config "$CA_DIR/openssl.cnf" \
    -in "$dir/cert.csr" -out "$dir/cert.crt" \
    -startdate "$(utc_offset_days "$start_days")" \
    -enddate "$(utc_offset_days "$end_days")" \
    -extfile "$dir/ext.cnf" >/dev/null 2>&1

  # Convenience copies in the shapes scanners commonly look for.
  cp "$dir/cert.crt" "$dir/cert.pem"
  cat "$dir/cert.crt" "$CA_CRT" > "$dir/fullchain.pem"
  cp "$CA_CRT" "$dir/chain.pem"

  chmod 600 "$dir/cert.key"
  chmod 644 "$dir/cert.crt" "$dir/cert.pem" "$dir/fullchain.pem" "$dir/chain.pem"

  echo "    notBefore/notAfter: $(openssl x509 -in "$dir/cert.crt" -noout -startdate -enddate | tr '\n' ' ')"
}

# ---------------------------------------------------------------------------
# The three scenarios that need OpenSSL.
# ---------------------------------------------------------------------------

# phase1-cert-024  EXPIRED, RSA-2048, destination: SSM Parameter Store
issue_certificate "phase1-cert-024" \
  "ps024-expired.$INTERNAL_DOMAIN" \
  "ps024-expired.$INTERNAL_DOMAIN" \
  -400 -35 "rsa:2048"

# phase1-cert-026  WARNING (30 days), RSA-2048, destination: S3 as PKCS#12
issue_certificate "phase1-cert-026" \
  "s3-026.$INTERNAL_DOMAIN" \
  "s3-026.$INTERNAL_DOMAIN keystore026.$INTERNAL_DOMAIN" \
  0 30 "rsa:2048"

# phase1-cert-030  EXPIRED, RSA-2048, destination: EC2 filesystem
issue_certificate "phase1-cert-030" \
  "legacy030.$INTERNAL_DOMAIN" \
  "legacy030.$INTERNAL_DOMAIN old.legacy030.$INTERNAL_DOMAIN" \
  -730 -90 "rsa:2048"

# --- PKCS#12 keystore for phase1-cert-026 ----------------------------------
P12_DIR="$OUTPUT_DIR/phase1-cert-026"
if [[ "$FORCE" == "true" || ! -f "$P12_DIR/cert.p12" ]]; then
  echo "==> building PKCS#12 keystore for phase1-cert-026"
  openssl pkcs12 -export \
    -inkey "$P12_DIR/cert.key" \
    -in "$P12_DIR/cert.crt" \
    -certfile "$CA_CRT" \
    -name "phase1-cert-026" \
    -macalg sha256 \
    -out "$P12_DIR/cert.p12" \
    -passout "pass:$P12_PASSWORD"
  cp "$P12_DIR/cert.p12" "$P12_DIR/cert.pfx"
  chmod 600 "$P12_DIR/cert.p12" "$P12_DIR/cert.pfx"
fi

# --- Manifest ---------------------------------------------------------------
cat > "$OUTPUT_DIR/manifest.json" <<JSON
{
  "generatedBy": "phases/phase-1/scripts/generate-test-certificates.sh",
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "warning": "Throwaway TEST material only. Never commit. Never trust.",
  "ca": {
    "certificate": "ca/openssl-lab-ca.crt",
    "privateKey": "ca/openssl-lab-ca.key"
  },
  "certificates": [
    {
      "certificateId": "phase1-cert-024",
      "expiryScenario": "EXPIRED",
      "keyAlgorithm": "RSA-2048",
      "destination": "SSM Parameter Store",
      "files": ["phase1-cert-024/cert.crt", "phase1-cert-024/cert.key", "phase1-cert-024/chain.pem"]
    },
    {
      "certificateId": "phase1-cert-026",
      "expiryScenario": "WARNING",
      "keyAlgorithm": "RSA-2048",
      "destination": "S3 (PKCS#12)",
      "files": ["phase1-cert-026/cert.crt", "phase1-cert-026/cert.key", "phase1-cert-026/cert.p12", "phase1-cert-026/cert.pfx"]
    },
    {
      "certificateId": "phase1-cert-030",
      "expiryScenario": "EXPIRED",
      "keyAlgorithm": "RSA-2048",
      "destination": "EC2 filesystem",
      "files": ["phase1-cert-030/cert.crt", "phase1-cert-030/cert.key", "phase1-cert-030/fullchain.pem"]
    }
  ]
}
JSON

echo
echo "Done. Artefacts in: $OUTPUT_DIR"
echo "Reminder: this directory is gitignored and must stay that way."
