package com.irondust.search.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

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
    private String form; // powder|capsules|tabs|drink|gel|bar
    private String flavor;
    private Double net_weight_g;
    private Integer servings;
    private Double serving_size_g;
    private Double price; // euros
    private Double price_per_serving;
    private Double price_per_100g;
    private List<String> goal_tags;
    private List<String> diet_tags;
    private List<String> ingredients_key;
    private String parent_id;
    private String variant_group_id;
    private List<String> warnings;
    private Map<String, String> provenance; // field -> source (attribute|regex|derived)

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
    public Double getServing_size_g() { return serving_size_g; }
    public void setServing_size_g(Double serving_size_g) { this.serving_size_g = serving_size_g; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Double getPrice_per_serving() { return price_per_serving; }
    public void setPrice_per_serving(Double price_per_serving) { this.price_per_serving = price_per_serving; }
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

    // Helper method to copy from RawProduct
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
