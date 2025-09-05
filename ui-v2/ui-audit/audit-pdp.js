const fs = require('fs');
(async () => {
  const { chromium } = require('playwright');
  const { AxeBuilder } = require('@axe-core/playwright');

  const outDir = __dirname + '/../../ui-v2/.screenshots';
  fs.mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  // PDP route to evaluate Alternatives/More picks
  await page.goto('http://localhost:8011/index.html#/p/wc_29951', { waitUntil: 'load' });

  const sizes = [
    { width: 375, height: 2000, label: 'mobile' },
    { width: 768, height: 2400, label: 'tablet' },
    { width: 1280, height: 4000, label: 'desktop' },
  ];

  for (const s of sizes) {
    await page.setViewportSize({ width: s.width, height: s.height });
    await page.screenshot({ path: `${outDir}/pdp-${s.label}-${s.width}.png`, fullPage: true });
  }

  const axeResults = await new AxeBuilder({ page }).analyze();
  fs.writeFileSync(`${outDir}/axe-index.json`, JSON.stringify(axeResults, null, 2));
  await browser.close();
})();


