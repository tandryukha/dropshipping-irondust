import { test, expect } from '@playwright/test';

// Helper to wait for layout stabilization
async function waitForStableLayout(page) {
  // Wait for network idle and a small animation frame buffer
  await page.waitForLoadState('load');
  await page.waitForTimeout(150);
}

test.describe('index.html responsive rendering', () => {
  test.beforeEach(async ({ page, baseURL }) => {
    await page.goto(baseURL!);
    await waitForStableLayout(page);
  });

  test('desktop layout: has 4-column grid and shows basket rail', async ({ page, isMobile }) => {
    test.skip(isMobile, 'Desktop-only check');

    // Count product grid columns by reading computed style
    const columns = await page.evaluate(() => {
      const grid = document.querySelector('#grid');
      if (!grid) return null;
      const cs = getComputedStyle(grid);
      return cs.gridTemplateColumns.split(' ').length;
    });
    expect(columns).toBeGreaterThanOrEqual(3); // 4 on wide screens, >=3 on smaller desktops

    // Basket panel should be present in DOM (visible hidden by default off-canvas)
    await expect(page.locator('#basketPanel')).toBeVisible();

    // Visual snapshot to protect desktop from regressions
    await expect(page).toHaveScreenshot('desktop-home.png', { fullPage: true, animations: 'disabled' });
  });

  test('mobile layout: single-column grid, basket hidden, mobile controls visible', async ({ page, isMobile }) => {
    test.skip(!isMobile, 'Mobile-only check');

    // Grid should be single column on mobile
    const columns = await page.evaluate(() => {
      const grid = document.querySelector('#grid');
      if (!grid) return null;
      const cs = getComputedStyle(grid);
      return cs.gridTemplateColumns.split(' ').length;
    });
    expect(columns).toBe(1);

    // Basket hidden on mobile via media query
    const basketDisplay = await page.evaluate(() => getComputedStyle(document.querySelector('#basketPanel')!).display);
    expect(basketDisplay).toBe('none');

    // Mobile filters button visible
    await expect(page.locator('#openFilters')).toBeVisible();

    // Visual snapshot to guard mobile layout
    await expect(page).toHaveScreenshot('mobile-home.png', { fullPage: true, animations: 'disabled' });
  });

  test('suggestions dropdown and add-to-basket work (smoke)', async ({ page }) => {
    const q = page.locator('#q');
    await q.fill('protein');
    await page.waitForTimeout(50);
    await expect(page.locator('#sugg')).toBeVisible();

    // Add first item in grid to basket and expect badge to update and basket to open
    const firstAdd = page.locator('#grid .product .add').first();
    await firstAdd.click();
    await expect(page.locator('#cartBadge')).not.toHaveText('0');
    await expect(page.locator('#basketPanel')).toHaveClass(/visible/);
  });

  test('mobile location modal size/insets match Amazon-like sheet', async ({ page, isMobile }) => {
    test.skip(!isMobile, 'Mobile-only check');

    await page.locator('#openLocation').click();
    await page.waitForTimeout(100);

    const rect = await page.locator('.location-modal-content').boundingBox();
    const vp = page.viewportSize()!;
    if (!rect) throw new Error('location modal content not found');

    // Assert exact pixel height from on-screen ruler measurement
    expect(Math.round(rect.height)).toBe(300);

    // Side insets around ~12px with tolerance 8–16px
    const leftInset = rect.x;
    const rightInset = vp.width - (rect.x + rect.width);
    expect(Math.round(leftInset)).toBe(12);
    expect(Math.round(rightInset)).toBe(12);
  });
});


