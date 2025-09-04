package com.irondust.search.service;

import com.irondust.search.model.ContentDoc;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Produces safe, on-site HTML for content items where full-text rendering is permitted.
 * Currently supports Wikipedia (CC BY-SA 4.0) lead extracts and FDA (Public Domain) recall items.
 * Adds attribution/license box to remain compliant and transparent.
 */
@Service
public class ContentRenderService {

    public record RenderResult(String html, Map<String, Object> meta) {}

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
        String safeBody = Jsoup.clean(excerpt, Safelist.relaxed()
                .addAttributes("a", "href", "title", "rel", "target")
                .preserveRelativeLinks(true));

        String license = doc.getLicense() != null ? doc.getLicense() : "";
        String url = doc.getUrl() != null ? doc.getUrl() : "";

        String attributionHtml = switch (source) {
            case "wikipedia" ->
                    "<div class=\"content-attribution\">" +
                    "Source: <a href=\"" + esc(url) + "\" target=\"_blank\" rel=\"nofollow noopener\">Wikipedia</a>. " +
                    "Licensed under <a href=\"https://creativecommons.org/licenses/by-sa/4.0/\" target=\"_blank\" rel=\"nofollow noopener\">CC BY-SA 4.0</a>. " +
                    "This section may have been modified; see the original article for history." +
                    "</div>";
            case "fda" ->
                    "<div class=\"content-attribution\">Source: U.S. FDA (Public Domain). <a href=\"" + esc(url) + "\" target=\"_blank\" rel=\"nofollow noopener\">View on FDA.gov</a></div>";
            default ->
                    "<div class=\"content-attribution\">Source: " + esc(source.toUpperCase()) + ". License: " + esc(license) + 
                    (url.isBlank() ? "" : " <a href=\\\"" + esc(url) + "\\\" target=\\\"_blank\\\" rel=\\\"nofollow noopener\\\">Original</a>") + "</div>";
        };

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

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String safeLower(String s) { return s == null ? "" : s.toLowerCase(); }
}


