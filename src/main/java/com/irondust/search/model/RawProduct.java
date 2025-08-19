package com.irondust.search.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RawProduct {
    private String id;                    // wc_<productId>
    private String type;                  // simple|variable|bundle|grouped
    private String sku;
    private String slug;
    private String name;
    private String permalink;
    private String description;

    private Integer price_cents;
    private String currency;
    private Boolean in_stock;
    private Integer low_stock_remaining;
    private Double rating;
    private Integer review_count;
    private List<String> images;

    private List<Integer> categories_ids;
    private List<String> categories_slugs;
    private List<String> categories_names;

    private String brand_slug;
    private String brand_name;

    private Map<String, List<String>> dynamic_attrs; // attr_pa_* fields collected at ingest
    private String search_text;

    // Constructor from JsonNode (for backward compatibility)
    public static RawProduct fromJsonNode(JsonNode p) {
        RawProduct raw = new RawProduct();
        long productId = p.path("id").asLong();
        raw.setId("wc_" + productId);
        raw.setType(p.path("type").asText(null));
        raw.setSku(p.path("sku").asText(null));
        raw.setSlug(p.path("slug").asText(null));
        raw.setName(p.path("name").asText(null));
        raw.setPermalink(p.path("permalink").asText(null));
        raw.setDescription(p.path("description").asText(""));

        JsonNode prices = p.path("prices");
        if (!prices.isMissingNode()) {
            try {
                raw.setPrice_cents(Integer.parseInt(prices.path("price").asText("0")));
            } catch (NumberFormatException e) { raw.setPrice_cents(null); }
            raw.setCurrency(prices.path("currency_code").asText(null));
        }

        raw.setIn_stock(p.path("is_in_stock").asBoolean(false));
        raw.setLow_stock_remaining(p.path("low_stock_remaining").isInt() ? p.path("low_stock_remaining").asInt() : null);
        raw.setRating(p.path("average_rating").isNumber() ? p.path("average_rating").asDouble() : 0.0);
        raw.setReview_count(p.path("review_count").isInt() ? p.path("review_count").asInt() : 0);

        List<String> images = new java.util.ArrayList<>();
        if (p.path("images").isArray()) {
            for (JsonNode img : p.path("images")) {
                String src = img.path("src").asText(null);
                if (src != null) images.add(src);
            }
        }
        raw.setImages(images);

        List<Integer> catIds = new java.util.ArrayList<>();
        List<String> catSlugs = new java.util.ArrayList<>();
        List<String> catNames = new java.util.ArrayList<>();
        if (p.path("categories").isArray()) {
            for (JsonNode c : p.path("categories")) {
                if (c.path("id").isInt()) catIds.add(c.path("id").asInt());
                if (c.path("slug").isTextual()) catSlugs.add(c.path("slug").asText());
                if (c.path("name").isTextual()) catNames.add(c.path("name").asText());
            }
        }
        raw.setCategories_ids(catIds);
        raw.setCategories_slugs(catSlugs);
        raw.setCategories_names(catNames);

        Map<String, List<String>> dynamic = new java.util.LinkedHashMap<>();
        String brandSlug = null;
        String brandName = null;
        if (p.path("attributes").isArray()) {
            for (JsonNode a : p.path("attributes")) {
                String taxonomy = a.path("taxonomy").asText("");
                if (taxonomy == null || taxonomy.isBlank()) continue;
                if (taxonomy.equals("pa_tootja")) {
                    if (a.path("terms").isArray() && a.path("terms").size() > 0) {
                        JsonNode t = a.path("terms").get(0);
                        brandSlug = t.path("slug").asText(null);
                        brandName = t.path("name").asText(null);
                    }
                }
                if (taxonomy.startsWith("pa_")) {
                    String key = "attr_" + taxonomy;
                    List<String> vals = new java.util.ArrayList<>();
                    if (a.path("terms").isArray()) {
                        for (JsonNode t : a.path("terms")) {
                            String slug = t.path("slug").asText(null);
                            if (slug != null) vals.add(slug);
                        }
                    }
                    dynamic.put(key, vals);
                }
            }
        }
        raw.setBrand_slug(brandSlug);
        raw.setBrand_name(brandName);
        raw.setDynamic_attrs(dynamic);

        // Build search text
        String stripped = raw.getDescription().replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        StringBuilder searchText = new StringBuilder();
        if (raw.getName() != null) searchText.append(raw.getName()).append(' ');
        searchText.append(stripped).append(' ');
        if (!catNames.isEmpty()) searchText.append(String.join(" ", catNames)).append(' ');
        if (brandName != null) searchText.append(brandName);
        raw.setSearch_text(searchText.toString().trim());

        return raw;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPermalink() { return permalink; }
    public void setPermalink(String permalink) { this.permalink = permalink; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getPrice_cents() { return price_cents; }
    public void setPrice_cents(Integer price_cents) { this.price_cents = price_cents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Boolean getIn_stock() { return in_stock; }
    public void setIn_stock(Boolean in_stock) { this.in_stock = in_stock; }
    public Integer getLow_stock_remaining() { return low_stock_remaining; }
    public void setLow_stock_remaining(Integer low_stock_remaining) { this.low_stock_remaining = low_stock_remaining; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public Integer getReview_count() { return review_count; }
    public void setReview_count(Integer review_count) { this.review_count = review_count; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public List<Integer> getCategories_ids() { return categories_ids; }
    public void setCategories_ids(List<Integer> categories_ids) { this.categories_ids = categories_ids; }
    public List<String> getCategories_slugs() { return categories_slugs; }
    public void setCategories_slugs(List<String> categories_slugs) { this.categories_slugs = categories_slugs; }
    public List<String> getCategories_names() { return categories_names; }
    public void setCategories_names(List<String> categories_names) { this.categories_names = categories_names; }
    public String getBrand_slug() { return brand_slug; }
    public void setBrand_slug(String brand_slug) { this.brand_slug = brand_slug; }
    public String getBrand_name() { return brand_name; }
    public void setBrand_name(String brand_name) { this.brand_name = brand_name; }
    @JsonAnyGetter
    public Map<String, List<String>> getDynamic_attrs() { return dynamic_attrs; }
    public void setDynamic_attrs(Map<String, List<String>> dynamic_attrs) { this.dynamic_attrs = dynamic_attrs; }
    public String getSearch_text() { return search_text; }
    public void setSearch_text(String search_text) { this.search_text = search_text; }
}
