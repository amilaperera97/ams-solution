import { test, expect } from '@playwright/test';

test.describe('Certificates Inventory Journey', () => {
  test.beforeEach(async ({ page }) => {
    // Mock the endpoints so we don't depend on WireMock running for Playwright tests
    await page.route('**/api/v1/organisations/org-001', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify({ id: 'org-001', name: 'Test Org', setupComplete: true }),
      });
    });

    await page.route('**/api/v1/certificates*', async route => {
      // Return 3 mock certificates (AWS Healthy, Azure Expired, GCP Critical)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify([
          { id: '1', domain: 'aws.example.com', provider: 'AWS', environment: 'PROD', service: 'ALB', status: 'Healthy', expiresAt: '2027-01-01' },
          { id: '2', domain: 'azure.example.com', provider: 'Azure', environment: 'DEV', service: 'App Service', status: 'Expired', expiresAt: '2023-01-01' },
          { id: '3', domain: 'gcp.example.com', provider: 'GCP', environment: 'QA', service: 'Load Balancer', status: 'Critical', expiresAt: '2024-01-01' },
        ]),
      });
    });
  });

  test('TC-070: View all certificates', async ({ page }) => {
    await page.goto('/certificates');
    
    // Check if table renders all 3 certs
    await expect(page.getByText('aws.example.com')).toBeVisible();
    await expect(page.getByText('azure.example.com')).toBeVisible();
    await expect(page.getByText('gcp.example.com')).toBeVisible();
  });

  test('TC-071: Filter by AWS', async ({ page }) => {
    await page.goto('/certificates');
    
    // Select Provider AWS
    await page.locator('select').first().selectOption('AWS');
    
    // Check URL reflects filter
    await expect(page).toHaveURL(/.*provider=AWS/);

    // Only AWS cert should be visible
    await expect(page.getByText('aws.example.com')).toBeVisible();
    await expect(page.getByText('azure.example.com')).not.toBeVisible();
    await expect(page.getByText('gcp.example.com')).not.toBeVisible();
  });

  test('TC-079: Filter by certificate status', async ({ page }) => {
    await page.goto('/certificates');
    
    // Select Status Expired
    await page.locator('select').nth(1).selectOption('Expired');
    
    // Check URL reflects filter
    await expect(page).toHaveURL(/.*status=Expired/);

    // Only Azure cert should be visible
    await expect(page.getByText('azure.example.com')).toBeVisible();
    await expect(page.getByText('aws.example.com')).not.toBeVisible();
  });
});
