package com.irondust.search.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDoc {
    private String id;                    // wc_<productId>
    private String parent_id;             // variation grouping id
    private String type;                  // simple|variable|bundle|grouped
    private String sku;
    private String slug;
    private String name;
    private String permalink;

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getParent_id() { return parent_id; }
    public void setParent_id(String parent_id) { this.parent_id = parent_id; }
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


