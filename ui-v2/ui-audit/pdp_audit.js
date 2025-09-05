const fs = require('fs');

(async () => {
  const { chromium } = require('playwright');
  const outDir = `${__dirname}/../../ui-v2/.screenshots`;
  fs.mkdirSync(outDir, { recursive: true });

  const ts = new Date().toISOString().replace(/[:.]/g,'-');

  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto('http://localhost:8011/index.html#/p/wc_29951', { waitUntil: 'load' });
  await page.waitForSelector('#pdpAltGrid');

  const sizes = [
    { width: 375, height: 2000, label: 'mobile' },
    { width: 768, height: 2400, label: 'tablet' },
    { width: 1280, height: 3200, label: 'desktop' },
  ];

  for (const s of sizes) {
    await page.setViewportSize({ width: s.width, height: s.height });
    await page.waitForTimeout(400);
    await page.screenshot({ path: `${outDir}/pdp-${s.label}-${s.width}-${ts}.png`, fullPage: true });
  }

  const { AxeBuilder } = require('@axe-core/playwright');
  const axeResults = await new AxeBuilder({ page }).analyze();
  fs.writeFileSync(`${outDir}/axe-pdp-${ts}.json`, JSON.stringify(axeResults, null, 2));

  await browser.close();
})();

