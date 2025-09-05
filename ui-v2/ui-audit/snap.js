const fs = require("fs");
(async () => {
  const { chromium } = require("playwright");
  const outDir = "../ui-audit-out";
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto("http://localhost:8011/index.html#%2Fp%2Fwoo-33033", { waitUntil: "load" });
  const shots = [
    { w: 375, h: 2000, l: "mobile" },
    { w: 768, h: 2400, l: "tablet" },
    { w: 1280, h: 4000, l: "desktop" },
  ];
  for (const s of shots) {
    await page.setViewportSize({ width: s.w, height: s.h });
    await page.waitForTimeout(1000);
    const file = `${outDir}/pdp-${s.l}-${s.w}.png`;
    await page.screenshot({ path: file, fullPage: true });
  }
  await browser.close();
})();
