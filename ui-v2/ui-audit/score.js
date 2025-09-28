const fs = require('fs');
const path = require('path');

async function computeHeuristics(page) {
  return await page.evaluate(() => {
    function pxToNumber(px) {
      const n = parseFloat(String(px).replace('px', ''));
      return Number.isFinite(n) ? n : 0;
    }

    const body = document.body;
    const styles = getComputedStyle(body);
    const baseFontSizePx = pxToNumber(styles.fontSize);
    const baseLineHeight = parseFloat(styles.lineHeight) / baseFontSizePx || 0;

    // Find main CTA: heuristics — first button with .btn-primary or role/aria label Add to cart
    let ctaEl = document.querySelector('.btn.btn-primary')
      || Array.from(document.querySelectorAll('button, a')).find(el => {
        const label = (el.getAttribute('aria-label') || el.textContent || '').toLowerCase();
        return label.includes('add to cart') || label.includes('checkout') || label.includes('buy');
      })
      || null;

    let ctaRect = ctaEl ? ctaEl.getBoundingClientRect() : null;
    const viewportH = window.innerHeight;
    const viewportW = window.innerWidth;

    // Line length: measure max text line among paragraphs visible in viewport
    let longestChars = 0;
    let sampledFontSizePx = baseFontSizePx;
    const paras = Array.from(document.querySelectorAll('p'))
      .filter(el => {
        const r = el.getBoundingClientRect();
        return r.top < viewportH && r.bottom > 0 && (el.textContent || '').trim().length > 30;
      })
      .slice(0, 60);
    for (const p of paras) {
      const text = (p.textContent || '').trim();
      const cs = getComputedStyle(p);
      const fs = pxToNumber(cs.fontSize) || baseFontSizePx;
      sampledFontSizePx = fs;
      // rough estimate: characters per line ≈ element width / (0.6 * fontSize)
      const width = p.getBoundingClientRect().width;
      const charsPerLine = width / (0.6 * fs);
      if (charsPerLine > longestChars) longestChars = Math.round(charsPerLine);
    }
    const hasParagraphs = paras.length > 0;

    // Sticky CTA thumb zone: element fixed at bottom 40% of viewport height
    const fixedCandidates = Array.from(document.querySelectorAll('button, a, .btn'))
      .filter(el => {
        const cs = getComputedStyle(el);
        return cs.position === 'fixed' || cs.position === 'sticky';
      });
    let stickyCtaInThumbZone = false;
    for (const el of fixedCandidates) {
      const r = el.getBoundingClientRect();
      if (r.top >= viewportH * 0.6 && r.bottom <= viewportH) {
        const label = (el.getAttribute('aria-label') || el.textContent || '').toLowerCase();
        if (label.includes('add') || label.includes('cart') || label.includes('checkout') || el.className.includes('btn-primary')) {
          stickyCtaInThumbZone = true;
          break;
        }
      }
    }

    const ctaWidth = ctaRect ? Math.round(ctaRect.width) : 0;
    const ctaHeight = ctaRect ? Math.round(ctaRect.height) : 0;
    const ctaAboveFold = ctaRect ? (ctaRect.top >= 0 && ctaRect.top < viewportH) : false;

    // Headings/cards density in viewport
    const headings = Array.from(document.querySelectorAll('h1,h2,h3')).filter(el => {
      const r = el.getBoundingClientRect();
      return r.top < viewportH && r.bottom > 0;
    }).length;
    const cards = Array.from(document.querySelectorAll('.card')).filter(el => {
      const r = el.getBoundingClientRect();
      return r.top < viewportH && r.bottom > 0;
    }).length;

    return {
      viewportW,
      viewportH,
      baseFontSizePx,
      baseLineHeight,
      longestCharsPerLine: longestChars,
      hasParagraphs,
      cta: {
        width: ctaWidth,
        height: ctaHeight,
        aboveFold: ctaAboveFold
      },
      stickyCtaInThumbZone,
      density: { headingsInView: headings, cardsInView: cards }
    };
  });
}

function bucket(value, [min, max]) {
  return value >= min && value <= max;
}

function scoreFromAxe(violationsCount) {
  if (violationsCount === 0) return 5;
  if (violationsCount <= 3) return 4;
  if (violationsCount <= 8) return 3;
  if (violationsCount <= 15) return 2;
  return 1;
}

async function run(url) {
  const { chromium } = require('playwright');
  const { AxeBuilder } = require('@axe-core/playwright');

  const outDir = path.resolve(__dirname, '../ui-audit-out');
  fs.mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  const sizes = [
    { width: 375, height: 740, label: 'mobile' },
    { width: 768, height: 900, label: 'tablet' },
    { width: 1280, height: 900, label: 'desktop' }
  ];

  const results = [];

  await page.goto(url, { waitUntil: 'load' });

  for (const s of sizes) {
    await page.setViewportSize({ width: s.width, height: s.height });

    const heur = await computeHeuristics(page);
    const axe = await new AxeBuilder({ page }).analyze();
    const violations = axe.violations?.length || 0;

    const readabilityOk = bucket(heur.baseFontSizePx, [16, 20]) && bucket(heur.baseLineHeight, [1.5, 1.8]) && (heur.hasParagraphs ? bucket(heur.longestCharsPerLine, [45, 75]) : true);
    const ctaOk = heur.cta.width >= 120 && heur.cta.height >= 40 && heur.cta.aboveFold;
    const ergonomicsOk = heur.stickyCtaInThumbZone === true;

    const score = {
      viewport: s.label,
      accessibility: { violations, score: scoreFromAxe(violations) },
      readability: { fontPx: heur.baseFontSizePx, lineHeight: Number(heur.baseLineHeight.toFixed(2)), charsPerLine: heur.longestCharsPerLine, hasParagraphs: heur.hasParagraphs, pass: readabilityOk },
      cta: { width: heur.cta.width, height: heur.cta.height, aboveFold: heur.cta.aboveFold, pass: ctaOk },
      ergonomics: { stickyThumbZone: heur.stickyCtaInThumbZone, pass: ergonomicsOk },
      density: heur.density
    };
    results.push(score);
  }

  // Derive theme label from input URL (query param), fallback to 'sporty'
  let themeLabel = 'sporty';
  try {
    const u = new URL(url);
    const t = (u.searchParams.get('theme') || '').trim();
    if (t) themeLabel = t;
  } catch(_) { /* noop */ }

  const summary = {
    url,
    timestamps: { startedAt: new Date().toISOString() },
    thresholds: {
      accessibility: 'violations == 0',
      readability: '16-20px, 1.5-1.8 LH, 45-75 CPL',
      cta: '>=120x40 and above fold',
      ergonomics: 'sticky CTA bottom 40%'
    },
    theme: themeLabel,
    viewports: results,
  };

  const base = url.includes('index-v2') ? 'index-v2' : 'index';
  const outFile = path.join(outDir, `score-${base}-theme-${themeLabel}.json`);
  fs.writeFileSync(outFile, JSON.stringify(summary, null, 2));

  await browser.close();
  return outFile;
}

if (require.main === module) {
  const url = process.argv[2];
  if (!url) {
    console.error('Usage: node score.js <url>');
    process.exit(2);
  }
  run(url).then((out) => {
    console.log('Score written to', out);
  }).catch((err) => {
    console.error(err);
    process.exit(1);
  });
}

module.exports = { run };


