# Certificate Discovery Platform

Spring Boot backend (`backend/`) plus a Vite/React frontend (`frontend/frontend/`).
`./start.sh` runs both.

## Profiles

| Profile | AWS calls | Database | Credentials |
|---------|-----------|----------|-------------|
| `dev` (default) | simulated (`MOCK`) | `backend/certplatform-dev.db` | plaintext tolerated - never point it at a real account |
| `qa` | real AWS (`REAL`) | `backend/certplatform-qa.db` | encrypted at rest, `CERTPLATFORM_SECRET_KEY` required |

```bash
./start.sh                  # dev
./start.sh --profile qa     # real AWS
./start.sh --help           # every flag
```

## Account authentication

An account records how the platform authenticates to one cloud account.

| Auth type | Use it for | Stored |
|-----------|-----------|--------|
| `IAM_ROLE` | **preferred for real AWS** | role ARN, optional external ID, optional bootstrap access keys |
| `ACCESS_KEY` | AWS where a role cannot be assumed | access key id, secret access key (encrypted) |
| `TOKEN` | mock/dev flows, and non-AWS providers that use bearer tokens | token (encrypted) |

`TOKEN` cannot reach real AWS - AWS only accepts SigV4-signed requests. When the AWS
provider is in `REAL` mode the account form hides the option, and the backend rejects
it as well, so no request ever leaves with a bearer token in hand.

Secrets are encrypted with `CERTPLATFORM_SECRET_KEY` (AES-256-GCM) before they reach
the database, and the API never returns a token, role ARN or secret access key - only
whether one is configured, plus the access key id masked to its last four characters.

### How IAM_ROLE authenticates

The backend calls STS `AssumeRole` on the account's role ARN and uses the returned
session credentials for the actual API calls.

- **With bootstrap access keys stored on the account**, those keys make the
  `AssumeRole` call.
- **With no keys stored** - the normal local-development case - the AWS SDK
  `DefaultCredentialsProvider` chain supplies the calling identity: environment
  credentials (`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN`),
  Java system properties, the web identity token file, then the profile named by
  `AWS_PROFILE` in `~/.aws/credentials` and `~/.aws/config` (including AWS SSO
  profiles, once `aws sso login` has been run), and finally container or instance
  metadata when running inside AWS.

The external ID is sent only when the account has one; set it when the target role's
trust policy carries an `sts:ExternalId` condition.

## Local QA against real AWS

Running on localhost only decides **where the backend process runs**. AWS API calls
still leave your machine for real AWS endpoints, against the real account, and any
change they make is real. The single exception is `AWS_ENDPOINT_OVERRIDE`: set it and
the SDK talks to that address (LocalStack) instead of AWS.

### 1. Configure or log in to an AWS profile

For a static profile:

```bash
aws configure --profile qa
```

For AWS SSO:

```bash
aws configure sso --profile qa   # first time only
aws sso login --profile qa
```

### 2. Verify the identity

```bash
aws sts get-caller-identity --profile qa
```

This must succeed and print the account and ARN you expect before going further. If it
fails here it will fail in the backend for the same reason.

### 3. Export the profile

```bash
export AWS_PROFILE=qa
```

The backend inherits this from the shell that runs `start.sh`; the SDK's default chain
reads it when assuming the role.

### 4. Export the encryption key

```bash
export CERTPLATFORM_SECRET_KEY="$(openssl rand -base64 32)"
```

Keep it - stored credentials cannot be read back with a different key, and the `qa`
profile refuses to start without one.

### 5. Start the stack

```bash
./start.sh --profile qa
```

Backend on `http://localhost:8080`, frontend on `http://localhost:5173`.

### 6. Add an account in the UI

Accounts → Add Account. With the AWS provider in `REAL` mode the form offers IAM Role
and Access Key only. For IAM Role, fill in the role ARN, an AWS region (required), and
the external ID if the role's trust policy demands one. Then use **Test Connection** -
it runs STS `GetCallerIdentity` with the account's credentials and checks the identity
AWS reports belongs to the account ID you entered.

### Required AWS permissions

Two separate things have to line up.

**1. Your local identity must be allowed to assume the role.** Attach a policy to the
user or role that `aws sts get-caller-identity` reported in step 2:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "sts:AssumeRole",
    "Resource": "arn:aws:iam::123456789012:role/CertificateDiscoveryRole"
  }]
}
```

**2. The target role must trust that identity.** Its trust policy needs the caller as a
principal:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "AWS": "arn:aws:iam::111122223333:user/your-local-user" },
    "Action": "sts:AssumeRole"
  }]
}
```

If you configure an external ID on the account, the trust policy must require the same
value, and `sts:AssumeRole` calls without it are denied:

```json
"Condition": { "StringEquals": { "sts:ExternalId": "your-external-id" } }
```

Missing either side gives `AccessDenied` from STS, which surfaces as a failed
connection test rather than a partial scan.

The assumed role itself needs read access to whatever is being discovered, e.g.
`acm:ListCertificates` and `acm:DescribeCertificate`.

### Pointing QA at LocalStack instead

```bash
export AWS_ENDPOINT_OVERRIDE=http://localhost:4566
./start.sh --profile qa
```

The provider stays in `REAL` mode - the same code path, the same credential
resolution - but every SDK call goes to LocalStack. Unset it to go back to real AWS.

### Troubleshooting

| Symptom | Cause |
|---------|-------|
| `Unable to load credentials from any of the providers` | no `AWS_PROFILE`, or the SSO session expired - re-run `aws sso login` |
| `AccessDenied` on `AssumeRole` | your identity lacks `sts:AssumeRole`, or the role's trust policy does not name it |
| `AccessDenied` mentioning the external ID | the account's external ID and the trust policy condition disagree |
| App will not start on `qa` | `CERTPLATFORM_SECRET_KEY` is not exported |
| `Failed to decrypt a stored secret` | the key differs from the one the value was written with |
| `TOKEN auth cannot reach real AWS` | the account is still on `TOKEN`; switch it to IAM Role or Access Key |

## Tests

```bash
cd backend && ./gradlew test          # backend
cd frontend/frontend && npm test      # frontend component tests (vitest)
cd frontend/frontend && npm run test:e2e   # Playwright
```
