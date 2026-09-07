import { test, expect } from '@playwright/test';

test.describe('Account Enhancements', () => {
  test.beforeEach(async ({ page }) => {
    let mockAccounts: any[] = [];
    
    // Mock the environments and providers API so they appear in dropdowns
    
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
        const newAcc = { id: `acc-new-${Date.now()}`, ...body, status: 'CONNECTED' };
        mockAccounts.push(newAcc);
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(newAcc) });
      } else {
        await route.fallback();
      }
    });
  });

  test('TC-ACCOUNT-001: User can create an account with Token authentication', async ({ page }) => {
    await page.goto('/accounts');
    await page.getByRole('button', { name: 'Add Account' }).click();

    await page.getByLabel(/Cloud Provider/i).selectOption('AWS');
    await page.getByLabel(/Environment/i).selectOption('PROD');
    await page.getByLabel(/Account Name/i).fill('Token Account');
    await page.getByLabel(/Account ID/i).fill('123456789012');
    
    // Choose Token
    await page.getByLabel('Token', { exact: true }).check();
    await page.getByLabel('Token Value').fill('super-secret-token');

    await page.getByRole('button', { name: 'Save Account' }).click();
    
    await expect(page.getByRole('row', { name: /Token Account/i })).toBeVisible();
    await expect(page.getByRole('row', { name: /Token Account/i })).toContainText('TOKEN');
  });

  test('TC-ACCOUNT-002: User can create an account with IAM Role authentication', async ({ page }) => {
    await page.goto('/accounts');
    await page.getByRole('button', { name: 'Add Account' }).click();

    await page.getByLabel(/Cloud Provider/i).selectOption('AWS');
    await page.getByLabel(/Environment/i).selectOption('PROD');
    await page.getByLabel(/Account Name/i).fill('IAM Role Account');
    await page.getByLabel(/Account ID/i).fill('123456789012');
    
    // Choose IAM Role
    await page.getByLabel('IAM Role').check();
    await page.getByLabel('Role ARN').fill('arn:aws:iam::123456789012:role/CertificateDiscoveryRole');

    await page.getByRole('button', { name: 'Save Account' }).click();
    
    await expect(page.getByRole('row', { name: /IAM Role Account/i })).toBeVisible();
    await expect(page.getByRole('row', { name: /IAM Role Account/i })).toContainText('IAM ROLE');
  });

  test('TC-ACCOUNT-003: Account ID is mandatory', async ({ page }) => {
    await page.goto('/accounts');
    await page.getByRole('button', { name: 'Add Account' }).click();

    await page.getByLabel(/Account Name/i).fill('No ID Account');
    await page.getByRole('button', { name: 'Save Account' }).click();

    // Check HTML5 validation or custom validation message
    const accountIdInput = page.getByLabel(/Account ID/i);
    const isInvalid = await accountIdInput.evaluate((el: HTMLInputElement) => !el.validity.valid);
    expect(isInvalid).toBe(true);
  });

  test('TC-ACCOUNT-004: Invalid Account ID is rejected', async ({ page }) => {
    await page.goto('/accounts');
    await page.getByRole('button', { name: 'Add Account' }).click();

    await page.getByLabel(/Account Name/i).fill('Invalid ID Account');
    await page.getByLabel(/Account ID/i).fill('12345');
    await page.getByLabel('Token', { exact: true }).check();
    await page.getByLabel('Token Value').fill('token');
    
    await page.getByRole('button', { name: 'Save Account' }).click();

    await expect(page.getByText('Account ID must contain exactly 12 digits.')).toBeVisible();
  });

  test('TC-ACCOUNT-005: Account ID accepts exactly 12 digits', async ({ page }) => {
    await page.goto('/accounts');
    await page.getByRole('button', { name: 'Add Account' }).click();

    await page.getByLabel(/Account Name/i).fill('Valid ID Account');
    await page.getByLabel(/Account ID/i).fill('123456789012');
    await page.getByLabel('Token', { exact: true }).check();
    await page.getByLabel('Token Value').fill('token');
    
    await page.getByRole('button', { name: 'Save Account' }).click();
    
    await expect(page.getByText('Account ID must contain exactly 12 digits.')).not.toBeVisible();
  });

  test('TC-ACCOUNT-006 & 009: Token field is shown when Token is selected, IAM Role is hidden', async ({ page }) => {
    await page.goto('/accounts');
    await page.getByRole('button', { name: 'Add Account' }).click();

    await page.getByLabel('Token', { exact: true }).check();
    await expect(page.getByLabel('Token Value')).toBeVisible();
    await expect(page.getByLabel('Role ARN')).not.toBeVisible();
  });

  test('TC-ACCOUNT-007 & 008: IAM Role field is shown when IAM Role is selected, Token is hidden', async ({ page }) => {
    await page.goto('/accounts');
    await page.getByRole('button', { name: 'Add Account' }).click();

    await page.getByLabel('IAM Role').check();
    await expect(page.getByLabel('Role ARN')).toBeVisible();
    await expect(page.getByLabel('Token Value')).not.toBeVisible();
  });

  test('TC-ACCOUNT-010: User can test Token connection', async ({ page }) => {
    await page.route('**/api/v1/accounts/*/test-connection', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, message: 'Connection successful' }) });
    });

    await page.goto('/accounts');
    await page.getByRole('button', { name: 'Add Account' }).click();

    await page.getByLabel(/Account ID/i).fill('123456789012');
    await page.getByLabel('Token', { exact: true }).check();
    await page.getByLabel('Token Value').fill('test-token');

    await page.getByRole('button', { name: 'Test Connection' }).click();
    await expect(page.getByText(/Connection successful/i)).toBeVisible();
  });

  test('TC-ACCOUNT-011: User can test IAM Role connection', async ({ page }) => {
    await page.route('**/api/v1/accounts/*/test-connection', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, message: 'Connection successful' }) });
    });

    await page.goto('/accounts');
    await page.getByRole('button', { name: 'Add Account' }).click();

    await page.getByLabel(/Account ID/i).fill('123456789012');
    await page.getByLabel('IAM Role').check();
    await page.getByLabel('Role ARN').fill('arn:aws:iam::123456789012:role/Role');

    await page.getByRole('button', { name: 'Test Connection' }).click();
    await expect(page.getByText(/Connection successful/i)).toBeVisible();
  });

  test('TC-ACCOUNT-012: User can edit authentication type', async ({ page }) => {
    let account = { 
      id: 'acc-edit', name: 'Edit Auth Account', provider: 'AWS', environmentId: 'env-1', 
      accountId: '123456789012', authType: 'TOKEN' 
    };
    
    await page.route('**/api/v1/accounts', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([account]) });
    });

    await page.route('**/api/v1/accounts/*', async route => {
      if (route.request().method() === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}');
        account = { ...account, ...body };
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(account) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/accounts');
    
    const accRow = page.getByRole('row', { name: /Edit Auth Account/i });
    await expect(accRow).toContainText('TOKEN');

    await accRow.getByRole('button', { name: 'Edit', exact: true }).click();
    
    await page.getByLabel('IAM Role').check();
    await page.getByLabel('Role ARN').fill('arn:aws:iam::123456789012:role/Role');
    
    await page.getByRole('button', { name: 'Save Account' }).click();
    
    await expect(page.getByRole('row', { name: /Edit Auth Account/i })).toContainText('IAM ROLE');
  });

  test('TC-ACCOUNT-013: User can edit Account ID', async ({ page }) => {
    let account = { 
      id: 'acc-edit-id', name: 'Edit ID Account', provider: 'AWS', environmentId: 'env-1', 
      accountId: '123456789012', authType: 'TOKEN' 
    };
    
    await page.route('**/api/v1/accounts', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([account]) });
    });

    await page.route('**/api/v1/accounts/*', async route => {
      if (route.request().method() === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}');
        account = { ...account, ...body };
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(account) });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/accounts');
    
    const accRow = page.getByRole('row', { name: /Edit ID Account/i });
    await accRow.getByRole('button', { name: 'Edit', exact: true }).click();
    
    await expect(page.getByLabel(/Account ID/i)).toHaveValue('123456789012');
    await page.getByLabel(/Account ID/i).fill('987654321098');
    
    await page.getByRole('button', { name: 'Save Account' }).click();
    
    // Test passes if we successfully save and modal closes (meaning it accepted the new ID)
    await expect(page.getByRole('dialog')).not.toBeVisible();
  });

  test('TC-ACCOUNT-SECURITY: No token leaked in table', async ({ page }) => {
    const account = { 
      id: 'acc-leak', name: 'Leak Test Account', provider: 'AWS', environmentId: 'env-1', 
      accountId: '123456789012', authType: 'TOKEN', token: 'super-secret-token' 
    };
    
    await page.route('**/api/v1/accounts', async route => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([account]) });
    });

    await page.goto('/accounts');
    
    const bodyText = await page.textContent('body');
    expect(bodyText).not.toContain('super-secret-token');
    await expect(page.getByRole('row', { name: /Leak Test Account/i })).toContainText('TOKEN');
  });
});
