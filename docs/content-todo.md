# Content roadmap and TODOs

- NIH/ODS fetcher: ingest supplement facts and articles (public domain). Map to `ContentDoc` with `source=nih_ods`, include `url`, `title`, `excerpt`, `topic`, `updatedAt`.
- CDC syndication: prefer official embed/widgets for on-site rendering; fall back to summary + link. Mark `source=cdc` and store `license=Public Domain`.
- NHS (OGL v3.0) fetcher: ingest summaries for supplements and conditions. Add attribution text per OGL v3.0 and disallow NHS logos.
- Extend `ContentRenderService` rules: allow on-site rendering for NIH/CDC (public domain) and NHS (OGL v3.0 with attribution). Add explicit attribution boxes.
- Enrich content with normalized `topic` tags for better context and UI placement.
- UI: on-site reader modal wired to `/content/render`; interleave product upsell blocks based on `topic`.
- UI: add "Open on-site" button to content rail; keep external "Read more" link.
- UI: A/B toggle to auto-open reader for eligible sources on click.
- Caching: short-lived cache for rendered HTML to avoid repeated calls.
- Analytics: track open rate of reader, dwell time, and upsell CTR.
- Legal: add site-wide medical disclaimer and per-block license attribution.
