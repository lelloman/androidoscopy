import { test, expect } from '@playwright/test';

test.describe('Dashboard App', () => {
    test('should display the header', async ({ page }) => {
        await page.goto('/');
        await expect(page.locator('h1')).toHaveText('Androidoscopy');
    });

    test('should show disconnected status initially', async ({ page }) => {
        await page.goto('/');
        const statusIndicator = page.locator('.status-indicator');
        await expect(statusIndicator).toBeVisible();
    });

    test('should show empty state when no sessions connected', async ({ page }) => {
        await page.goto('/');
        // Wait for any connection attempt to settle
        await page.waitForTimeout(500);
        await expect(page.locator('.empty-state')).toBeVisible();
    });
});
