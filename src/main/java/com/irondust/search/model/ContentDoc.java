package com.irondust.search.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Document model for editorial/external content items indexed in Meilisearch.
 */
public class ContentDoc {
    private String id; // source-specific stable id
    private String source; // e.g., wikipedia, fda, nih_ods, cdc
    private String license; // e.g., CC BY-SA 4.0, Public Domain, OGL v3
    private String title;
    private String excerpt; // short summary/lead paragraph
    private String url; // canonical URL at the source
    private String language; // ISO code, e.g., en, ro, ru
    private String topic; // normalized topic (e.g., creatine, protein)
    private List<String> tags; // arbitrary tags, can be used as facets
    private OffsetDateTime updatedAt;
    private Map<String, Object> meta; // source-specific fields

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }
}


