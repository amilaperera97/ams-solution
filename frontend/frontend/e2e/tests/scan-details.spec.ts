import { test, expect } from '@playwright/test';
import * as fs from 'fs';

test.describe('Scan Details Enhancement', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to scans and wait for load
    await page.goto('/scans');
  });

  test('TC-SCAN-001: User can see dummy scans in Scan History', async ({ page }) => {
    await expect(page.getByRole('row', { name: /Production Full Certificate Scan/i })).toBeVisible();
    await expect(page.getByRole('row', { name: /Development Certificate Scan/i })).toBeVisible();
  });

  test('TC-SCAN-002: User can see completed scans', async ({ page }) => {
    await expect(page.getByRole('row', { name: /Production ALB Certificate Scan/i })).toContainText('Completed');
  });

  test('TC-SCAN-003: User can see failed scans', async ({ page }) => {
    await expect(page.getByRole('row', { name: /Failed Production Scan/i })).toContainText('Failed');
  });

  test('TC-SCAN-004: User can see partial-success scans', async ({ page }) => {
    await expect(page.getByRole('row', { name: /Partial Organisation Scan/i })).toContainText('Partial Success');
  });

  test('TC-SCAN-005: User can see in-progress scans', async ({ page }) => {
    await expect(page.getByRole('row', { name: /Current Production Scan/i })).toContainText('In Progress');
  });

  test('TC-SCAN-006 & 007: User can click a completed scan and expanded details are displayed', async ({ page }) => {
    await page.getByRole('row', { name: /Production Full Certificate Scan/i }).click();
    await expect(page.getByRole('heading', { name: /Certificates Discovered/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /Export CSV/i })).toBeVisible();
  });

  test('TC-SCAN-008 & 009: Certificate count and table are displayed', async ({ page }) => {
    await page.getByRole('row', { name: /Production Full Certificate Scan/i }).click();
    await expect(page.getByText('184', { exact: true })).toBeVisible(); // 184 certificates
    await expect(page.getByRole('table').nth(1)).toBeVisible(); // The certificates table
  });

  test('TC-SCAN-010 to 014: Certificate info is displayed', async ({ page }) => {
    await page.getByRole('row', { name: /Production Full Certificate Scan/i }).click();
    
    // Check for row in certificate table
    const certRow = page.getByRole('row', { name: /api.example.com/i }).first();
    await expect(certRow).toBeVisible();
    await expect(certRow).toContainText('Production Account UK');
    await expect(certRow).toContainText('PROD');
    await expect(certRow).toContainText('eu-west-2');
    await expect(certRow).toContainText('ALB');
    await expect(certRow).toContainText('ACTIVE');
  });

  test('TC-SCAN-015: User can open individual certificate details', async ({ page }) => {
    await page.getByRole('row', { name: /Production Full Certificate Scan/i }).click();
    await page.getByRole('row', { name: /api.example.com/i }).first().click();
    
    // modal should be visible
    const modal = page.getByRole('dialog');
    await expect(modal).toBeVisible();
    await expect(modal).toContainText('Certificate Details');
    await expect(modal).toContainText('Amazon RSA 2048 M02');
  });

  test('In-progress scan test: live progress for an in-progress scan', async ({ page }) => {
    await page.getByRole('row', { name: /Current Production Scan/i }).click();
    await expect(page.getByRole('heading', { name: 'IN PROGRESS' })).toBeVisible();
    await expect(page.getByText(/62%/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Cancel Scan/i })).toBeVisible();
  });

  test('Failed scan test: failed scan shows failure reason', async ({ page }) => {
    await page.getByRole('row', { name: /Failed Production Scan/i }).click();
    await expect(page.getByRole('heading', { name: 'FAILED' })).toBeVisible();
    await expect(page.getByText(/Unable to connect to Production Account 2/i)).toBeVisible();
  });

  test('Partial success test: partial scan shows partial results', async ({ page }) => {
    await page.getByRole('row', { name: /Partial Organisation Scan/i }).click();
    await expect(page.getByRole('heading', { name: 'PARTIAL SUCCESS' })).toBeVisible();
    await expect(page.getByText(/6 successful/i)).toBeVisible();
    await expect(page.getByText(/2 failed/i)).toBeVisible();
  });

  test('Export CSV test: user can export certificates from a completed scan as CSV', async ({ page }) => {
    await page.getByRole('row', { name: /Production Full Certificate Scan/i }).click();
    
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: /Export CSV/i }).click();
    const download = await downloadPromise;

    expect(download.suggestedFilename()).toMatch(/\.csv$/);

    const path = await download.path();
    expect(path).toBeTruthy();

    const csv = await fs.promises.readFile(path, 'utf8');

    expect(csv).toContain('certificateId');
    expect(csv).toContain('domain');
    expect(csv).toContain('accountId');
    expect(csv).toContain('expiryDate');
    expect(csv).toContain('api.example.com');
  });

  test('Export CSV test: CSV does not contain authentication secrets', async ({ page }) => {
    await page.getByRole('row', { name: /Production Full Certificate Scan/i }).click();
    
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: /Export CSV/i }).click();
    const download = await downloadPromise;
    const path = await download.path();
    const csv = await fs.promises.readFile(path, 'utf8');

    expect(csv).not.toMatch(/secretKey/i);
    expect(csv).not.toMatch(/accessKey/i);
    expect(csv).not.toMatch(/token/i);
    expect(csv).not.toMatch(/password/i);
  });
});
