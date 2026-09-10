import { test, expect, type Page } from '@playwright/test';

const ORG = { id: 'org-001', name: 'Test Org', setupComplete: true };
const PROVIDERS = [{ id: 'aws-1', type: 'AWS', name: 'AWS Provider' }];
const ENVIRONMENTS = [{ id: 'env-1', name: 'PROD' }];

type Mode = 'MOCK' | 'REAL';

/**
 * Stubs every endpoint the Accounts page reads. `accounts` is mutated by POST/PUT so a
 * test can assert on what the table shows after a save, and the saved payloads are
 * captured so a test can assert what was - and was not - sent.
 */
async function mockBackend(page: Page, options: { accounts?: any[]; mode?: Mode } = {}) {
  const accounts: any[] = options.accounts ? [...options.accounts] : [];
  const saved: { method: string; body: any }[] = [];

  const json = (body: unknown, status = 200) => ({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });

  await page.route('**/api/v1/organisations/current', route => route.fulfill(json(ORG)));
  await page.route('**/api/v1/organisations/org-001/providers', route => route.fulfill(json(PROVIDERS)));
  await page.route('**/api/v1/providers/aws-1/environments', route => route.fulfill(json(ENVIRONMENTS)));
  await page.route('**/api/v1/config/cloud-providers', route =>
    route.fulfill(json({ AWS: { mode: options.mode ?? 'MOCK', defaultRegion: 'eu-west-2' } })));

  await page.route('**/api/v1/environments/env-1/accounts', async route => {
    if (route.request().method() === 'POST') {
      const body = JSON.parse(route.request().postData() || '{}');
      saved.push({ method: 'POST', body });
      const created = { id: `acc-${accounts.length + 1}`, providerId: 'aws-1', provider: 'AWS',
                        environmentId: 'env-1', status: 'ACTIVE', credentialsConfigured: true, ...body };
      // Secrets are not echoed back, exactly as the real API behaves.
      delete created.token;
      delete created.secretAccessKey;
      delete created.roleArn;
      delete created.externalId;
      accounts.push(created);
      await route.fulfill(json(created, 201));
    } else {
      await route.fulfill(json(accounts));
    }
  });

  await page.route('**/api/v1/accounts/*/test-connection', route =>
    route.fulfill(json({ status: 'CONNECTED', provider: 'AWS', accountId: '123456789012',
      message: 'Authenticated as arn:aws:sts::123456789012:assumed-role/CertRole/certplatform in eu-west-2' })));

  await page.route('**/api/v1/accounts/*', async route => {
    if (route.request().method() !== 'PUT') return route.fallback();
    const body = JSON.parse(route.request().postData() || '{}');
    saved.push({ method: 'PUT', body });
    const id = new URL(route.request().url()).pathname.split('/').pop();
    const index = accounts.findIndex(a => a.id === id);
    const updated = { ...accounts[index], ...body };
    delete updated.token;
    delete updated.secretAccessKey;
    delete updated.roleArn;
    delete updated.externalId;
    accounts[index] = updated;
    await route.fulfill(json(updated));
  });

  return saved;
}

async function openAddForm(page: Page) {
  await page.goto('/accounts');
  const add = page.getByRole('button', { name: 'Add Account' });
  await expect(add).toBeEnabled();
  await add.click();
  await expect(page.getByRole('heading', { name: 'Add Account' })).toBeVisible();
}

test.describe('Account Enhancements', () => {
  test('TC-ACCOUNT-001: User can create an account with Token authentication', async ({ page }) => {
    await mockBackend(page);
    await openAddForm(page);

    await page.getByLabel('Account Name').fill('Token Account');
    await page.getByLabel('Account ID').fill('123456789012');
    await page.getByRole('radio', { name: 'Token' }).check();
    await page.getByLabel('Token Value').fill('super-secret-token');

    await page.getByRole('button', { name: 'Save Account' }).click();

    await expect(page.getByRole('row', { name: /Token Account/i })).toContainText('TOKEN');
  });

  test('TC-ACCOUNT-002: User can create an account with IAM Role authentication', async ({ page }) => {
    const saved = await mockBackend(page);
    await openAddForm(page);

    await page.getByLabel('Account Name').fill('IAM Role Account');
    await page.getByLabel('Account ID').fill('123456789012');
    await page.getByRole('radio', { name: 'IAM Role' }).check();
    await page.getByLabel('Role ARN').fill('arn:aws:iam::123456789012:role/CertificateDiscoveryRole');
    await page.getByLabel('AWS Region').fill('eu-west-2');

    await page.getByRole('button', { name: 'Save Account' }).click();

    const row = page.getByRole('row', { name: /IAM Role Account/i });
    await expect(row).toContainText('IAM ROLE');
    await expect(row).toContainText('eu-west-2');
    expect(saved.at(-1)?.body).toMatchObject({ authType: 'IAM_ROLE', region: 'eu-west-2' });
  });

  test('TC-ACCOUNT-002b: User can create an account with Access Key authentication', async ({ page }) => {
    const saved = await mockBackend(page);
    await openAddForm(page);

    await page.getByLabel('Account Name').fill('Access Key Account');
    await page.getByLabel('Account ID').fill('123456789012');
    await page.getByRole('radio', { name: 'Access Key' }).check();
    await page.getByLabel('Access Key ID').fill('AKIAIOSFODNN7EXAMPLE');
    await page.getByLabel('Secret Access Key').fill('wJalrXUtnFEMI/K7MDENG');
    await page.getByLabel('AWS Region').fill('eu-west-2');

    await page.getByRole('button', { name: 'Save Account' }).click();

    const row = page.getByRole('row', { name: /Access Key Account/i });
    // The regression this pins: an ACCESS_KEY account used to be labelled TOKEN.
    await expect(row).toContainText('ACCESS KEY');
    await expect(row).not.toContainText('TOKEN');
    expect(saved.at(-1)?.body).toMatchObject({
      authType: 'ACCESS_KEY',
      accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
      secretAccessKey: 'wJalrXUtnFEMI/K7MDENG',
      region: 'eu-west-2',
    });
  });

  test('TC-ACCOUNT-003: Account ID is mandatory', async ({ page }) => {
    await mockBackend(page);
    await openAddForm(page);

    await page.getByLabel('Account Name').fill('No ID Account');
    await page.getByRole('button', { name: 'Save Account' }).click();

    const accountIdInput = page.getByLabel('Account ID');
    const isInvalid = await accountIdInput.evaluate((el: HTMLInputElement) => !el.validity.valid);
    expect(isInvalid).toBe(true);
  });

  test('TC-ACCOUNT-004: Invalid Account ID is rejected', async ({ page }) => {
    await mockBackend(page);
    await openAddForm(page);

    await page.getByLabel('Account Name').fill('Invalid ID Account');
    await page.getByLabel('Account ID').fill('12345');
    await page.getByLabel('Role ARN').fill('arn:aws:iam::123456789012:role/Role');

    await page.getByRole('button', { name: 'Save Account' }).click();

    await expect(page.getByText('Account ID must contain exactly 12 digits.')).toBeVisible();
  });

  test('TC-ACCOUNT-005: Account ID accepts exactly 12 digits', async ({ page }) => {
    await mockBackend(page);
    await openAddForm(page);

    await page.getByLabel('Account Name').fill('Valid ID Account');
    await page.getByLabel('Account ID').fill('123456789012');
    await page.getByLabel('Role ARN').fill('arn:aws:iam::123456789012:role/Role');

    await page.getByRole('button', { name: 'Save Account' }).click();

    await expect(page.getByText('Account ID must contain exactly 12 digits.')).not.toBeVisible();
  });

  test('TC-ACCOUNT-006 & 009: Token field is shown when Token is selected, other credentials are hidden', async ({ page }) => {
    await mockBackend(page);
    await openAddForm(page);

    await page.getByRole('radio', { name: 'Token' }).check();
    await expect(page.getByLabel('Token Value')).toBeVisible();
    await expect(page.getByLabel('Role ARN')).not.toBeVisible();
    await expect(page.getByLabel('Access Key ID')).not.toBeVisible();
    // Region is an AWS API concept and has no meaning for a bearer token.
    await expect(page.getByLabel('AWS Region')).not.toBeVisible();
  });

  test('TC-ACCOUNT-007 & 008: IAM Role fields are shown when IAM Role is selected', async ({ page }) => {
    await mockBackend(page);
    await openAddForm(page);

    await page.getByRole('radio', { name: 'IAM Role' }).check();
    await expect(page.getByLabel('Role ARN')).toBeVisible();
    await expect(page.getByLabel(/External ID/)).toBeVisible();
    await expect(page.getByLabel('AWS Region')).toBeVisible();
    await expect(page.getByLabel('Token Value')).not.toBeVisible();
    await expect(page.getByLabel('Secret Access Key')).not.toBeVisible();
  });

  test('TC-ACCOUNT-009b: Access Key fields are shown when Access Key is selected', async ({ page }) => {
    await mockBackend(page);
    await openAddForm(page);

    await page.getByRole('radio', { name: 'Access Key' }).check();
    await expect(page.getByLabel('Access Key ID')).toBeVisible();
    await expect(page.getByLabel('Secret Access Key')).toBeVisible();
    await expect(page.getByLabel('AWS Region')).toBeVisible();
    await expect(page.getByLabel('Role ARN')).not.toBeVisible();
  });

  test('TC-ACCOUNT-010: Test Connection is unavailable until the account is saved', async ({ page }) => {
    await mockBackend(page);
    await openAddForm(page);

    await page.getByLabel('Account ID').fill('123456789012');
    await page.getByRole('radio', { name: 'Token' }).check();
    await page.getByLabel('Token Value').fill('test-token');

    // The endpoint resolves the account by its internal id and reads the stored
    // credentials, so there is nothing to test before the first save.
    await expect(page.getByRole('button', { name: 'Test Connection' })).toBeDisabled();
    await expect(page.getByText(/Save the account first/i)).toBeVisible();
  });

  test('TC-ACCOUNT-011: User can test the connection of a stored account', async ({ page }) => {
    await mockBackend(page, {
      accounts: [{
        id: 'acc-test-conn', name: 'Testable Account', providerId: 'aws-1', provider: 'AWS',
        environmentId: 'env-1', accountId: '123456789012', authType: 'IAM_ROLE',
        region: 'eu-west-2', roleArnConfigured: true, credentialsConfigured: true, status: 'ACTIVE',
      }],
    });

    await page.goto('/accounts');
    await page.getByRole('row', { name: /Testable Account/i })
      .getByRole('button', { name: 'Edit', exact: true }).click();

    await page.getByRole('button', { name: 'Test Connection' }).click();
    await expect(page.getByText(/Connection successful/i)).toBeVisible();
  });

  test('TC-ACCOUNT-011b: A rejected credential is reported as a failure, with the reason', async ({ page }) => {
    await mockBackend(page, {
      accounts: [{
        id: 'acc-bad-conn', name: 'Rejected Account', providerId: 'aws-1', provider: 'AWS',
        environmentId: 'env-1', accountId: '123456789012', authType: 'ACCESS_KEY',
        region: 'eu-west-2', accessKeyId: '****MPLE', credentialsConfigured: true, status: 'ACTIVE',
      }],
    });

    // AWS refusing the credentials is a 200 carrying status FAILED, not an HTTP error.
    await page.route('**/api/v1/accounts/acc-bad-conn/test-connection', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'FAILED', provider: 'AWS', accountId: '123456789012',
        message: 'AWS rejected the credentials: The security token included in the request is invalid.',
      }),
    }));

    await page.goto('/accounts');
    await page.getByRole('row', { name: /Rejected Account/i })
      .getByRole('button', { name: 'Edit', exact: true }).click();

    await page.getByRole('button', { name: 'Test Connection' }).click();

    await expect(page.getByText(/Connection failed/i)).toBeVisible();
    await expect(page.getByText(/security token included in the request is invalid/i)).toBeVisible();
  });

  test('TC-ACCOUNT-010b: A rejected save shows the reason the API gave', async ({ page }) => {
    await mockBackend(page, { mode: 'REAL' });
    // Whatever the client-side rules let through, the backend has the last word -
    // and its message is the only thing that says which rule was broken.
    await page.route('**/api/v1/environments/env-1/accounts', route =>
      route.request().method() === 'POST'
        ? route.fulfill({
            status: 400,
            contentType: 'text/plain',
            body: 'Refusing to store credentials for a REAL provider without encryption. '
                + 'Set CERTPLATFORM_SECRET_KEY and restart.',
          })
        : route.fallback());

    await openAddForm(page);
    await page.getByLabel('Account Name').fill('Doomed Account');
    await page.getByLabel('Account ID').fill('123456789012');
    await page.getByRole('radio', { name: 'Access Key' }).check();
    await page.getByLabel('Access Key ID').fill('AKIAIOSFODNN7EXAMPLE');
    await page.getByLabel('Secret Access Key').fill('wJalrXUtnFEMI/K7MDENG');
    await page.getByLabel('AWS Region').fill('eu-west-2');

    await page.getByRole('button', { name: 'Save Account' }).click();

    await expect(page.getByText(/Account not saved/i)).toBeVisible();
    await expect(page.getByText(/CERTPLATFORM_SECRET_KEY/i)).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Add Account' })).toBeVisible();
  });

  test('TC-ACCOUNT-012: User can edit authentication type', async ({ page }) => {
    const saved = await mockBackend(page, {
      accounts: [{
        id: 'acc-edit', name: 'Edit Auth Account', providerId: 'aws-1', provider: 'AWS',
        environmentId: 'env-1', accountId: '123456789012', authType: 'TOKEN',
        credentialsConfigured: true, status: 'ACTIVE',
      }],
    });

    await page.goto('/accounts');
    const accRow = page.getByRole('row', { name: /Edit Auth Account/i });
    await expect(accRow).toContainText('TOKEN');

    await accRow.getByRole('button', { name: 'Edit', exact: true }).click();

    await page.getByRole('radio', { name: 'IAM Role' }).check();
    await page.getByLabel('Role ARN').fill('arn:aws:iam::123456789012:role/Role');
    await page.getByLabel('AWS Region').fill('eu-west-2');

    await page.getByRole('button', { name: 'Save Account' }).click();

    await expect(page.getByRole('row', { name: /Edit Auth Account/i })).toContainText('IAM ROLE');
    expect(saved.at(-1)).toMatchObject({ method: 'PUT' });
  });

  test('TC-ACCOUNT-013: User can edit Account ID', async ({ page }) => {
    await mockBackend(page, {
      accounts: [{
        id: 'acc-edit-id', name: 'Edit ID Account', providerId: 'aws-1', provider: 'AWS',
        environmentId: 'env-1', accountId: '123456789012', authType: 'TOKEN',
        credentialsConfigured: true, status: 'ACTIVE',
      }],
    });

    await page.goto('/accounts');
    const accRow = page.getByRole('row', { name: /Edit ID Account/i });
    await accRow.getByRole('button', { name: 'Edit', exact: true }).click();

    await expect(page.getByLabel('Account ID')).toHaveValue('123456789012');
    await page.getByLabel('Account ID').fill('987654321098');

    await page.getByRole('button', { name: 'Save Account' }).click();

    await expect(page.getByRole('heading', { name: 'Edit Account' })).not.toBeVisible();
  });

  test('TC-ACCOUNT-014: Editing shows no stored secret and keeps it when left blank', async ({ page }) => {
    const saved = await mockBackend(page, {
      accounts: [{
        id: 'acc-key', name: 'Key Account', providerId: 'aws-1', provider: 'AWS',
        environmentId: 'env-1', accountId: '123456789012', authType: 'ACCESS_KEY',
        region: 'eu-west-2', accessKeyId: '****MPLE', credentialsConfigured: true, status: 'ACTIVE',
      }],
    });

    await page.goto('/accounts');
    await page.getByRole('row', { name: /Key Account/i })
              .getByRole('button', { name: 'Edit', exact: true }).click();

    await expect(page.getByLabel('Secret Access Key')).toHaveValue('');
    await expect(page.getByLabel('Access Key ID')).toHaveValue('');
    await expect(page.getByText(/Currently \*\*\*\*MPLE/)).toBeVisible();
    await expect(page.getByLabel('AWS Region')).toHaveValue('eu-west-2');

    await page.getByLabel('AWS Region').fill('us-east-1');
    await page.getByRole('button', { name: 'Save Account' }).click();

    await expect(page.getByRole('heading', { name: 'Edit Account' })).not.toBeVisible();
    const body = saved.at(-1)?.body;
    expect(body).toMatchObject({ authType: 'ACCESS_KEY', region: 'us-east-1' });
    expect(body).not.toHaveProperty('secretAccessKey');
    expect(body).not.toHaveProperty('accessKeyId');
  });

  test('TC-ACCOUNT-015: Token is not offered when AWS is in REAL mode', async ({ page }) => {
    await mockBackend(page, { mode: 'REAL' });
    await openAddForm(page);

    await expect(page.getByRole('radio', { name: 'Token' })).toHaveCount(0);
    await expect(page.getByRole('radio', { name: 'IAM Role' })).toBeVisible();
    await expect(page.getByRole('radio', { name: 'Access Key' })).toBeVisible();
    await expect(page.getByText(/token authentication is not offered/i)).toBeVisible();
  });

  test('TC-ACCOUNT-016: Real AWS requires a region', async ({ page }) => {
    const saved = await mockBackend(page, { mode: 'REAL' });
    await openAddForm(page);

    await page.getByLabel('Account Name').fill('No Region Account');
    await page.getByLabel('Account ID').fill('123456789012');
    await page.getByLabel('Role ARN').fill('arn:aws:iam::123456789012:role/Role');

    await page.getByRole('button', { name: 'Save Account' }).click();

    await expect(page.getByText('Region is required for real AWS accounts, e.g. eu-west-2.')).toBeVisible();
    expect(saved).toHaveLength(0);
  });

  test('TC-ACCOUNT-SECURITY: No token leaked in table', async ({ page }) => {
    await mockBackend(page, {
      accounts: [{
        id: 'acc-leak', name: 'Leak Test Account', providerId: 'aws-1', provider: 'AWS',
        environmentId: 'env-1', accountId: '123456789012', authType: 'TOKEN',
        credentialsConfigured: true, status: 'ACTIVE',
      }],
    });

    await page.goto('/accounts');

    const bodyText = await page.textContent('body');
    expect(bodyText).not.toContain('super-secret-token');
    await expect(page.getByRole('row', { name: /Leak Test Account/i })).toContainText('TOKEN');
  });
});
