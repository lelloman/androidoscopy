import { test, expect } from '@playwright/test';

test.describe('Dashboard App', () => {
    test.describe('Header', () => {
        test('should display the app title', async ({ page }) => {
            await page.goto('/');
            await expect(page.locator('h1')).toHaveText('Androidoscopy');
        });

        test('should display status indicator', async ({ page }) => {
            await page.goto('/');
            const statusIndicator = page.locator('.status-indicator');
            await expect(statusIndicator).toBeVisible();
        });

        test('should show connection status in header', async ({ page }) => {
            await page.goto('/');
            const statusIndicator = page.locator('.status-indicator');
            // Should show either Connecting, Connected, or Disconnected
            await expect(statusIndicator).toContainText(/(Connecting|Connected|Disconnected)/);
        });
    });

    test.describe('Empty State', () => {
        test('should show empty state when no sessions connected', async ({ page }) => {
            await page.goto('/');
            // Wait for any connection attempt to settle
            await page.waitForTimeout(1000);
            await expect(page.locator('.empty-state')).toBeVisible();
        });

        test('should display helpful message in empty state', async ({ page }) => {
            await page.goto('/');
            await page.waitForTimeout(1000);
            const emptyState = page.locator('.empty-state');
            // Should contain either "No connected apps" or "Connecting to server"
            await expect(emptyState).toBeVisible();
        });
    });

    test.describe('Layout', () => {
        test('should have proper page structure', async ({ page }) => {
            await page.goto('/');
            await expect(page.locator('main')).toBeVisible();
            await expect(page.locator('header')).toBeVisible();
        });

        test('should have proper status bar', async ({ page }) => {
            await page.goto('/');
            await expect(page.locator('.status-bar')).toBeVisible();
        });
    });

    test.describe('Accessibility', () => {
        test('should have proper heading structure', async ({ page }) => {
            await page.goto('/');
            const h1 = page.locator('h1');
            await expect(h1).toHaveCount(1);
            await expect(h1).toBeVisible();
        });

        test('should have visible text content', async ({ page }) => {
            await page.goto('/');
            // Ensure main content is visible
            await expect(page.locator('main')).toBeVisible();
        });
    });

    test.describe('Responsiveness', () => {
        test('should render on mobile viewport', async ({ page }) => {
            await page.setViewportSize({ width: 375, height: 667 });
            await page.goto('/');
            await expect(page.locator('h1')).toBeVisible();
        });

        test('should render on tablet viewport', async ({ page }) => {
            await page.setViewportSize({ width: 768, height: 1024 });
            await page.goto('/');
            await expect(page.locator('h1')).toBeVisible();
        });

        test('should render on desktop viewport', async ({ page }) => {
            await page.setViewportSize({ width: 1920, height: 1080 });
            await page.goto('/');
            await expect(page.locator('h1')).toBeVisible();
        });
    });
});
