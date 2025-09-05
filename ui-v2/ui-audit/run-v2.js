const fs = require('fs');
const path = require('path');

(async () => {
  const { chromium } = require('playwright');
  const outDir = path.resolve(__dirname, '../../ui-v2/.screenshots');
  fs.mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto('http://localhost:8011/index-v2.html#/p/wc_29951', { waitUntil: 'load' });

  const sizes = [
    { width: 375, height: 2000, label: 'mobile' },
    { width: 768, height: 2400, label: 'tablet' },
    { width: 1280, height: 4000, label: 'desktop' }
  ];

  for (const s of sizes) {
    await page.setViewportSize({ width: s.width, height: s.height });
    await page.screenshot({ path: path.join(outDir, `pdp-v2-${s.label}-${s.width}.png`), fullPage: true });
  }

  const { AxeBuilder } = require('@axe-core/playwright');
  const axeResults = await new AxeBuilder({ page }).analyze();
  fs.writeFileSync(path.join(outDir, 'axe-v2.json'), JSON.stringify(axeResults, null, 2));

  await browser.close();
})().catch(err => {
  console.error(err);
  process.exit(1);
});


