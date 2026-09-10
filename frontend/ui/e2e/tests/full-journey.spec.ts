import { test, expect } from '@playwright/test';
import * as fs from 'fs';

test('Final E2E Journey: Account to Scan Details Export', async ({ page }) => {
  // Mock API endpoints to support the journey since we are navigating end-to-end
  let mockAccounts: any[] = [];
  
  
    await page.route('**/api/v1/organisations/org-001', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'org-001', setupComplete: true }) });
    });
await page.route('**/api/v1/organisations/org-001/providers', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ id: 'aws-1', type: 'AWS', name: 'AWS Provider' }]) });
  });
  
  await page.route('**/api/v1/environments', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ id: 'env-1', name: 'PROD' }]) });
  });
  
  await page.route('**/api/v1/accounts', async route => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockAccounts) });
    } else if (route.request().method() === 'POST') {
      const body = JSON.parse(route.request().postData() || '{}');
      const newAcc = { id: `acc-full-${Date.now()}`, ...body, status: 'CONNECTED' };
      mockAccounts.push(newAcc);
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(newAcc) });
    } else {
      await route.fallback();
    }
  });

  await page.route('**/api/v1/accounts/*', async route => {
    if (route.request().method() === 'PUT') {
      const body = JSON.parse(route.request().postData() || '{}');
      mockAccounts = mockAccounts.map(acc => acc.id === route.request().url().split('/').pop() ? { ...acc, ...body } : acc);
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    } else {
      await route.fallback();
    }
  });

  await page.route('**/api/v1/accounts/*/test-connection', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status: 'CONNECTED', message: 'Authenticated as arn:aws:sts::123456789012:assumed-role/CertRole/certplatform in eu-west-2' }) });
  });

  // Login is just navigation for now as auth is not fully mocked
  await page.goto('/accounts');

  // Add Account
  await page.getByRole('button', { name: 'Add Account' }).click();
  await page.getByLabel(/Cloud Provider/i).selectOption('AWS');
  await page.getByLabel(/Environment/i).selectOption('PROD');
  await page.getByLabel(/Account Name/i).fill('E2E Prod Account');
  await page.getByLabel(/Account ID/i).fill('123456789012');
  
  await page.getByLabel('IAM Role').check();
  await page.getByLabel('Role ARN').fill('arn:aws:iam::123456789012:role/CertRole');

  await page.getByRole('button', { name: 'Save Account' }).click();
  await expect(page.getByRole('row', { name: /E2E Prod Account/i })).toBeVisible();

  // Edit Account - and only now can the connection be tested, since the test reads
  // the credentials back from the stored account.
  const accRow = page.getByRole('row', { name: /E2E Prod Account/i });
  await accRow.getByRole('button', { name: 'Edit', exact: true }).click();

  await page.getByRole('button', { name: 'Test Connection' }).click();
  await expect(page.getByText(/Connection successful/i)).toBeVisible();

  await page.getByLabel('Token', { exact: true }).check();
  await page.getByLabel('Token Value').fill('e2e-token-secret');
  await page.getByRole('button', { name: 'Save Account' }).click();

  await expect(accRow).toContainText('TOKEN');

  // Go to Scans
  await page.getByRole('link', { name: 'Scans' }).click();
  
  // Scans from wiremock should be listed
  await expect(page.getByRole('row', { name: /Production Full Certificate Scan/i })).toBeVisible();
  
  // Expand scan details
  await page.getByRole('row', { name: /Production Full Certificate Scan/i }).click();
  
  // Verify certificates table is there
  await expect(page.getByRole('heading', { name: /Certificates Discovered/i })).toBeVisible();
  
  // Open certificate details
  await page.getByRole('row', { name: /api.example.com/i }).first().click();
  
  const modal = page.getByRole('dialog');
  await expect(modal).toBeVisible();
  await expect(modal).toContainText('Certificate Details');
  await expect(modal).toContainText('Amazon RSA 2048 M02');
  
  // Close modal
  await modal.getByRole('button', { name: '×' }).click();
  
  // Export CSV
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: /Export CSV/i }).click();
  const download = await downloadPromise;
  
  const path = await download.path();
  expect(path).toBeTruthy();
  const csv = await fs.promises.readFile(path, 'utf8');
  expect(csv).toContain('api.example.com');
  
  // Return to scan history (collapsing)
  await page.getByRole('row', { name: /Production Full Certificate Scan/i }).first().click();
  await expect(page.getByRole('heading', { name: /Certificates Discovered/i })).not.toBeVisible();
});
