package com.irondust.search.service.content;

import com.irondust.search.model.ContentDoc;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fetches short extracts from Wikipedia with attribution and license info.
 * Uses the REST v1 summary endpoint.
 */
@Service
public class WikipediaFetcher {
    private final WebClient http;

    public WikipediaFetcher() {
        this.http = WebClient.builder()
                .baseUrl("https://en.wikipedia.org")
                .defaultHeaders(h -> h.setAccept(MediaType.parseMediaTypes("application/json")))
                .build();
    }

    public Mono<ContentDoc> fetchSummary(String title, String lang) {
        String language = (lang == null || lang.isBlank()) ? "en" : lang;
        String wikiHost = language.equals("en") ? "https://en.wikipedia.org" : ("https://" + language + ".wikipedia.org");
        WebClient client = language.equals("en") ? http : http.mutate().baseUrl(wikiHost).build();
        String normalized = title.replace(' ', '_');
        return client.get()
                .uri("/api/rest_v1/page/summary/{title}", normalized)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                .map((java.util.Map<String, Object> json) -> {
                    ContentDoc doc = new ContentDoc();
                    String pageTitle = String.valueOf(json.getOrDefault("title", title));
                    String extract = String.valueOf(json.getOrDefault("extract", ""));
                    Object cu = json.get("content_urls");
                    String pageUrl = "https://wikipedia.org";
                    if (cu instanceof java.util.Map<?,?> cuMap) {
                        Object desktop = cuMap.get("desktop");
                        if (desktop instanceof java.util.Map<?,?> desk) {
                            Object page = desk.get("page");
                            if (page != null) {
                                pageUrl = String.valueOf(page);
                            }
                        }
                    }
                    String stableId = ("wikipedia_" + language + "_" + normalized.toLowerCase()).replaceAll("[^a-z0-9_-]", "_");

                    doc.setId(stableId);
                    doc.setSource("wikipedia");
                    doc.setLicense("CC BY-SA 4.0");
                    doc.setTitle(pageTitle);
                    doc.setExcerpt(extract);
                    doc.setUrl(pageUrl);
                    doc.setLanguage(language);
                    doc.setTags(List.of("wikipedia"));
                    doc.setUpdatedAt(OffsetDateTime.now());
                    return doc;
                });
    }

    /**
     * Fetches the full article HTML (body only) for on-site rendering.
     * Uses action=parse (formatversion=2) which returns sanitized article body HTML.
     */
    public Mono<String> fetchFullHtml(String title, String lang) {
        String language = (lang == null || lang.isBlank()) ? "en" : lang;
        String wikiHost = language.equals("en") ? "https://en.wikipedia.org" : ("https://" + language + ".wikipedia.org");
        WebClient client = language.equals("en") ? http : http.mutate().baseUrl(wikiHost).build();
        String normalized = title.replace(' ', '_');
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/w/api.php")
                        .queryParam("action", "parse")
                        .queryParam("page", normalized)
                        .queryParam("prop", "text")
                        .queryParam("formatversion", "2")
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                .map(map -> {
                    Object parse = map.get("parse");
                    if (parse instanceof java.util.Map<?,?> p) {
                        Object text = p.get("text");
                        return text != null ? String.valueOf(text) : "";
                    }
                    return "";
                })
                .onErrorReturn("");
    }
}


