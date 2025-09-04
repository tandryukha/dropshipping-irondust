package com.irondust.search.service;

import com.irondust.search.model.ContentDoc;
import com.irondust.search.service.content.WikipediaFetcher;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Produces safe, on-site HTML for content items where full-text rendering is permitted.
 * Supports Wikipedia (CC BY-SA 4.0) full articles and FDA (Public Domain) items.
 * Adds attribution/license box to remain compliant and transparent.
 */
@Service
public class ContentRenderService {

    public record RenderResult(String html, Map<String, Object> meta) {}

    private final WikipediaFetcher wikipediaFetcher;

    public ContentRenderService(WikipediaFetcher wikipediaFetcher) {
        this.wikipediaFetcher = wikipediaFetcher;
    }

    public boolean canRenderOnSite(ContentDoc doc) {
        if (doc == null) return false;
        String source = safeLower(doc.getSource());
        String license = safeLower(doc.getLicense());
        // Whitelist: FDA (Public Domain), Wikipedia (CC BY-SA), NIH/CDC (public domain) when added later
        if ("fda".equals(source)) return true;
        if ("wikipedia".equals(source) && (license.contains("cc by-sa") || license.contains("cc-by-sa"))) return true;
        if (("nih".equals(source) || "cdc".equals(source) || "fda".equals(source)) && license.contains("public domain")) return true;
        return false;
    }

    public RenderResult render(ContentDoc doc) {
        if (doc == null) return new RenderResult("", Map.of("error", "no_document"));
        String source = safeLower(doc.getSource());
        String title = doc.getTitle() != null ? doc.getTitle() : "";
        String excerpt = doc.getExcerpt() != null ? doc.getExcerpt() : "";

        // Sanitize excerpt; allow simple formatting
        String safeBody = Jsoup.clean(excerpt, buildSafelist());

        String license = doc.getLicense() != null ? doc.getLicense() : "";
        String url = doc.getUrl() != null ? doc.getUrl() : "";

        String attributionHtml = attributionBox(source, url);

        String html = "<article class=\"content-article\">" +
                (title.isBlank() ? "" : ("<h1>" + esc(title) + "</h1>")) +
                "<div class=\"content-body\">" + safeBody + "</div>" +
                attributionHtml +
                "</article>";

        return new RenderResult(html, Map.of(
                "source", doc.getSource(),
                "license", license,
                "url", url,
                "title", title,
                "language", doc.getLanguage()
        ));
    }

    /**
     * Rich rendering that attempts to include the full article body and images when legally allowed.
     * Currently implemented for Wikipedia.
     */
    public Mono<RenderResult> renderRichAsync(ContentDoc doc) {
        if (doc == null) return Mono.just(new RenderResult("", Map.of("error", "no_document")));
        String source = safeLower(doc.getSource());
        if ("wikipedia".equals(source)) {
            String title = doc.getTitle() != null ? doc.getTitle() : "";
            String lang = (doc.getLanguage() == null || doc.getLanguage().isBlank()) ? "en" : doc.getLanguage();
            String host = lang.equals("en") ? "https://en.wikipedia.org" : ("https://" + lang + ".wikipedia.org");
            return wikipediaFetcher.fetchFullHtml(title, lang)
                    .map(raw -> rewriteWikiLinks(raw, host))
                    .map(html -> Jsoup.clean(html, buildSafelist()))
                    .map(body -> {
                        String attributionHtml = attributionBox(source, doc.getUrl());
                        String imagesNote = "<div class=\"content-attribution\" style=\"font-size:12px;color:#6b7280\">Images may have separate licenses. View on source for details.</div>";
                        String full = "<article class=\"content-article\">" +
                                (doc.getTitle() == null || doc.getTitle().isBlank() ? "" : ("<h1>" + esc(doc.getTitle()) + "</h1>")) +
                                "<div class=\"content-body\">" + body + "</div>" +
                                imagesNote + attributionHtml +
                                "</article>";
                        return new RenderResult(full, Map.of(
                                "source", doc.getSource(),
                                "license", doc.getLicense(),
                                "url", doc.getUrl(),
                                "title", doc.getTitle(),
                                "language", doc.getLanguage()
                        ));
                    })
                    .onErrorReturn(render(doc));
        }
        return Mono.just(render(doc));
    }

    private Safelist buildSafelist() {
        return Safelist.relaxed()
                .addTags("figure", "figcaption", "table", "thead", "tbody", "tfoot", "tr", "th", "td", "sup", "sub")
                .addAttributes("a", "href", "title", "rel", "target")
                .addAttributes("img", "src", "srcset", "alt", "title", "width", "height", "loading", "decoding")
                .preserveRelativeLinks(true);
    }

    private String attributionBox(String source, String url) {
        return switch (safeLower(source)) {
            case "wikipedia" ->
                    "<div class=\"content-attribution\">" +
                            "Source: <a href=\"" + esc(url) + "\" target=\"_blank\" rel=\"nofollow noopener\">Wikipedia</a>. " +
                            "Licensed under <a href=\"https://creativecommons.org/licenses/by-sa/4.0/\" target=\"_blank\" rel=\"nofollow noopener\">CC BY-SA 4.0</a>. " +
                            "This section may have been modified; see the original article for history." +
                            "</div>";
            case "fda" ->
                    "<div class=\"content-attribution\">Source: U.S. FDA (Public Domain). <a href=\"" + esc(url) + "\" target=\"_blank\" rel=\"nofollow noopener\">View on FDA.gov</a></div>";
            default ->
                    "<div class=\"content-attribution\">Source: " + esc(source.toUpperCase()) + ". " +
                            (url == null || url.isBlank() ? "" : " <a href=\\\"" + esc(url) + "\\\" target=\\\"_blank\\\" rel=\\\"nofollow noopener\\\">Original</a>") + "</div>";
        };
    }

    private String rewriteWikiLinks(String html, String host) {
        if (html == null) return "";
        String s = html;
        s = s.replace("href=\"//", "href=\"https://");
        s = s.replace("src=\"//", "src=\"https://");
        s = s.replace("href=\"/", "href=\"" + host + "/");
        s = s.replace("src=\"/", "src=\"" + host + "/");
        return s;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String safeLower(String s) { return s == null ? "" : s.toLowerCase(); }
}


