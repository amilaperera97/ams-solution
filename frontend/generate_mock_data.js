const fs = require('fs');
const path = require('path');

const mappingsDir = path.join(__dirname, 'wiremock', 'mappings');
const filesDir = path.join(__dirname, 'wiremock', '__files');

function writeMapping(filename, request, response) {
    fs.writeFileSync(path.join(mappingsDir, filename), JSON.stringify({ request, response }, null, 2));
}

function writeDataFile(filename, data) {
    fs.writeFileSync(path.join(filesDir, filename), JSON.stringify(data, null, 2));
}

// 1. Organization
writeMapping('org-get.json', { method: 'GET', url: '/api/v1/organisations/org-001' }, { status: 200, bodyFileName: 'organization.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('org-put.json', { method: 'PUT', url: '/api/v1/organisations/org-001' }, { status: 200, bodyFileName: 'organization.json', headers: { 'Content-Type': 'application/json' } });
writeDataFile('organization.json', { id: 'org-001', name: 'ABC Corporation', setupComplete: false });

// 2. Providers
writeMapping('providers-get.json', { method: 'GET', url: '/api/v1/organisations/org-001/providers' }, { status: 200, bodyFileName: 'providers.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('providers-post.json', { method: 'POST', url: '/api/v1/organisations/org-001/providers' }, { status: 201, bodyFileName: 'provider-created.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('providers-put.json', { method: 'PUT', urlPattern: '/api/v1/providers/[^/]+' }, { status: 200, bodyFileName: 'provider-created.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('providers-delete.json', { method: 'DELETE', urlPattern: '/api/v1/providers/[^/]+' }, { status: 204 });
writeMapping('provider-environments.json', { method: 'GET', urlPattern: '/api/v1/providers/[^/]+/environments' }, { status: 200, bodyFileName: 'environments.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('provider-test.json', { method: 'POST', urlPattern: '/api/v1/accounts/[^/]+/test-connection' }, { status: 200, bodyFileName: 'connection-test.json', headers: { 'Content-Type': 'application/json' } });

writeDataFile('provider-created.json', { id: 'prov-mock', name: 'Mock Provider', type: 'AWS', status: 'Pending', lastSync: null });
writeDataFile('connection-test.json', { success: true, message: 'Connection successful' });

const providers = [
    { id: 'aws-provider-001', type: 'AWS', name: 'AWS Production', status: 'Connected', accountsCount: 12, environmentsCount: 4, certificatesCount: 820 },
    { id: 'azure-provider-001', type: 'Azure', name: 'Azure Corp', status: 'Connected', accountsCount: 8, environmentsCount: 2, certificatesCount: 180 },
    { id: 'gcp-provider-001', type: 'GCP', name: 'GCP Main', status: 'Warning', accountsCount: 5, environmentsCount: 2, certificatesCount: 70 }
];
writeDataFile('providers.json', providers);

// 3. Environments
writeMapping('envs-get.json', { method: 'GET', url: '/api/v1/environments' }, { status: 200, bodyFileName: 'environments.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('envs-post.json', { method: 'POST', url: '/api/v1/environments' }, { status: 201, bodyFileName: 'env-created.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('envs-put.json', { method: 'PUT', urlPattern: '/api/v1/environments/[^/]+' }, { status: 200, bodyFileName: 'env-created.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('envs-delete.json', { method: 'DELETE', urlPattern: '/api/v1/environments/[^/]+' }, { status: 204 });
writeMapping('env-accounts.json', { method: 'GET', urlPattern: '/api/v1/environments/[^/]+/accounts' }, { status: 200, bodyFileName: 'accounts.json', headers: { 'Content-Type': 'application/json' } });

writeDataFile('env-created.json', { id: 'env-mock', name: 'Mock Environment', accountsCount: 0, certificatesCount: 0 });

const environments = [
    { id: 'env-prod', name: 'PROD', accountsCount: 15, certificatesCount: 840 },
    { id: 'env-staging', name: 'STAGING', accountsCount: 5, certificatesCount: 120 },
    { id: 'env-qa', name: 'QA', accountsCount: 3, certificatesCount: 80 },
    { id: 'env-dev', name: 'DEV', accountsCount: 2, certificatesCount: 30 }
];
writeDataFile('environments.json', environments);

// 4. Accounts
writeMapping('accounts-get.json', { method: 'GET', urlPathPattern: '/api/v1/accounts' }, { status: 200, bodyFileName: 'accounts.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('accounts-post.json', { method: 'POST', url: '/api/v1/accounts' }, { status: 201, bodyFileName: 'account-created.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('accounts-put.json', { method: 'PUT', urlPattern: '/api/v1/accounts/.*' }, { status: 200, bodyFileName: 'account-created.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('accounts-delete.json', { method: 'DELETE', urlPattern: '/api/v1/accounts/.*' }, { status: 204 });

writeDataFile('account-created.json', { id: 'acc-mock', name: 'Mock Account', provider: 'AWS', environment: 'DEV', status: 'Active', certificatesCount: 0 });

const accounts = [
    { id: 'aws-prod-001', name: 'aws-prod-main', provider: 'AWS', environment: 'PROD', status: 'Active', certificatesCount: 450 },
    { id: 'aws-prod-002', name: 'aws-prod-db', provider: 'AWS', environment: 'PROD', status: 'Active', certificatesCount: 120 },
    { id: 'azure-prod-001', name: 'azure-prod-eu', provider: 'Azure', environment: 'PROD', status: 'Active', certificatesCount: 270 },
    { id: 'gcp-qa-001', name: 'gcp-qa-sandbox', provider: 'GCP', environment: 'QA', status: 'Suspended', certificatesCount: 80 }
];
writeDataFile('accounts.json', accounts);

// 5. Certificates
// Generate 120 certificates
const certificates = [];
const services = ['ALB', 'API Gateway', 'CloudFront', 'ECS', 'EKS', 'Application Gateway', 'App Service', 'Key Vault', 'Load Balancer'];
const statuses = ['Healthy', 'Expiring Soon', 'Critical', 'Expired'];

for (let i=1; i<=120; i++) {
    const isAws = i <= 60;
    const isAzure = i > 60 && i <= 90;
    
    let provider = isAws ? 'AWS' : (isAzure ? 'Azure' : 'GCP');
    let env = ['DEV', 'QA', 'STAGING', 'PROD'][i % 4];
    
    let now = new Date();
    let expiryDays = Math.floor(Math.random() * 400) - 20; // some expired (-20) to +380 days
    let expiresAt = new Date(now.getTime() + expiryDays * 24 * 60 * 60 * 1000);
    
    let status = 'Healthy';
    if (expiryDays < 0) status = 'Expired';
    else if (expiryDays < 7) status = 'Critical';
    else if (expiryDays < 30) status = 'Expiring Soon';

    certificates.push({
        id: `cert-${i.toString().padStart(4, '0')}`,
        name: `cert-${provider.toLowerCase()}-${i}`,
        domain: `app${i}.example.com`,
        provider: provider,
        environment: env,
        account: `${provider}-account-01`,
        region: isAws ? 'eu-west-2' : 'uksouth',
        service: services[i % services.length],
        status: status,
        issuedAt: new Date(now.getTime() - 100 * 24 * 60 * 60 * 1000).toISOString(),
        expiresAt: expiresAt.toISOString(),
        issuer: "Let's Encrypt",
        certificateType: 'Public',
        lastDiscoveredAt: new Date().toISOString()
    });
}
writeMapping('certs-get.json', { method: 'GET', urlPathPattern: '/api/v1/certificates' }, { status: 200, bodyFileName: 'certificates.json', headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' } });
writeDataFile('certificates.json', certificates);

// CORS for all
writeMapping('cors.json', { method: 'OPTIONS', urlPattern: '.*' }, { status: 200, headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type' } });

// Dashboard summary stats
writeMapping('dashboard-stats.json', { method: 'GET', url: '/api/v1/dashboard/stats' }, { status: 200, bodyFileName: 'dashboard.json', headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' } });
writeDataFile('dashboard.json', {
    providers: 3,
    environments: 4,
    accounts: 25,
    certificates: 120,
    health: {
        expired: certificates.filter(c => c.status === 'Expired').length,
        critical: certificates.filter(c => c.status === 'Critical').length,
        expiringSoon: certificates.filter(c => c.status === 'Expiring Soon').length,
        healthy: certificates.filter(c => c.status === 'Healthy').length
    }
});

// 5. Scans
writeMapping('scans-get.json', { method: 'GET', urlPathPattern: '/api/v1/scans' }, { status: 200, bodyFileName: 'scans.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('scans-post.json', { method: 'POST', url: '/api/v1/scans' }, { status: 201, bodyFileName: 'scan-created.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('scans-put.json', { method: 'PUT', urlPattern: '/api/v1/scans/.*' }, { status: 200, bodyFileName: 'scan-created.json', headers: { 'Content-Type': 'application/json' } });
writeMapping('scans-delete.json', { method: 'DELETE', urlPattern: '/api/v1/scans/.*' }, { status: 204 });

writeDataFile('scan-created.json', { id: 'scan-mock', name: 'Mock Scan', status: 'Queued', startTime: null, certificatesFound: 0 });

const scans = [
    { id: 'scan-001', name: 'Production Full Certificate Scan', provider: 'AWS', environment: 'PROD', accountCount: 2, certificatesFound: 184, status: 'Completed', startTime: '2026-08-15T07:30:00Z', completedAt: '2026-08-15T07:34:21Z', durationSeconds: 261 },
    { id: 'scan-002', name: 'Production ALB Certificate Scan', provider: 'AWS', environment: 'PROD', accountCount: 1, certificatesFound: 48, status: 'Completed' },
    { id: 'scan-003', name: 'Azure Production Scan', provider: 'Azure', environment: 'PROD', accountCount: 2, certificatesFound: 76, status: 'Completed' },
    { id: 'scan-004', name: 'Development Certificate Scan', provider: 'AWS', environment: 'DEV', accountCount: 2, certificatesFound: 32, status: 'Completed' },
    { id: 'scan-005', name: 'QA Certificate Scan', provider: 'AWS', environment: 'QA', accountCount: 1, certificatesFound: 27, status: 'Completed' },
    { id: 'scan-006', name: 'Full Organisation Scan', provider: 'All', environment: 'All', accountCount: 8, certificatesFound: 427, status: 'Completed' },
    { id: 'scan-007', name: 'Staging Scan', provider: 'AWS', environment: 'STAGING', accountCount: 1, certificatesFound: 54, status: 'Completed' },
    { id: 'scan-008', name: 'Failed Production Scan', provider: 'AWS', environment: 'PROD', accountCount: 2, certificatesFound: 0, status: 'Failed', failureReason: 'Unable to connect to Production Account 2.' },
    { id: 'scan-009', name: 'Partial Organisation Scan', provider: 'All', environment: 'All', accountCount: 8, certificatesFound: 319, status: 'Partial Success', successfulAccounts: 6, failedAccounts: 2 },
    { id: 'scan-010', name: 'Current Production Scan', provider: 'AWS', environment: 'PROD', accountCount: 2, certificatesFound: 91, status: 'In Progress', progress: 62, completedAccounts: 1 }
];
writeDataFile('scans.json', scans);

// 6. Scan Certificates
writeMapping('scan-certificates-get.json', { method: 'GET', urlPattern: '/api/v1/scans/.*/certificates' }, { status: 200, bodyFileName: 'scan-certificates.json', headers: { 'Content-Type': 'application/json' } });

const scanCertificates = {
  scanId: 'scan-001',
  total: 184,
  items: [
    {
      id: "cert-001",
      certificateId: "cert-001",
      domain: "api.example.com",
      provider: "AWS",
      environment: "PROD",
      accountName: "Production Account UK",
      accountId: "123456789012",
      region: "eu-west-2",
      service: "ALB",
      resource: "prod-api-alb",
      status: "ACTIVE",
      issuedDate: "2026-06-15",
      expiryDate: "2026-09-15",
      issuer: "Amazon RSA 2048 M02",
      algorithm: "RSA 2048",
      autoRenewal: true,
      lastScanned: "2026-08-15T08:34:21Z",
      sans: ["api.example.com", "api.internal.example.com"]
    },
    {
      id: "cert-002",
      certificateId: "cert-002",
      domain: "www.example.com",
      provider: "AWS",
      environment: "PROD",
      accountName: "Production Account UK",
      accountId: "123456789012",
      region: "eu-west-2",
      service: "CloudFront",
      resource: "prod-cdn",
      status: "ACTIVE",
      issuedDate: "2025-10-21",
      expiryDate: "2026-10-21",
      issuer: "Amazon RSA 2048 M02",
      algorithm: "RSA 2048",
      autoRenewal: true,
      lastScanned: "2026-08-15T08:34:21Z",
      sans: ["www.example.com", "example.com"]
    },
    {
      id: "cert-003",
      certificateId: "cert-003",
      domain: "payments.example.com",
      provider: "AWS",
      environment: "PROD",
      accountName: "Production Shared",
      accountId: "678901234567",
      region: "eu-west-1",
      service: "ALB",
      resource: "prod-payments-alb",
      status: "EXPIRING SOON",
      issuedDate: "2025-09-05",
      expiryDate: "2026-09-05",
      issuer: "Let's Encrypt Authority X3",
      algorithm: "RSA 4096",
      autoRenewal: false,
      lastScanned: "2026-08-15T08:34:21Z",
      sans: ["payments.example.com"]
    }
  ]
};
writeDataFile('scan-certificates.json', scanCertificates);

console.log('WireMock data generated!');
