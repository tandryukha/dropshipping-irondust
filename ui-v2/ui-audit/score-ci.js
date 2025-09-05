const fs = require('fs');
const path = require('path');
const { run } = require('./score');

async function main() {
  const url = process.argv[2];
  if (!url) {
    console.error('Usage: node score-ci.js <url>');
    process.exit(2);
  }
  const outPath = await run(url);
  const data = JSON.parse(fs.readFileSync(outPath, 'utf8'));

  let ok = true;
  const failures = [];

  for (const v of data.viewports) {
    if (!(v.accessibility.violations === 0)) {
      ok = false;
      failures.push(`[${v.viewport}] Accessibility violations=${v.accessibility.violations}`);
    }
    if (!v.readability.pass) {
      ok = false;
      failures.push(`[${v.viewport}] Readability fail (fontPx=${v.readability.fontPx}, LH=${v.readability.lineHeight}, CPL=${v.readability.charsPerLine})`);
    }
    if (!v.cta.pass) {
      ok = false;
      failures.push(`[${v.viewport}] CTA fail (size=${v.cta.width}x${v.cta.height}, aboveFold=${v.cta.aboveFold})`);
    }
    if (!v.ergonomics.pass) {
      ok = false;
      failures.push(`[${v.viewport}] Ergonomics fail (stickyThumbZone=${v.ergonomics.stickyThumbZone})`);
    }
  }

  if (!ok) {
    console.error('Design gate failed:\n' + failures.join('\n'));
    process.exit(1);
  } else {
    console.log('Design gate passed');
  }
}

main().catch(err => { console.error(err); process.exit(1); });


