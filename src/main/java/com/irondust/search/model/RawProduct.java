package com.irondust.search.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Represents raw product data as received from WooCommerce Store API.
 * This is the initial data structure before any enrichment or processing.
 * 
 * <p>RawProduct contains all the original fields from WooCommerce including:
 * <ul>
 *   <li>Basic product information (id, name, sku, etc.)</li>
 *   <li>Pricing and inventory data</li>
 *   <li>Categories and brand information</li>
 *   <li>Dynamic attributes (WooCommerce product attributes)</li>
 *   <li>Search text (concatenated from name, description, categories)</li>
 * </ul>
 * 
 * <p>This class serves as the input to the enrichment pipeline and should not
 * be modified during processing. All enrichment results are stored in
 * {@link ParsedProduct} and {@link EnrichedProduct}.
 * 
 * @see ParsedProduct
 * @see EnrichedProduct
 * @see com.irondust.search.service.enrichment.EnrichmentPipeline
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RawProduct {
    /** Unique product identifier in format "wc_<productId>" */
    private String id;
    
    /** Product type: simple, variable, bundle, grouped */
    private String type;
    
    /** Stock keeping unit */
    private String sku;
    
    /** URL-friendly product slug */
    private String slug;
    
    /** Product display name */
    private String name;
    
    /** Full product URL */
    private String permalink;
    
    /** Product description (HTML content) */
    private String description;

    /** Price in cents */
    private Integer price_cents;
    /** Regular price in cents (if on sale) */
    private Integer regular_price_cents;
    /** Sale price in cents (if on sale) */
    private Integer sale_price_cents;
    
    /** Currency code (e.g., "EUR") */
    private String currency;
    
    /** Whether product is currently in stock */
    private Boolean in_stock;
    
    /** Remaining stock if low stock threshold is reached */
    private Integer low_stock_remaining;
    
    /** Average product rating (0.0 - 5.0) */
    private Double rating;
    
    /** Number of product reviews */
    private Integer review_count;
    
    /** List of product image URLs */
    private List<String> images;

    /** Category IDs */
    private List<Integer> categories_ids;
    
    /** Category slugs */
    private List<String> categories_slugs;
    
    /** Category display names */
    private List<String> categories_names;

    /** Brand slug */
    private String brand_slug;
    
    /** Brand display name */
    private String brand_name;

    /** 
     * Dynamic attributes from WooCommerce product attributes.
     * Keys are in format "attr_pa_<taxonomy>" where taxonomy is the WooCommerce attribute name.
     * Values are lists of attribute term slugs.
     */
    private Map<String, List<String>> dynamic_attrs;
    
    /** 
     * Searchable text concatenated from name, description, categories, and brand.
     * HTML tags are stripped and whitespace is normalized.
     */
    private String search_text;

    /**
     * Creates a RawProduct instance from a WooCommerce JSON response.
     * 
     * <p>This method parses the standard WooCommerce Store API response format
     * and extracts all relevant fields into the RawProduct structure.
     * 
     * @param p The JSON node containing WooCommerce product data
     * @return A populated RawProduct instance
     */
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
            // Optional regular/sale prices for discount computation
            try {
                String rp = prices.path("regular_price").asText(null);
                if (rp != null && !rp.isBlank()) raw.setRegular_price_cents(Integer.parseInt(rp));
            } catch (NumberFormatException ignored) { raw.setRegular_price_cents(null); }
            try {
                String sp = prices.path("sale_price").asText(null);
                if (sp != null && !sp.isBlank()) raw.setSale_price_cents(Integer.parseInt(sp));
            } catch (NumberFormatException ignored) { raw.setSale_price_cents(null); }
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
    public Integer getRegular_price_cents() { return regular_price_cents; }
    public void setRegular_price_cents(Integer regular_price_cents) { this.regular_price_cents = regular_price_cents; }
    public Integer getSale_price_cents() { return sale_price_cents; }
    public void setSale_price_cents(Integer sale_price_cents) { this.sale_price_cents = sale_price_cents; }
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
