#!/bin/bash

# SetupPage
sed -i 's|/api/organisation|/api/v1/organisations/org-001|g' src/pages/SetupPage.tsx

# useApi
sed -i 's|/api/organisation|/api/v1/organisations/org-001|g' src/hooks/useApi.ts
sed -i 's|/api/providers|/api/v1/organisations/org-001/providers|g' src/hooks/useApi.ts
sed -i 's|/api/environments|/api/v1/environments|g' src/hooks/useApi.ts
sed -i 's|/api/accounts|/api/v1/accounts|g' src/hooks/useApi.ts
sed -i 's|/api/certificates|/api/v1/certificates|g' src/hooks/useApi.ts
sed -i 's|/api/dashboard/stats|/api/v1/dashboard/stats|g' src/hooks/useApi.ts

# Providers
sed -i "s|'/api/providers'|'/api/v1/organisations/org-001/providers'|g" src/pages/Providers.tsx
sed -i "s|\`/api/providers/\${providerToEdit.id}\`|\`/api/v1/providers/\${providerToEdit.id}\`|g" src/pages/Providers.tsx
sed -i "s|\`/api/providers/\${id}\`|\`/api/v1/providers/\${id}\`|g" src/pages/Providers.tsx
sed -i "s|\`/api/connections/\${id}/test\`|\`/api/v1/providers/\${id}/test-connection\`|g" src/pages/Providers.tsx

# Environments
sed -i 's|/api/environments|/api/v1/environments|g' src/pages/Environments.tsx

# Accounts
sed -i "s|'/api/accounts'|'/api/v1/accounts'|g" src/pages/Accounts.tsx
sed -i "s|'/api/providers'|'/api/v1/organisations/org-001/providers'|g" src/pages/Accounts.tsx
sed -i "s|'/api/environments'|'/api/v1/environments'|g" src/pages/Accounts.tsx
sed -i "s|\`/api/accounts/\${accountToEdit.id}\`|\`/api/v1/accounts/\${accountToEdit.id}\`|g" src/pages/Accounts.tsx
sed -i "s|\`/api/accounts/\${id}\`|\`/api/v1/accounts/\${id}\`|g" src/pages/Accounts.tsx
sed -i "s|\`/api/connections/test/test\`|\`/api/v1/accounts/test/test-connection\`|g" src/pages/Accounts.tsx

# Scans
sed -i "s|/api/scans|/api/v1/scans|g" src/pages/Scans.tsx

# NewScanModal
sed -i "s|'/api/providers'|'/api/v1/organisations/org-001/providers'|g" src/components/NewScanModal.tsx
sed -i "s|\`/api/providers/\${p}/environments\`|\`/api/v1/providers/\${p}/environments\`|g" src/components/NewScanModal.tsx
sed -i "s|\`/api/environments/\${e}/accounts\`|\`/api/v1/environments/\${e}/accounts\`|g" src/components/NewScanModal.tsx

