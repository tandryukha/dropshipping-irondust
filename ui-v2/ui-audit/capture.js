const fs = require("fs");
(async () => {
  const { chromium } = require("playwright");
  const outDir = "../ui-audit-out";
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto("http://localhost:8011/index.html", { waitUntil: "load" });
  const sizes = [
    { width: 375, height: 2000, label: "mobile" },
    { width: 768, height: 2400, label: "tablet" },
    { width: 1280, height: 4000, label: "desktop" }
  ];
  for (const s of sizes) {
    await page.setViewportSize({ width: s.width, height: s.height });
    await page.screenshot({ path: `${outDir}/index-${s.label}-${s.width}.png`, fullPage: true });
  }
  await browser.close();
})();
