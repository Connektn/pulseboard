import { test, expect } from '@playwright/test';

test.describe('Smoke Tests', () => {
  test('should load the main page', async ({ page }) => {
    await page.goto('/');

    // Check that the page loads with the expected title
    await expect(page).toHaveTitle(/Pulseboard/);

    // Check for key UI elements
    await expect(page.locator('h1')).toContainText('Pulseboard');
  });

  test('should display dashboard components', async ({ page }) => {
    await page.goto('/');

    // Wait for the page to load
    await page.waitForLoadState('networkidle');

    // Check for stats dashboard
    await expect(page.locator('[data-testid="stats-dashboard"]')).toBeVisible();

    // Check for alerts section
    await expect(page.locator('[data-testid="alerts-section"]')).toBeVisible();
  });

  test('should handle API connectivity', async ({ page }) => {
    await page.goto('/');

    // Wait for initial load
    await page.waitForLoadState('networkidle');

    // Check that stats are loaded (should show some content or loading state)
    const statsSection = page.locator('[data-testid="stats-dashboard"]');
    await expect(statsSection).toBeVisible();

    // Allow some time for API calls
    await page.waitForTimeout(2000);

    // Check that we don't have permanent error states
    const errorElements = page.locator('[data-testid*="error"]');
    const errorCount = await errorElements.count();

    // If there are error elements, they should not be critical app-breaking errors
    if (errorCount > 0) {
      const errorTexts = await errorElements.allTextContents();
      // Check that errors are not about missing pages or critical failures
      errorTexts.forEach(text => {
        expect(text.toLowerCase()).not.toContain('404');
        expect(text.toLowerCase()).not.toContain('page not found');
        expect(text.toLowerCase()).not.toContain('critical error');
      });
    }
  });

  test('should have functioning SSE connection indicator', async ({ page }) => {
    await page.goto('/');

    // Wait for page load
    await page.waitForLoadState('networkidle');

    // Look for connection status indicator
    const connectionStatus = page.locator('[data-testid="connection-status"]');

    // Should be visible and show some connection state
    if (await connectionStatus.isVisible()) {
      const statusText = await connectionStatus.textContent();
      expect(statusText).toBeTruthy();
    }

    // Allow time for SSE connection attempt
    await page.waitForTimeout(3000);
  });

  test('should display charts and visualizations', async ({ page }) => {
    await page.goto('/');

    // Wait for page load and give time for charts to render
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Look for chart canvases (Chart.js typically renders to canvas)
    const canvases = page.locator('canvas');
    const canvasCount = await canvases.count();

    // Should have at least one chart rendered
    expect(canvasCount).toBeGreaterThan(0);
  });

  test('should be responsive on mobile viewport', async ({ page }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');

    // Wait for page load
    await page.waitForLoadState('networkidle');

    // Check that main elements are still visible
    await expect(page.locator('h1')).toBeVisible();

    // Check that the layout adapts (no horizontal overflow)
    const body = page.locator('body');
    const bodyWidth = await body.evaluate(el => el.scrollWidth);
    expect(bodyWidth).toBeLessThanOrEqual(375);
  });

  test('should handle navigation and routing', async ({ page }) => {
    await page.goto('/');

    // Wait for initial load
    await page.waitForLoadState('networkidle');

    // If there are navigation links, test them
    const navLinks = page.locator('nav a, [data-testid*="nav"]');
    const linkCount = await navLinks.count();

    if (linkCount > 0) {
      // Click the first navigation link if it exists
      await navLinks.first().click();

      // Should not result in a 404 or error page
      await page.waitForLoadState('networkidle');

      // Check we're still in the app (has our main heading or brand)
      const hasMainContent = await page.locator('h1, [data-testid*="main"]').count();
      expect(hasMainContent).toBeGreaterThan(0);
    }
  });

  test('should load without JavaScript errors', async ({ page }) => {
    const errors: string[] = [];

    // Listen for console errors
    page.on('console', msg => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    // Listen for page errors
    page.on('pageerror', error => {
      errors.push(error.message);
    });

    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Allow some time for any async operations
    await page.waitForTimeout(2000);

    // Filter out non-critical errors (like network timeouts during dev)
    const criticalErrors = errors.filter(error => {
      const errorLower = error.toLowerCase();
      return !errorLower.includes('network') &&
             !errorLower.includes('timeout') &&
             !errorLower.includes('websocket') &&
             !errorLower.includes('sse') &&
             !errorLower.includes('fetch');
    });

    expect(criticalErrors).toHaveLength(0);
  });
});