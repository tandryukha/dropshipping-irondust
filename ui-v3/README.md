# supplements-mockup — Playwright checks

## Setup
- Install deps:
  - `npm install`
  - If needed: `npx playwright install chromium --with-deps`

## Run tests (manual)
- All (desktop + mobile): `npm run test`
- Desktop only: `npm run test:desktop`
- Mobile only: `npm run test:mobile`
- Update snapshots (only after intentional UI changes): `npm run test:update`
- Open last HTML report: `npx playwright show-report`

## Where screenshots live
- Baselines (committed): `tests/desktop7.spec.ts-snapshots/`
  - `desktop-home-Desktop-Chromium-<os>.png`
  - `mobile-home-Mobile-Chromium-<os>.png`
- On failures (actual/expected/diff): `test-results/**/`

## MCP in Cursor (optional)
- From MCP panel:
  - `runTests` → same as `npm run -s test`
  - `updateSnapshots` → same as `npm run -s test:update`
  - `evaluateJson` → runs `node tools/evaluate-json.js` for pipeline scoring
