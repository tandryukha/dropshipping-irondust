package com.irondust.search.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Represents a product after deterministic parsing and enrichment.
 * This class extends the raw product data with normalized, computed, and parsed fields.
 * 
 * <p>ParsedProduct contains all fields from {@link RawProduct} plus additional
 * fields that are computed deterministically through the enrichment pipeline:
 * 
 * <h3>Core Fields (from RawProduct)</h3>
 * <ul>
 *   <li>Basic product information (id, name, sku, etc.)</li>
 *   <li>Pricing and inventory data</li>
 *   <li>Categories and brand information</li>
 *   <li>Dynamic attributes</li>
 * </ul>
 * 
 * <h3>Parsed Fields (deterministic enrichment)</h3>
 * <ul>
 *   <li><strong>Form</strong>: Normalized product form (powder, capsules, tabs, etc.)</li>
 *   <li><strong>Flavor</strong>: Normalized flavor description</li>
 *   <li><strong>Net weight</strong>: Weight in grams, normalized from various units</li>
 *   <li><strong>Servings</strong>: Number of servings per container</li>
 *   <li><strong>Serving size</strong>: Weight per serving in grams</li>
 *   <li><strong>Price calculations</strong>: Price per serving, price per 100g</li>
 *   <li><strong>Goal tags</strong>: Fitness/health goals (preworkout, strength, etc.)</li>
 *   <li><strong>Diet tags</strong>: Dietary restrictions (vegan, gluten-free, etc.)</li>
 *   <li><strong>Ingredients</strong>: Key ingredient tokens</li>
 *   <li><strong>Variation grouping</strong>: Parent ID for product variations</li>
 * </ul>
 * 
 * <p>This class serves as the intermediate state in the enrichment pipeline.
 * It contains all deterministic parsing results and is ready for AI enrichment
 * to produce the final {@link EnrichedProduct}.
 * 
 * @see RawProduct
 * @see EnrichedProduct
 * @see com.irondust.search.service.enrichment.EnrichmentPipeline
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParsedProduct {
    // Core fields from RawProduct
    private String id;
    private String type;
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
    private Map<String, List<String>> dynamic_attrs;
    private String search_text;

    // Parsed fields (deterministic)
    /** 
     * Normalized product form. One of: powder, capsules, tabs, drink, gel, bar.
     * Extracted from WooCommerce attributes or inferred from product text.
     */
    private String form;
    
    /** 
     * Normalized flavor description. Examples: unflavored, citrus, berry, etc.
     * Extracted from WooCommerce attributes or inferred from product text.
     */
    private String flavor;
    
    /** 
     * Net weight in grams. Normalized from various units (kg, g, ml, l).
     * Extracted from WooCommerce attributes or parsed from product text.
     */
    private Double net_weight_g;
    
    /** 
     * Number of servings per container.
     * Extracted from WooCommerce attributes or calculated from net weight / serving size.
     * If a range is present on the label, {@link #servings_min} and {@link #servings_max}
     * will be populated instead and this field can remain null.
     */
    private Integer servings;
    
    /**
     * Minimum number of servings when a range is provided (e.g., 30–60 servings).
     */
    private Integer servings_min;
    
    /**
     * Maximum number of servings when a range is provided (e.g., 30–60 servings).
     */
    private Integer servings_max;
    
    /** 
     * Weight per serving in grams.
     * Parsed from product text using regex patterns.
     */
    private Double serving_size_g;
    
    /** 
     * Price in euros (calculated from price_cents / 100).
     */
    private Double price;
    
    /** 
     * Price per serving in euros (price / servings).
     * Calculated when both price and servings are available.
     */
    private Double price_per_serving;
    
    /**
     * Minimum price per serving when a serving range is present.
     * Computed as price / servings_max (more servings → lower price per serving).
     */
    private Double price_per_serving_min;
    
    /**
     * Maximum price per serving when a serving range is present.
     * Computed as price / servings_min (fewer servings → higher price per serving).
     */
    private Double price_per_serving_max;
    
    /** 
     * Price per 100g in euros ((price * 100) / net_weight_g).
     * Useful for comparing value across different product sizes.
     */
    private Double price_per_100g;
    
    /** 
     * Fitness and health goal tags. Examples: preworkout, strength, endurance, etc.
     * Extracted from categories and product text using keyword matching.
     */
    private List<String> goal_tags;
    
    /** 
     * Dietary restriction tags. Examples: vegan, gluten_free, lactose_free, etc.
     * Extracted from WooCommerce attributes and product text.
     */
    private List<String> diet_tags;
    
    /** 
     * Key ingredient tokens for search and filtering.
     * Extracted from product name and description when explicitly mentioned.
     */
    private List<String> ingredients_key;
    
    /** 
     * Parent ID for product variations. Used to group related products.
     * Generated from brand + normalized base title.
     */
    private String parent_id;
    
    /** 
     * Variant group ID for product variations. Currently same as parent_id.
     */
    private String variant_group_id;
    
    /** 
     * Warning messages from the enrichment process.
     * Contains issues like missing critical fields, unit ambiguities, etc.
     */
    private List<String> warnings;
    
    /** 
     * Provenance information for each enriched field.
     * Maps field names to their source: "attribute", "regex", "derived", etc.
     */
    private Map<String, String> provenance;

    // Getters and setters for core fields
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
    public Map<String, List<String>> getDynamic_attrs() { return dynamic_attrs; }
    public void setDynamic_attrs(Map<String, List<String>> dynamic_attrs) { this.dynamic_attrs = dynamic_attrs; }
    public String getSearch_text() { return search_text; }
    public void setSearch_text(String search_text) { this.search_text = search_text; }

    // Getters and setters for parsed fields
    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }
    public String getFlavor() { return flavor; }
    public void setFlavor(String flavor) { this.flavor = flavor; }
    public Double getNet_weight_g() { return net_weight_g; }
    public void setNet_weight_g(Double net_weight_g) { this.net_weight_g = net_weight_g; }
    public Integer getServings() { return servings; }
    public void setServings(Integer servings) { this.servings = servings; }
    public Integer getServings_min() { return servings_min; }
    public void setServings_min(Integer servings_min) { this.servings_min = servings_min; }
    public Integer getServings_max() { return servings_max; }
    public void setServings_max(Integer servings_max) { this.servings_max = servings_max; }
    public Double getServing_size_g() { return serving_size_g; }
    public void setServing_size_g(Double serving_size_g) { this.serving_size_g = serving_size_g; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Double getPrice_per_serving() { return price_per_serving; }
    public void setPrice_per_serving(Double price_per_serving) { this.price_per_serving = price_per_serving; }
    public Double getPrice_per_serving_min() { return price_per_serving_min; }
    public void setPrice_per_serving_min(Double price_per_serving_min) { this.price_per_serving_min = price_per_serving_min; }
    public Double getPrice_per_serving_max() { return price_per_serving_max; }
    public void setPrice_per_serving_max(Double price_per_serving_max) { this.price_per_serving_max = price_per_serving_max; }
    public Double getPrice_per_100g() { return price_per_100g; }
    public void setPrice_per_100g(Double price_per_100g) { this.price_per_100g = price_per_100g; }
    public List<String> getGoal_tags() { return goal_tags; }
    public void setGoal_tags(List<String> goal_tags) { this.goal_tags = goal_tags; }
    public List<String> getDiet_tags() { return diet_tags; }
    public void setDiet_tags(List<String> diet_tags) { this.diet_tags = diet_tags; }
    public List<String> getIngredients_key() { return ingredients_key; }
    public void setIngredients_key(List<String> ingredients_key) { this.ingredients_key = ingredients_key; }
    public String getParent_id() { return parent_id; }
    public void setParent_id(String parent_id) { this.parent_id = parent_id; }
    public String getVariant_group_id() { return variant_group_id; }
    public void setVariant_group_id(String variant_group_id) { this.variant_group_id = variant_group_id; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public Map<String, String> getProvenance() { return provenance; }
    public void setProvenance(Map<String, String> provenance) { this.provenance = provenance; }

    /**
     * Creates a ParsedProduct instance from a RawProduct.
     * 
     * <p>This method copies all fields from the RawProduct to create a new
     * ParsedProduct instance. The parsed fields (form, flavor, etc.) will be
     * populated by the enrichment pipeline.
     * 
     * @param raw The RawProduct to copy from
     * @return A new ParsedProduct with all raw fields copied
     */
    public static ParsedProduct fromRawProduct(RawProduct raw) {
        ParsedProduct parsed = new ParsedProduct();
        parsed.setId(raw.getId());
        parsed.setType(raw.getType());
        parsed.setSku(raw.getSku());
        parsed.setSlug(raw.getSlug());
        parsed.setName(raw.getName());
        parsed.setPermalink(raw.getPermalink());
        parsed.setDescription(raw.getDescription());
        parsed.setPrice_cents(raw.getPrice_cents());
        parsed.setCurrency(raw.getCurrency());
        parsed.setIn_stock(raw.getIn_stock());
        parsed.setLow_stock_remaining(raw.getLow_stock_remaining());
        parsed.setRating(raw.getRating());
        parsed.setReview_count(raw.getReview_count());
        parsed.setImages(raw.getImages());
        parsed.setCategories_ids(raw.getCategories_ids());
        parsed.setCategories_slugs(raw.getCategories_slugs());
        parsed.setCategories_names(raw.getCategories_names());
        parsed.setBrand_slug(raw.getBrand_slug());
        parsed.setBrand_name(raw.getBrand_name());
        parsed.setDynamic_attrs(raw.getDynamic_attrs());
        parsed.setSearch_text(raw.getSearch_text());
        return parsed;
    }
}
