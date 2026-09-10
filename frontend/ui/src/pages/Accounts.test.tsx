import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Accounts from './Accounts';

type Json = Record<string, unknown> | unknown[];

const ORG = { id: 'org-001', name: 'Test Org', setupComplete: true };
const PROVIDERS = [{ id: 'aws-1', type: 'AWS', name: 'AWS Provider' }];
const ENVIRONMENTS = [{ id: 'env-1', name: 'PROD' }];

/**
 * Routes are matched longest-pattern-first, so a test can override a single endpoint
 * without restating the rest of the fixture.
 */
function mockApi(overrides: Record<string, Json | ((init?: RequestInit) => Json)> = {}) {
  const calls: { url: string; method: string; body: any }[] = [];

  const routes: Record<string, Json | ((init?: RequestInit) => Json)> = {
    '/api/v1/organisations/current': ORG,
    '/api/v1/organisations/org-001/providers': PROVIDERS,
    '/api/v1/providers/aws-1/environments': ENVIRONMENTS,
    '/api/v1/environments/env-1/accounts': [],
    '/api/v1/config/cloud-providers': { AWS: { mode: 'MOCK', defaultRegion: 'eu-west-2' } },
    ...overrides,
  };

  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    calls.push({ url, method, body: init?.body ? JSON.parse(init.body as string) : undefined });

    const key = Object.keys(routes)
      .filter(k => url === k || url.startsWith(k))
      .sort((a, b) => b.length - a.length)[0];

    if (key === undefined) {
      return new Response('', { status: 404, statusText: 'Not Found' });
    }
    const route = routes[key];
    const body = typeof route === 'function' ? route(init) : route;
    return new Response(JSON.stringify(body), {
      status: method === 'POST' ? 201 : 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }));

  return calls;
}

function renderAccounts() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/accounts']}>
        <Accounts />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

/** Waits for the provider/environment queries to settle, then opens the form. */
async function openAddForm(user: ReturnType<typeof userEvent.setup>) {
  const addButton = await screen.findByRole('button', { name: 'Add Account' });
  // Stays disabled until the environment query resolves.
  await waitFor(() => expect((addButton as HTMLButtonElement).disabled).toBe(false));
  await user.click(addButton);
  return screen.findByRole('heading', { name: 'Add Account' });
}

async function fillBasics(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.type(screen.getByLabelText('Account Name'), name);
  await user.type(screen.getByLabelText('Account ID'), '123456789012');
}

const savedBody = (calls: { url: string; method: string; body: any }[], method: string) =>
  calls.filter(c => c.method === method).at(-1)?.body;

describe('Accounts form', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends role ARN, external ID and region for an IAM_ROLE account', async () => {
    const calls = mockApi();
    const user = userEvent.setup();
    renderAccounts();
    await openAddForm(user);

    await fillBasics(user, 'IAM Role Account');
    await user.click(screen.getByRole('radio', { name: 'IAM Role' }));
    await user.type(screen.getByLabelText('Role ARN'), 'arn:aws:iam::123456789012:role/CertificateDiscoveryRole');
    await user.type(screen.getByLabelText(/External ID/), 'shared-external-id');
    await user.type(screen.getByLabelText('AWS Region'), 'eu-west-2');

    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    await waitFor(() => expect(savedBody(calls, 'POST')).toBeDefined());
    expect(savedBody(calls, 'POST')).toEqual({
      name: 'IAM Role Account',
      accountId: '123456789012',
      authType: 'IAM_ROLE',
      roleArn: 'arn:aws:iam::123456789012:role/CertificateDiscoveryRole',
      externalId: 'shared-external-id',
      region: 'eu-west-2',
    });
  });

  it('sends access key id, secret access key and region for an ACCESS_KEY account', async () => {
    const calls = mockApi();
    const user = userEvent.setup();
    renderAccounts();
    await openAddForm(user);

    await fillBasics(user, 'Access Key Account');
    await user.click(screen.getByRole('radio', { name: 'Access Key' }));
    await user.type(screen.getByLabelText('Access Key ID'), 'AKIAIOSFODNN7EXAMPLE');
    await user.type(screen.getByLabelText('Secret Access Key'), 'wJalrXUtnFEMI/K7MDENG');
    await user.type(screen.getByLabelText('AWS Region'), 'eu-west-2');

    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    await waitFor(() => expect(savedBody(calls, 'POST')).toBeDefined());
    expect(savedBody(calls, 'POST')).toEqual({
      name: 'Access Key Account',
      accountId: '123456789012',
      authType: 'ACCESS_KEY',
      accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
      secretAccessKey: 'wJalrXUtnFEMI/K7MDENG',
      region: 'eu-west-2',
    });
  });

  it('sends a token and no region for a TOKEN account while AWS is in MOCK mode', async () => {
    const calls = mockApi();
    const user = userEvent.setup();
    renderAccounts();
    await openAddForm(user);

    await fillBasics(user, 'Token Account');
    await user.click(screen.getByRole('radio', { name: 'Token' }));
    await user.type(screen.getByLabelText('Token Value'), 'a-mock-token');

    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    await waitFor(() => expect(savedBody(calls, 'POST')).toBeDefined());
    expect(savedBody(calls, 'POST')).toEqual({
      name: 'Token Account',
      accountId: '123456789012',
      authType: 'TOKEN',
      token: 'a-mock-token',
    });
    // Region is an AWS API concept; a token account never reaches an AWS endpoint.
    expect(screen.queryByLabelText('AWS Region')).toBeNull();
  });

  it('hides Token entirely when AWS is configured for REAL mode', async () => {
    mockApi({ '/api/v1/config/cloud-providers': { AWS: { mode: 'REAL', defaultRegion: 'eu-west-2' } } });
    const user = userEvent.setup();
    renderAccounts();
    await openAddForm(user);

    expect(screen.queryByRole('radio', { name: 'Token' })).toBeNull();
    expect(screen.getByRole('radio', { name: 'IAM Role' })).toBeTruthy();
    expect(screen.getByRole('radio', { name: 'Access Key' })).toBeTruthy();
    expect(screen.getByText(/token authentication is not offered/i)).toBeTruthy();
  });

  it('still offers Token when the provider is in MOCK mode', async () => {
    mockApi();
    const user = userEvent.setup();
    renderAccounts();
    await openAddForm(user);

    expect(screen.getByRole('radio', { name: 'Token' })).toBeTruthy();
  });

  it('requires a region for real AWS and rejects one that is not an AWS region', async () => {
    const calls = mockApi({ '/api/v1/config/cloud-providers': { AWS: { mode: 'REAL' } } });
    const user = userEvent.setup();
    renderAccounts();
    await openAddForm(user);

    await fillBasics(user, 'No Region Account');
    await user.type(screen.getByLabelText('Role ARN'), 'arn:aws:iam::123456789012:role/Role');
    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    expect(await screen.findByText('Region is required for real AWS accounts, e.g. eu-west-2.')).toBeTruthy();
    expect(savedBody(calls, 'POST')).toBeUndefined();

    await user.type(screen.getByLabelText('AWS Region'), 'London');
    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    expect(await screen.findByText('Region must look like an AWS region, e.g. eu-west-2.')).toBeTruthy();
    expect(savedBody(calls, 'POST')).toBeUndefined();
  });

  it('reports a missing and a malformed role ARN', async () => {
    const calls = mockApi();
    const user = userEvent.setup();
    renderAccounts();
    await openAddForm(user);

    await fillBasics(user, 'Bad ARN Account');
    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    expect(await screen.findByText('Role ARN is required when authentication is IAM Role.')).toBeTruthy();

    await user.type(screen.getByLabelText('Role ARN'), 'not-an-arn');
    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    expect(await screen.findByText('Role ARN must look like arn:aws:iam::123456789012:role/RoleName.')).toBeTruthy();
    expect(savedBody(calls, 'POST')).toBeUndefined();
  });

  it('reports a missing secret access key and a malformed access key id', async () => {
    const calls = mockApi();
    const user = userEvent.setup();
    renderAccounts();
    await openAddForm(user);

    await fillBasics(user, 'Bad Key Account');
    await user.click(screen.getByRole('radio', { name: 'Access Key' }));
    await user.type(screen.getByLabelText('Access Key ID'), 'not-a-key');
    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    expect(await screen.findByText('Access key ID must be 20 characters starting with AKIA or ASIA.')).toBeTruthy();
    expect(screen.getByText('Secret access key is required when authentication is Access Key.')).toBeTruthy();
    expect(savedBody(calls, 'POST')).toBeUndefined();
  });

  it('still rejects an account ID that is not 12 digits', async () => {
    const calls = mockApi();
    const user = userEvent.setup();
    renderAccounts();
    await openAddForm(user);

    await user.type(screen.getByLabelText('Account Name'), 'Short ID Account');
    await user.type(screen.getByLabelText('Account ID'), '12345');
    await user.type(screen.getByLabelText('Role ARN'), 'arn:aws:iam::123456789012:role/Role');
    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    expect(await screen.findByText('Account ID must contain exactly 12 digits.')).toBeTruthy();
    expect(savedBody(calls, 'POST')).toBeUndefined();
  });
});

describe('Accounts table', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const account = (over: Record<string, unknown>) => ({
    id: 'acc-1',
    providerId: 'aws-1',
    provider: 'AWS',
    environmentId: 'env-1',
    name: 'QA Account',
    accountId: '123456789012',
    status: 'ACTIVE',
    credentialsConfigured: true,
    ...over,
  });

  it('labels each authentication type correctly, and never calls an ACCESS_KEY account TOKEN', async () => {
    mockApi({
      '/api/v1/environments/env-1/accounts': [
        account({ id: 'acc-key', name: 'Key Account', authType: 'ACCESS_KEY', accessKeyId: '****MPLE', region: 'eu-west-2' }),
        account({ id: 'acc-role', name: 'Role Account', authType: 'IAM_ROLE', roleArnConfigured: true, region: 'us-east-1' }),
        account({ id: 'acc-token', name: 'Token Account', authType: 'TOKEN' }),
      ],
    });
    renderAccounts();

    const keyRow = (await screen.findByText('Key Account')).closest('tr')!;
    expect(within(keyRow).getByText('ACCESS KEY')).toBeTruthy();
    expect(keyRow.textContent).not.toContain('TOKEN');
    expect(within(keyRow).getByText('eu-west-2')).toBeTruthy();

    const roleRow = screen.getByText('Role Account').closest('tr')!;
    expect(within(roleRow).getByText('IAM ROLE')).toBeTruthy();

    const tokenRow = screen.getByText('Token Account').closest('tr')!;
    expect(within(tokenRow).getByText('TOKEN')).toBeTruthy();
  });
});

describe('Accounts edit', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const storedKeyAccount = {
    id: 'acc-key',
    providerId: 'aws-1',
    provider: 'AWS',
    environmentId: 'env-1',
    name: 'Key Account',
    accountId: '123456789012',
    authType: 'ACCESS_KEY',
    region: 'eu-west-2',
    accessKeyId: '****MPLE',
    credentialsConfigured: true,
    status: 'ACTIVE',
  };

  async function openEditForm(user: ReturnType<typeof userEvent.setup>, name: string) {
    const row = (await screen.findByText(name)).closest('tr')!;
    await user.click(within(row).getByRole('button', { name: 'Edit' }));
    return screen.findByRole('heading', { name: 'Edit Account' });
  }

  it('shows no stored secret when editing, only that one is configured', async () => {
    mockApi({ '/api/v1/environments/env-1/accounts': [storedKeyAccount] });
    const user = userEvent.setup();
    renderAccounts();
    await openEditForm(user, 'Key Account');

    expect((screen.getByLabelText('Secret Access Key') as HTMLInputElement).value).toBe('');
    expect((screen.getByLabelText('Access Key ID') as HTMLInputElement).value).toBe('');
    expect(screen.getByText(/Currently \*\*\*\*MPLE/)).toBeTruthy();
    expect(screen.getByText('Already configured. Leave blank to keep it.')).toBeTruthy();
    // The masked id is the only credential the page ever holds.
    expect(document.body.textContent).not.toContain('wJalrXUtnFEMI');
  });

  it('preserves stored credentials by omitting the fields the operator left blank', async () => {
    const calls = mockApi({ '/api/v1/environments/env-1/accounts': [storedKeyAccount] });
    const user = userEvent.setup();
    renderAccounts();
    await openEditForm(user, 'Key Account');

    // Region prefills from the response and is the only thing changed.
    expect((screen.getByLabelText('AWS Region') as HTMLInputElement).value).toBe('eu-west-2');
    await user.clear(screen.getByLabelText('AWS Region'));
    await user.type(screen.getByLabelText('AWS Region'), 'us-east-1');
    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    await waitFor(() => expect(savedBody(calls, 'PUT')).toBeDefined());
    const body = savedBody(calls, 'PUT');
    expect(body).toEqual({
      name: 'Key Account',
      accountId: '123456789012',
      authType: 'ACCESS_KEY',
      region: 'us-east-1',
    });
    expect(body).not.toHaveProperty('secretAccessKey');
    expect(body).not.toHaveProperty('accessKeyId');
    expect(calls.filter(c => c.method === 'PUT')[0].url).toBe('/api/v1/accounts/acc-key');
  });

  it('replaces a secret when the operator does type a new one', async () => {
    const calls = mockApi({ '/api/v1/environments/env-1/accounts': [storedKeyAccount] });
    const user = userEvent.setup();
    renderAccounts();
    await openEditForm(user, 'Key Account');

    await user.type(screen.getByLabelText('Secret Access Key'), 'a-brand-new-secret');
    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    await waitFor(() => expect(savedBody(calls, 'PUT')).toBeDefined());
    expect(savedBody(calls, 'PUT').secretAccessKey).toBe('a-brand-new-secret');
    expect(savedBody(calls, 'PUT')).not.toHaveProperty('accessKeyId');
  });

  it('lets an IAM_ROLE account be saved without resending the role ARN it never received', async () => {
    const calls = mockApi({
      '/api/v1/environments/env-1/accounts': [{
        ...storedKeyAccount,
        id: 'acc-role',
        name: 'Role Account',
        authType: 'IAM_ROLE',
        accessKeyId: undefined,
        roleArnConfigured: true,
        externalIdConfigured: true,
      }],
    });
    const user = userEvent.setup();
    renderAccounts();
    await openEditForm(user, 'Role Account');

    expect((screen.getByLabelText('Role ARN') as HTMLInputElement).value).toBe('');
    await user.clear(screen.getByLabelText('Account Name'));
    await user.type(screen.getByLabelText('Account Name'), 'Renamed Role Account');
    await user.click(screen.getByRole('button', { name: 'Save Account' }));

    await waitFor(() => expect(savedBody(calls, 'PUT')).toBeDefined());
    expect(savedBody(calls, 'PUT')).toEqual({
      name: 'Renamed Role Account',
      accountId: '123456789012',
      authType: 'IAM_ROLE',
      region: 'eu-west-2',
    });
  });
});
