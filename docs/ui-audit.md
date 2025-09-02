## Headless UI Audit (screenshots + accessibility)

Use this to quickly evaluate `ui-v2` after frontend changes. It serves the UI locally, takes screenshots at common breakpoints, and generates accessibility reports (axe-core + pa11y). Lighthouse is optional.

### Prerequisites
- Node.js 18+ and `npx`
- Python 3 (for the static server)

### 1) Start the local UI server
Run this in a separate terminal:

```bash
./ui-v2/serve.sh
```

This serves `http://localhost:8011/index.html`.

### 2) One-time setup for the audit workspace

```bash
mkdir -p tmp/ui-audit && cd tmp/ui-audit
npm init -y
npm i -D playwright @axe-core/playwright pa11y
npx playwright install chromium
```

### 3) Capture screenshots and generate axe-core report

```bash
node - <<'NODE'
const fs = require('fs');
(async () => {
  const { chromium } = require('playwright');
  const outDir = '../ui-audit-out';
  fs.mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto('http://localhost:8011/index.html', { waitUntil: 'load' });

  const sizes = [
    { width: 375, height: 2000, label: 'mobile' },
    { width: 768, height: 2400, label: 'tablet' },
    { width: 1280, height: 4000, label: 'desktop' },
  ];

  for (const s of sizes) {
    await page.setViewportSize({ width: s.width, height: s.height });
    await page.screenshot({ path: `${outDir}/index-${s.label}-${s.width}.png`, fullPage: true });
  }

  const { AxeBuilder } = require('@axe-core/playwright');
  const axeResults = await new AxeBuilder({ page }).analyze();
  fs.writeFileSync(`${outDir}/axe-index.json`, JSON.stringify(axeResults, null, 2));

  await browser.close();
})();
NODE
```

### 4) Run pa11y (accessibility) report

```bash
npx pa11y http://localhost:8011/index.html --reporter json > ../ui-audit-out/pa11y-index.json || true
```

### 5) Optional: run Lighthouse (with Playwright Chromium)

```bash
CHROME_PATH=$(node -p "require('playwright').chromium.executablePath()")
npx lighthouse http://localhost:8011/index.html \
  --preset=desktop \
  --output=html --output=json \
  --output-path=../ui-audit-out/lighthouse-index \
  --no-update-notifier \
  --chrome-path="$CHROME_PATH"
```

### Outputs
Artifacts are written to `tmp/ui-audit-out/`:
- `index-mobile-375.png`, `index-tablet-768.png`, `index-desktop-1280.png`
- `axe-index.json`, `pa11y-index.json`
- `lighthouse-index.html` and `lighthouse-index.report.json` (if Lighthouse ran)

### What to look for
- Ensure visual hierarchy, spacing, and CTA prominence look good across breakpoints
- Fix accessibility blockers first (labels, names, contrast, focus states)
- Regression check: compare screenshots to previous ones if applicable


