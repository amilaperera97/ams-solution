import { test, expect } from '@playwright/test';

test.describe('Scan Scope Enhancements', () => {
  test.beforeEach(async ({ page }) => {
    // Mock the dependent APIs
    const mockProviders = [
      { id: 'aws', type: 'AWS', name: 'AWS' },
      { id: 'azure', type: 'Azure', name: 'Azure' }
    ];
    const mockAwsEnvs = [{ id: 'prod', name: 'PROD' }, { id: 'dev', name: 'DEV' }];
    const mockAzureEnvs = [{ id: 'prod', name: 'PROD' }];
    
    const mockProdAccounts = [
      { id: 'account-001', name: 'Production Account UK', provider: 'aws', environment: 'prod', accountId: '123456789012' },
      { id: 'account-002', name: 'Production Shared Services', provider: 'aws', environment: 'prod', accountId: '678901234567' },
      { id: 'azure-account-001', name: 'Azure Prod Account', provider: 'azure', environment: 'prod', accountId: 'AZ-123' }
    ];

    
    await page.route('**/api/v1/organisations/org-001', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'org-001', setupComplete: true }) });
    });
await page.route('**/api/v1/organisations/org-001/providers', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockProviders) });
    });

    await page.route('**/api/v1/organisations/org-001/providers/aws/environments', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockAwsEnvs) });
    });
    
    await page.route('**/api/v1/organisations/org-001/providers/azure/environments', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockAzureEnvs) });
    });

    await page.route('**/api/v1/environments/prod/accounts', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockProdAccounts) });
    });

    await page.route('**/api/v1/environments/dev/accounts', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
    });

    await page.route('**/api/v1/scans', async route => {
      if (route.request().method() === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}');
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ id: `scan-mock-${Date.now()}`, status: 'Queued', ...body })
        });
      } else if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 'historical-scan-1', name: 'Historical Scan', scopeType: 'ACCOUNT', providers: ['aws'], environments: ['prod'], accounts: ['account-001'], status: 'Completed', certificatesFound: 10 }
          ])
        });
      } else {
        await route.fallback();
      }
    });
  });

  test('TC-SCAN-SCOPE-001: user can create a scan for an entire cloud provider', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByLabel('Scan Name').fill('AWS Full Scan');
    await page.getByRole('radio', { name: 'Scan by Cloud Provider' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption('aws');

    await expect(page.getByText(/All AWS accounts/i)).toBeVisible();

    await page.getByRole('button', { name: 'Review Scan' }).click();

    await expect(page.getByText('AWS Full Scan')).toBeVisible();
    await expect(page.getByText(/AWS/i).first()).toBeVisible();

    await page.getByRole('button', { name: 'Start Scan' }).click();

    // Verification it returns to scan list
    await expect(page.getByRole('button', { name: 'New Scan' })).toBeVisible();
  });

  test('TC-SCAN-SCOPE-002: user can create a scan for a specific environment', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByLabel('Scan Name').fill('AWS Production Environment Scan');
    await page.getByRole('radio', { name: 'Scan by Environment' }).check();

    await page.getByLabel('Provider', { exact: true }).selectOption('aws');
    await page.getByLabel('Environment', { exact: true }).selectOption('prod');

    await expect(page.getByText(/All accounts in PROD/i)).toBeVisible();

    await page.getByRole('button', { name: 'Review Scan' }).click();

    await expect(page.getByText(/AWS/i).first()).toBeVisible();
    await expect(page.getByText(/PROD/i).first()).toBeVisible();

    await page.getByRole('button', { name: 'Start Scan' }).click();
  });

  test('TC-SCAN-SCOPE-003: user can create a scan for a specific account', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByLabel('Scan Name').fill('Test Scan');
    await page.getByRole('radio', { name: 'Scan by Account' }).check();

    await page.getByLabel('Provider', { exact: true }).selectOption('aws');
    await page.getByLabel('Environment', { exact: true }).selectOption('prod');
    await page.getByLabel('Account', { exact: true }).selectOption('account-001');

    await expect(page.getByText('123456789012').first()).toBeVisible();
    await expect(page.getByText('Production Account UK').first()).toBeVisible();

    await page.getByRole('button', { name: 'Review Scan' }).click();

    await expect(page.getByText('Production Account UK').first()).toBeVisible();

    await page.getByRole('button', { name: 'Start Scan' }).click();
  });

  test('TC-SCAN-SCOPE-004: user can scan multiple accounts', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByLabel('Scan Name').fill('Test Scan');
    await page.getByRole('radio', { name: 'Scan by Account' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption('aws');
    await page.getByLabel('Environment', { exact: true }).selectOption('prod');

    await page.getByLabel('Account', { exact: true }).selectOption(['account-001', 'account-002']);

    await expect(page.getByText(/2 accounts selected/i)).toBeVisible();

    await page.getByRole('button', { name: 'Review Scan' }).click();

    await expect(page.getByText('Production Account UK').first()).toBeVisible();
    await expect(page.getByText('Production Shared Services').first()).toBeVisible();
  });

  test('TC-SCAN-SCOPE-005: environment options depend on selected provider', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByRole('radio', { name: 'Scan by Environment' }).check();
    
    await page.getByLabel('Provider', { exact: true }).selectOption('aws');
    await expect(page.getByLabel('Environment', { exact: true }).locator('option', { hasText: 'PROD' })).toBeVisible();
    await expect(page.getByLabel('Environment', { exact: true }).locator('option', { hasText: 'DEV' })).toBeVisible();

    await page.getByLabel('Provider', { exact: true }).selectOption('azure');
    await expect(page.getByLabel('Environment', { exact: true }).locator('option', { hasText: 'PROD' })).toBeVisible();
    await expect(page.getByLabel('Environment', { exact: true }).locator('option', { hasText: 'DEV' })).not.toBeVisible();
  });

  test('TC-SCAN-SCOPE-006: changing provider clears invalid environment and account selections', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByRole('radio', { name: 'Scan by Account' }).check();

    await page.getByLabel('Provider', { exact: true }).selectOption('aws');
    await page.getByLabel('Environment', { exact: true }).selectOption('prod');
    await page.getByLabel('Account', { exact: true }).selectOption('account-001');

    await page.getByLabel('Provider', { exact: true }).selectOption('azure');

    await expect(page.getByLabel('Account', { exact: true })).toHaveValues([]);
  });

  test('TC-SCAN-SCOPE-007: user can create an organisation-wide scan', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByLabel('Scan Name').fill('Test Scan');
    await page.getByRole('radio', { name: 'Scan by Cloud Provider' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption('all');

    await expect(page.getByText(/All configured cloud providers/i)).toBeVisible();

    await page.getByRole('button', { name: 'Review Scan' }).click();

    await expect(page.getByText(/All Providers/i).first()).toBeVisible();
  });

  test('TC-SCAN-SCOPE-008: user can create a custom multi-provider scan', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByLabel('Scan Name').fill('Test Scan');
    await page.getByRole('radio', { name: 'Custom Scan' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption(['aws', 'azure']);
    await page.getByLabel('Environment', { exact: true }).selectOption(['prod']);
    await page.getByLabel('Account', { exact: true }).selectOption(['account-001', 'azure-account-001']);

    await expect(page.getByText(/2 accounts selected/i)).toBeVisible();

    await page.getByRole('button', { name: 'Review Scan' }).click();

    await expect(page.getByText(/AWS/i).first()).toBeVisible();
    await expect(page.getByText(/Azure/i).first()).toBeVisible();
  });

  test('TC-SCAN-SCOPE-009: scan review shows the exact selected scope', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByLabel('Scan Name').fill('API Test Scan');
    await page.getByRole('radio', { name: 'Scan by Account' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption('aws');
    await page.getByLabel('Environment', { exact: true }).selectOption('prod');
    await page.getByLabel('Account', { exact: true }).selectOption('account-001');

    await page.getByLabel('eu-west-2').check();
    await page.getByLabel('ACM').check();
    await page.getByLabel('ALB').check();

    await page.getByRole('button', { name: 'Review Scan' }).click();

    await expect(page.getByText('AWS', { exact: true })).toBeVisible();
    await expect(page.getByText('PROD', { exact: true })).toBeVisible();
    await expect(page.getByText('Production Account UK').first()).toBeVisible();
    await expect(page.getByText('eu-west-2')).toBeVisible();
    await expect(page.getByText('ACM')).toBeVisible();
    await expect(page.getByText('ALB')).toBeVisible();
  });

  test('TC-SCAN-SCOPE-010: user can modify scan scope before starting', async ({ page }) => {
    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByLabel('Scan Name').fill('Scan');
    await page.getByRole('radio', { name: 'Scan by Environment' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption('aws');
    await page.getByLabel('Environment', { exact: true }).selectOption('prod');

    await page.getByRole('button', { name: 'Review Scan' }).click();
    await page.getByRole('button', { name: 'Back' }).click();

    await page.getByLabel('Environment', { exact: true }).selectOption('dev');
    await page.getByRole('button', { name: 'Review Scan' }).click();

    await expect(page.getByText('DEV', { exact: true })).toBeVisible();
    await expect(page.getByText('PROD', { exact: true })).not.toBeVisible();
  });

  test('TC-SCAN-SCOPE-011: started scan displays its configured scope in scan history', async ({ page }) => {
    await page.goto('/scans');
    
    const row = page.getByRole('row', { name: /Historical Scan/ });
    await expect(row).toContainText('AWS');
    await expect(row).toContainText('PROD');
  });

  test('API request sends correct payload', async ({ page }) => {
    let capturedBody: any;
    await page.route('**/api/v1/scans', async route => {
      if (route.request().method() === 'POST') {
        capturedBody = JSON.parse(route.request().postData() || '{}');
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 'scan-1', ...capturedBody }) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/scans');
    await page.getByRole('button', { name: 'New Scan' }).click();

    await page.getByLabel('Scan Name').fill('API Test Scan');
    await page.getByRole('radio', { name: 'Scan by Account' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption('aws');
    await page.getByLabel('Environment', { exact: true }).selectOption('prod');
    await page.getByLabel('Account', { exact: true }).selectOption('account-001');

    await page.getByRole('button', { name: 'Review Scan' }).click();
    await page.getByRole('button', { name: 'Start Scan' }).click();

    expect(capturedBody).toBeDefined();
    expect(capturedBody.scopeType).toBe('ACCOUNT');
    expect(capturedBody.providers).toEqual(['aws']);
    expect(capturedBody.environments).toEqual(['prod']);
    expect(capturedBody.accounts).toEqual(['account-001']);
  });

  test('Run Again populates previous scope', async ({ page }) => {
    await page.goto('/scans');
    
    const row = page.getByRole('row', { name: /Historical Scan/ });
    await row.getByRole('button', { name: 'Run Again' }).click();
    
    await expect(page.getByRole('heading', { name: 'Run Scan Again' })).toBeVisible();
    await expect(page.getByRole('radio', { name: 'Scan by Account' })).toBeChecked();
    
    await expect(page.getByLabel('Provider', { exact: true })).toHaveValue('aws');
    await expect(page.getByLabel('Environment', { exact: true })).toHaveValue('prod');
    
    await expect(page.getByLabel('Account', { exact: true })).toHaveValues(['account-001']);
  });
});
