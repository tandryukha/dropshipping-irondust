package com.irondust.search.controller;

import com.irondust.search.config.AppProperties;
import com.irondust.search.service.ContentIndexService;
import com.irondust.search.service.ContentIngestService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.irondust.search.service.ContentRenderService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class ContentController {
    private final ContentIndexService contentIndexService;
    private final ContentIngestService contentIngestService;
    private final AppProperties appProperties;
    private final ContentRenderService contentRenderService;

    public ContentController(ContentIndexService contentIndexService, ContentIngestService contentIngestService, AppProperties appProperties, ContentRenderService contentRenderService) {
        this.contentIndexService = contentIndexService;
        this.contentIngestService = contentIngestService;
        this.appProperties = appProperties;
        this.contentRenderService = contentRenderService;
    }

    @PostMapping(value = "/ingest/content/minimal", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> ingestMinimal(@RequestHeader(value = "x-admin-key", required = false) String adminKey) {
        if (adminKey == null || !adminKey.equals(appProperties.getAdminKey())) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        return contentIngestService.ingestMinimalSeed()
                .map(count -> ResponseEntity.ok(Map.of("indexed", count)));
    }

    @PostMapping(value = "/content/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String q = body == null ? "" : String.valueOf(body.getOrDefault("q", ""));
        String filter = body == null ? null : (String) body.get("filter");
        int page = body != null && body.get("page") instanceof Number ? ((Number) body.get("page")).intValue() : 1;
        int size = body != null && body.get("size") instanceof Number ? ((Number) body.get("size")).intValue() : 10;
        return contentIndexService.search(q, filter, page, size);
    }

    @PostMapping(value = "/content/render", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> render(@RequestBody Map<String, Object> body) {
        if (body == null) return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "missing_body")));
        Object hit = body.get("hit");
        if (!(hit instanceof Map<?, ?> m)) return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "missing_hit")));

        com.irondust.search.model.ContentDoc doc = new com.irondust.search.model.ContentDoc();
        Object id = m.get("id"); doc.setId(id != null ? String.valueOf(id) : "");
        Object source = m.get("source"); doc.setSource(source != null ? String.valueOf(source) : "");
        Object license = m.get("license"); doc.setLicense(license != null ? String.valueOf(license) : "");
        Object title = m.get("title"); doc.setTitle(title != null ? String.valueOf(title) : "");
        Object excerpt = m.get("excerpt"); doc.setExcerpt(excerpt != null ? String.valueOf(excerpt) : "");
        Object url = m.get("url"); if (url != null) doc.setUrl(String.valueOf(url));
        Object language = m.get("language"); if (language != null) doc.setLanguage(String.valueOf(language));

        if (!contentRenderService.canRenderOnSite(doc)) {
            return Mono.just(ResponseEntity.status(451).body(Map.of(
                    "error", "not_eligible_for_on_site_render",
                    "hint", "Use source url in an embed or link",
                    "source", doc.getSource(),
                    "license", doc.getLicense()
            )));
        }

        return contentRenderService.renderRichAsync(doc)
                .map(rr -> ResponseEntity.ok(Map.of("html", rr.html(), "meta", rr.meta())));
    }
}


