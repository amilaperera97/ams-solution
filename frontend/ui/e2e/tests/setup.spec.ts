import { test, expect } from '@playwright/test';

test.describe('First-time Setup Journey', () => {
  test('TC-001: First-time user can configure organisation', async ({ page }) => {
    page.on('console', msg => console.log('BROWSER LOG:', msg.text()));
    page.on('pageerror', err => console.log('BROWSER ERROR:', err.message));

    let orgState = { id: 'org-001', name: '', setupComplete: false };

    // Intercept the /api/v1/organisations/org-001 to simulate new user
    await page.route('**/api/v1/organisations/org-001', async route => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          headers: { 'Access-Control-Allow-Origin': '*' },
          body: JSON.stringify(orgState),
        });
      } else if (route.request().method() === 'PUT') {
        const body = JSON.parse(route.request().postData() || '{}');
        orgState = { ...orgState, ...body };
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          headers: { 'Access-Control-Allow-Origin': '*' },
          body: JSON.stringify(orgState),
        });
      } else {
        await route.fallback();
      }
    });

    // Mock dashboard stats
    await page.route('**/api/v1/dashboard/stats', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify({
          providers: 0,
          environments: 0,
          accounts: 0,
          certificates: 0,
          health: { expired: 0, critical: 0, expiringSoon: 0, healthy: 0 }
        }),
      });
    });

    await page.goto('/');

    // Should redirect or show setup page
    try {
      await expect(page.getByRole('heading', { name: /Welcome to Certificate Discovery/i })).toBeVisible();
    } catch(e) {
      console.log('PAGE CONTENT:', await page.content());
      throw e;
    }
    await expect(page.getByText(/Setup Progress/i)).toBeVisible();

    // Fill organisation details
    await page.getByLabel(/Organisation Name/i).fill('My Test Org');
    await page.getByRole('button', { name: /Save & Continue/i }).click();

    // Should progress to Dashboard (or next setup step)
    // For simplicity, let's assume if it's minimal setup, it goes to dashboard.
    await expect(page.getByRole('heading', { name: /Dashboard/i })).toBeVisible();
    await expect(page.getByText('My Test Org')).toBeVisible();
  });
});
