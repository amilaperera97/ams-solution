import { test, expect } from '@playwright/test';

test.describe('Configuration Journey', () => {
  test.beforeEach(async ({ page }) => {
    // Mock organisation to bypass setup
    await page.route('**/api/v1/organisations/org-001', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 'org-1', name: 'Test Org', setupComplete: true }),
      });
    });
  });

  test('TC-001: User can open Cloud Providers', async ({ page }) => {
    await page.route('**/api/v1/organisations/org-001/providers', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto('/providers');
    await expect(page.getByRole('heading', { name: /Cloud Providers/i })).toBeVisible();
    await expect(page.getByText(/No providers configured/i)).toBeVisible();
  });

  test('TC-002: User can add AWS provider', async ({ page }) => {
    let providers: any[] = [];
    
    await page.route('**/api/v1/organisations/org-001/providers', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(providers) });
      } else if (route.request().method() === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}');
        const newProvider = { id: 'prov-1', ...body, status: 'Pending' };
        providers.push(newProvider);
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(newProvider) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/providers');
    
    // Open add provider modal
    await page.getByRole('button', { name: /Add Provider/i }).click();
    
    // Fill form
    await page.getByLabel(/Provider Name/i).fill('My AWS Prod');
    await page.getByLabel(/Provider Type/i).selectOption('AWS');
    await page.getByRole('button', { name: /Save Provider/i }).click();
    
    // Check if it appears in the list
    await expect(page.getByText('My AWS Prod')).toBeVisible();
    await expect(page.getByText('Pending')).toBeVisible();
  });

  test('TC-006: User can test provider connection', async ({ page }) => {
    const provider = { id: 'prov-1', name: 'My AWS Prod', type: 'AWS', status: 'Pending' };
    
    await page.route('**/api/v1/organisations/org-001/providers', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([provider]) });
    });
    
    await page.route('**/api/v1/accounts/*/test-connection', async route => {
      provider.status = 'Connected';
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, message: 'Connection successful' }) });
    });

    await page.goto('/providers');
    
    await expect(page.getByText('Pending')).toBeVisible();
    
    // Click test connection
    await page.getByRole('button', { name: /Test Connection/i }).first().click();
    
    // Verify status changed
    await expect(page.getByText('Connected')).toBeVisible();
  });

  test('TC-CRUD-001: User can update a cloud provider', async ({ page }) => {
    let provider = { id: 'prov-update', name: 'AWS Production', type: 'AWS', status: 'Connected' };
    
    await page.route('**/api/v1/organisations/org-001/providers', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([provider]) });
      } else {
        await route.fallback();
      }
    });

    await page.route('**/api/v1/providers/*', async route => {
      if (route.request().method() === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}');
        provider = { ...provider, ...body };
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(provider) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/providers');
    
    // Check initial state
    await expect(page.getByText('AWS Production')).toBeVisible();
    
    // Click Edit
    const providerCard = page.locator('div').filter({ hasText: 'AWS Production' }).first();
    await providerCard.getByRole('button', { name: 'Edit' }).click();
    
    // Change value
    await page.getByLabel(/Provider Name/i).fill('AWS Production Updated');
    await page.getByRole('button', { name: /Save Provider/i }).click();
    
    // Verify updated state
    await expect(page.getByText('AWS Production Updated')).toBeVisible();
    await expect(page.getByText('AWS Production', { exact: true })).not.toBeVisible();
  });

  test('TC-CRUD-002: User can delete a cloud provider after confirmation', async ({ page }) => {
    let providers = [{ id: 'prov-del', name: 'AWS Production Updated', type: 'AWS', status: 'Connected' }];
    
    await page.route('**/api/v1/organisations/org-001/providers', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(providers) });
    });

    await page.route('**/api/v1/providers/*', async route => {
      if (route.request().method() === 'DELETE') {
        providers = [];
        await route.fulfill({ status: 204 });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/providers');
    
    // Check initial state
    const providerCard = page.locator('div').filter({ hasText: 'AWS Production Updated' }).first();
    await providerCard.getByRole('button', { name: 'Delete' }).click();
    
    // Dialog appears
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText(/Are you sure you want to delete/i)).toBeVisible();
    
    // Confirm delete
    await dialog.getByRole('button', { name: 'Delete' }).click();
    
    // Verify removal
    await expect(page.getByText('AWS Production Updated')).not.toBeVisible();
  });

  test('TC-CRUD-003: User can cancel provider deletion', async ({ page }) => {
    let providers = [{ id: 'prov-del-cancel', name: 'Azure Production', type: 'Azure', status: 'Connected' }];
    
    await page.route('**/api/v1/organisations/org-001/providers', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(providers) });
    });

    await page.goto('/providers');
    
    // Check initial state
    const providerCard = page.locator('div').filter({ hasText: 'Azure Production' }).first();
    await providerCard.getByRole('button', { name: 'Delete' }).click();
    
    // Dialog appears
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    
    // Cancel delete
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    
    // Verify NOT removed
    await expect(page.getByText('Azure Production')).toBeVisible();
  });

  test('TC-CRUD-004: Provider delete failure is shown to the user', async ({ page }) => {
    let providers = [{ id: 'prov-del-fail', name: 'Provider Delete Failure', type: 'GCP', status: 'Connected' }];
    
    await page.route('**/api/v1/organisations/org-001/providers', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(providers) });
    });

    await page.route('**/api/v1/providers/*', async route => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Internal Server Error' }) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/providers');
    
    // Check initial state
    const providerCard = page.locator('div').filter({ hasText: 'Provider Delete Failure' }).first();
    await providerCard.getByRole('button', { name: 'Delete' }).click();
    
    // Dialog appears and confirm delete
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: 'Delete' }).click();
    
    // Dialog should show error
    await expect(page.getByRole('alert')).toContainText(/unable to delete/i);
    
    // Close dialog
    await dialog.getByRole('button', { name: 'Cancel' }).click();

    // Verify still exists
    await expect(page.getByText('Provider Delete Failure')).toBeVisible();
  });

  test('TC-007: User can create environment', async ({ page }) => {
    let envs: any[] = [];
    
    await page.route('**/api/v1/environments', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envs) });
      } else if (route.request().method() === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}');
        const newEnv = { id: 'env-1', ...body };
        envs.push(newEnv);
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(newEnv) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/environments');
    
    // Open add environment modal
    await page.getByRole('button', { name: /Add Environment/i }).click();
    
    // Fill form
    await page.getByLabel(/Environment Name/i).fill('PROD');
    await page.getByRole('button', { name: /Save Environment/i }).click();
    
    // Check if it appears
    await expect(page.getByText('PROD')).toBeVisible();
  });

  test('TC-CRUD-005: User can update an environment', async ({ page }) => {
    let env = { id: 'env-prod', name: 'PROD', accountsCount: 0 };
    
    await page.route('**/api/v1/environments', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([env]) });
    });

    await page.route('**/api/v1/environments/*', async route => {
      if (route.request().method() === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}');
        env = { ...env, ...body };
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(env) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/environments');
    
    // Click Edit
    const envCard = page.locator('div').filter({ hasText: 'PROD' }).first();
    await envCard.getByRole('button', { name: 'Edit' }).click();
    
    // Change value
    await page.getByLabel(/Environment Name/i).fill('PRODUCTION');
    await page.getByRole('button', { name: /Save Environment/i }).click();
    
    // Verify
    await expect(page.getByText('PRODUCTION')).toBeVisible();
    await expect(page.getByText('PROD', { exact: true })).not.toBeVisible();
  });

  test('TC-CRUD-006: User can delete an environment', async ({ page }) => {
    let envs = [{ id: 'env-del', name: 'TEST-DELETE-ENV', accountsCount: 0 }];
    
    await page.route('**/api/v1/environments', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envs) });
    });

    await page.route('**/api/v1/environments/*', async route => {
      if (route.request().method() === 'DELETE') {
        envs = [];
        await route.fulfill({ status: 204 });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/environments');
    
    const envCard = page.locator('div').filter({ hasText: 'TEST-DELETE-ENV' }).first();
    await envCard.getByRole('button', { name: 'Delete' }).click();
    
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(/delete this environment/i);
    
    await dialog.getByRole('button', { name: 'Delete' }).click();
    
    await expect(page.getByText('TEST-DELETE-ENV')).not.toBeVisible();
  });

  test('TC-CRUD-007: User cannot accidentally delete environment containing accounts', async ({ page }) => {
    const envs = [{ id: 'env-deps', name: 'PROD-WITH-ACCOUNTS', accountsCount: 5 }];
    
    await page.route('**/api/v1/environments', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envs) });
    });

    await page.goto('/environments');
    
    const envCard = page.locator('div').filter({ hasText: 'PROD-WITH-ACCOUNTS' }).first();
    await envCard.getByRole('button', { name: 'Delete' }).click();
    
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(/accounts are associated with this environment/i);
    await expect(dialog.getByRole('button', { name: 'Delete' })).toBeDisabled();
  });

  test('TC-CRUD-010: User can delete an account', async ({ page }) => {
    let accounts = [{ id: 'acc-del', name: 'account-to-delete', provider: 'AWS', environment: 'PROD', status: 'Active', certificatesCount: 0 }];
    
    await page.route('**/api/v1/accounts', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(accounts) });
    });

    await page.route('**/api/v1/accounts/*', async route => {
      if (route.request().method() === 'DELETE') {
        accounts = [];
        await route.fulfill({ status: 204 });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/accounts');
    
    const accRow = page.getByRole('row', { name: /account-to-delete/i });
    await accRow.getByRole('button', { name: 'Delete' }).click();
    
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(/delete account/i);
    
    await dialog.getByRole('button', { name: 'Delete' }).click();
    
    await expect(accRow).not.toBeVisible();
  });

  test('TC-CRUD-011: Account deletion warns about discovered certificates', async ({ page }) => {
    const accounts = [{ id: 'acc-certs', name: 'account-with-certificates', provider: 'AWS', environment: 'PROD', status: 'Active', certificatesCount: 15 }];
    
    await page.route('**/api/v1/accounts', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(accounts) });
    });

    await page.goto('/accounts');
    
    const accRow = page.getByRole('row', { name: /account-with-certificates/i });
    await accRow.getByRole('button', { name: 'Delete' }).click();
    
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(/certificates are associated with this account/i);
    await expect(dialog).toContainText(/certificate records will remain available/i);
  });

  test('TC-CRUD-012: User can create a new scan', async ({ page }) => {
    let scans = [];
    
    await page.route('**/api/v1/scans', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(scans) });
      } else if (route.request().method() === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}');
        const newScan = { id: 'scan-1', status: 'Queued', ...body };
        scans.push(newScan);
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(newScan) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/scans');
    
    await page.getByRole('button', { name: 'New Scan' }).click();
    
    await page.getByLabel('Scan Name').fill('Queued Production Scan');
    await page.getByRole('radio', { name: 'Scan by Cloud Provider' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption('all');

    await page.getByRole('button', { name: 'Review Scan' }).click();
    await page.getByRole('button', { name: 'Start Scan' }).click();
    
    await expect(page.getByRole('row', { name: /Queued Production Scan/i })).toBeVisible();
  });

  test('TC-CRUD-013: User can edit a queued scan before starting', async ({ page }) => {
    let scan = { id: 'scan-q', name: 'Queued Production Scan', status: 'Queued' };
    
    await page.route('**/api/v1/scans', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([scan]) });
    });

    await page.route('**/api/v1/scans/*', async route => {
      if (route.request().method() === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}');
        scan = { ...scan, ...body };
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(scan) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/scans');
    
    const row = page.getByRole('row', { name: /Queued Production Scan/i });
    await row.getByRole('button', { name: 'Edit' }).click();
    
    await page.getByLabel('Scan Name').fill('Updated Production Scan');
    await page.getByRole('radio', { name: 'Scan by Cloud Provider' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption('all');

    await page.getByRole('button', { name: 'Review Scan' }).click();
    await page.getByRole('button', { name: 'Start Scan' }).click();
    
    await expect(page.getByRole('row', { name: /Updated Production Scan/i })).toBeVisible();
  });

  test('TC-CRUD-014: Run Again creates a new scan without modifying the original', async ({ page }) => {
    let scans = [{ id: 'scan-c', name: 'Completed Production Scan', status: 'Completed' }];
    
    await page.route('**/api/v1/scans', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(scans) });
      } else if (route.request().method() === 'POST') {
        const body = JSON.parse(route.request().postData() || '{}');
        const newScan = { id: `scan-${Date.now()}`, status: 'Queued', ...body };
        scans.push(newScan);
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(newScan) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/scans');
    
    const original = page.getByRole('row', { name: /Completed Production Scan/i }).first();
    await original.getByRole('button', { name: 'Run Again' }).click();
    
    await expect(page.getByRole('heading', { name: /Run Scan Again/i })).toBeVisible();

    await page.getByRole('radio', { name: 'Scan by Cloud Provider' }).check();
    await page.getByLabel('Provider', { exact: true }).selectOption('all');

    await page.getByRole('button', { name: 'Review Scan' }).click();
    await page.getByRole('button', { name: 'Start Scan' }).click();
    
    await expect(page.getByRole('row', { name: /Completed Production Scan/i })).toHaveCount(2);
  });

  test('TC-CRUD-015: User can delete a completed scan', async ({ page }) => {
    let scans = [{ id: 'scan-del', name: 'Scan-To-Delete', status: 'Completed' }];
    
    await page.route('**/api/v1/scans', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(scans) });
    });

    await page.route('**/api/v1/scans/*', async route => {
      if (route.request().method() === 'DELETE') {
        scans = [];
        await route.fulfill({ status: 204 });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/scans');
    
    const row = page.getByRole('row', { name: /Scan-To-Delete/i });
    await row.getByRole('button', { name: 'Delete' }).click();
    
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(/delete this scan/i);
    
    await dialog.getByRole('button', { name: 'Delete' }).click();
    
    await expect(row).not.toBeVisible();
  });

  test('TC-CRUD-016: In-progress scan cannot be deleted directly', async ({ page }) => {
    let scans = [{ id: 'scan-act', name: 'Active Production Scan', status: 'In Progress' }];
    
    await page.route('**/api/v1/scans', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(scans) });
    });

    await page.goto('/scans');
    
    const row = page.getByRole('row', { name: /Active Production Scan/i });
    await expect(row.getByRole('button', { name: 'Delete' })).not.toBeVisible();
    await expect(row.getByRole('button', { name: 'Cancel Scan' })).toBeVisible();
  });
});
