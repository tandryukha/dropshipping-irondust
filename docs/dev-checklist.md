## Development Checklist

Use this checklist when making changes.

### Backend
- Build, run unit tests.
- Restart services via `./rebuild-and-watch.sh` when configs change.
- Verify critical endpoints with `curl`.

### Frontend (ui-v2)
- Run the Headless UI Audit after UI changes: see [Headless UI Audit](ui-audit.md).
  - Start server: `./ui-v2/serve.sh`
  - Generate screenshots and a11y reports (axe/pa11y)
  - Optional: run Lighthouse (Playwright Chromium)
- Review artifacts under `tmp/ui-audit-out/` and fix contrast/labels/focus issues first.

### Docs / Postman
- If API changed: update Postman collection in `postman/` and `/docs/api.md`.
- Keep `/docs` current; run mkdocs locally if needed.


