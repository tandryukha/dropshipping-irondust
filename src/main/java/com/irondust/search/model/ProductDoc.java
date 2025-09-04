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
    private Integer regular_price_cents;
    private Integer sale_price_cents;
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
    
    // Multilingual fields
    private Map<String, String> name_i18n;                  // name translations by language code
    private Map<String, String> description_i18n;           // description translations
    private Map<String, String> short_description_i18n;     // short description translations
    private Map<String, String> benefit_snippet_i18n;       // benefit snippet translations
    private Map<String, List<String>> categories_names_i18n; // category name translations
    private Map<String, String> form_i18n;                  // form translations
    private Map<String, String> flavor_i18n;                // flavor translations
    private Map<String, String> search_text_i18n;           // search text in all languages
    private Map<String, List<Map<String, String>>> faq_i18n; // FAQ translations by language

    // Parsed + Derived fields (Phase 1)
    private String form;
    private String flavor;
    private Double net_weight_g;
    private Integer servings;
    private Integer servings_min;
    private Integer servings_max;
    private Double serving_size_g;
    private Double price; // euros
    private Double price_per_serving;
    private Double price_per_serving_min;
    private Double price_per_serving_max;
    private Double price_per_100g;
    private Double discount_pct;
    private Boolean is_on_sale;
    // Count-based packaging fields
    private Integer unit_count;
    private Integer units_per_serving;
    private Double unit_mass_g;
    private Double price_per_unit;
    private List<String> goal_tags;
    private List<String> diet_tags;
    private List<String> ingredients_key;
    // Per-goal relevance scores (0.0-1.0)
    private Double goal_preworkout_score;
    private Double goal_strength_score;
    private Double goal_endurance_score;
    private Double goal_lean_muscle_score;
    private Double goal_recovery_score;
    private Double goal_weight_loss_score;
    private Double goal_wellness_score;
    // AI synonyms flattened per language for search
    private List<String> synonyms_en;
    private List<String> synonyms_ru;
    private List<String> synonyms_et;
    // AI-generated UX fields (Phase 1 optional)
    private String benefit_snippet;
    private List<java.util.Map<String, String>> faq;

    // AI dosage/timing (localized variants provided via *_i18n below)
    private String dosage_text;
    private String timing_text;
    // UX title optimized for display (feature-flagged generation)
    private String display_title;

    // Localized dosage/timing
    private Map<String, String> dosage_text_i18n;
    private Map<String, String> timing_text_i18n;

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
    
    // Multilingual field getters and setters
    public Map<String, String> getName_i18n() { return name_i18n; }
    public void setName_i18n(Map<String, String> name_i18n) { this.name_i18n = name_i18n; }
    public Map<String, String> getDescription_i18n() { return description_i18n; }
    public void setDescription_i18n(Map<String, String> description_i18n) { this.description_i18n = description_i18n; }
    public Map<String, String> getShort_description_i18n() { return short_description_i18n; }
    public void setShort_description_i18n(Map<String, String> short_description_i18n) { this.short_description_i18n = short_description_i18n; }
    public Map<String, List<String>> getCategories_names_i18n() { return categories_names_i18n; }
    public void setCategories_names_i18n(Map<String, List<String>> categories_names_i18n) { this.categories_names_i18n = categories_names_i18n; }
    public Map<String, String> getForm_i18n() { return form_i18n; }
    public void setForm_i18n(Map<String, String> form_i18n) { this.form_i18n = form_i18n; }
    public Map<String, String> getFlavor_i18n() { return flavor_i18n; }
    public void setFlavor_i18n(Map<String, String> flavor_i18n) { this.flavor_i18n = flavor_i18n; }
    public Map<String, String> getSearch_text_i18n() { return search_text_i18n; }
    public void setSearch_text_i18n(Map<String, String> search_text_i18n) { this.search_text_i18n = search_text_i18n; }
    public Map<String, String> getBenefit_snippet_i18n() { return benefit_snippet_i18n; }
    public void setBenefit_snippet_i18n(Map<String, String> benefit_snippet_i18n) { this.benefit_snippet_i18n = benefit_snippet_i18n; }
    public Map<String, List<Map<String, String>>> getFaq_i18n() { return faq_i18n; }
    public void setFaq_i18n(Map<String, List<Map<String, String>>> faq_i18n) { this.faq_i18n = faq_i18n; }

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
    public Double getDiscount_pct() { return discount_pct; }
    public void setDiscount_pct(Double discount_pct) { this.discount_pct = discount_pct; }
    public Boolean getIs_on_sale() { return is_on_sale; }
    public void setIs_on_sale(Boolean is_on_sale) { this.is_on_sale = is_on_sale; }
    public Integer getUnit_count() { return unit_count; }
    public void setUnit_count(Integer unit_count) { this.unit_count = unit_count; }
    public Integer getUnits_per_serving() { return units_per_serving; }
    public void setUnits_per_serving(Integer units_per_serving) { this.units_per_serving = units_per_serving; }
    public Double getUnit_mass_g() { return unit_mass_g; }
    public void setUnit_mass_g(Double unit_mass_g) { this.unit_mass_g = unit_mass_g; }
    public Double getPrice_per_unit() { return price_per_unit; }
    public void setPrice_per_unit(Double price_per_unit) { this.price_per_unit = price_per_unit; }
    public List<String> getGoal_tags() { return goal_tags; }
    public void setGoal_tags(List<String> goal_tags) { this.goal_tags = goal_tags; }
    public List<String> getDiet_tags() { return diet_tags; }
    public void setDiet_tags(List<String> diet_tags) { this.diet_tags = diet_tags; }
    public List<String> getIngredients_key() { return ingredients_key; }
    public void setIngredients_key(List<String> ingredients_key) { this.ingredients_key = ingredients_key; }
    public Double getGoal_preworkout_score() { return goal_preworkout_score; }
    public void setGoal_preworkout_score(Double goal_preworkout_score) { this.goal_preworkout_score = goal_preworkout_score; }
    public Double getGoal_strength_score() { return goal_strength_score; }
    public void setGoal_strength_score(Double goal_strength_score) { this.goal_strength_score = goal_strength_score; }
    public Double getGoal_endurance_score() { return goal_endurance_score; }
    public void setGoal_endurance_score(Double goal_endurance_score) { this.goal_endurance_score = goal_endurance_score; }
    public Double getGoal_lean_muscle_score() { return goal_lean_muscle_score; }
    public void setGoal_lean_muscle_score(Double goal_lean_muscle_score) { this.goal_lean_muscle_score = goal_lean_muscle_score; }
    public Double getGoal_recovery_score() { return goal_recovery_score; }
    public void setGoal_recovery_score(Double goal_recovery_score) { this.goal_recovery_score = goal_recovery_score; }
    public Double getGoal_weight_loss_score() { return goal_weight_loss_score; }
    public void setGoal_weight_loss_score(Double goal_weight_loss_score) { this.goal_weight_loss_score = goal_weight_loss_score; }
    public Double getGoal_wellness_score() { return goal_wellness_score; }
    public void setGoal_wellness_score(Double goal_wellness_score) { this.goal_wellness_score = goal_wellness_score; }
    public List<String> getSynonyms_en() { return synonyms_en; }
    public void setSynonyms_en(List<String> synonyms_en) { this.synonyms_en = synonyms_en; }
    public List<String> getSynonyms_ru() { return synonyms_ru; }
    public void setSynonyms_ru(List<String> synonyms_ru) { this.synonyms_ru = synonyms_ru; }
    public List<String> getSynonyms_et() { return synonyms_et; }
    public void setSynonyms_et(List<String> synonyms_et) { this.synonyms_et = synonyms_et; }
    public String getBenefit_snippet() { return benefit_snippet; }
    public void setBenefit_snippet(String benefit_snippet) { this.benefit_snippet = benefit_snippet; }
    public List<java.util.Map<String, String>> getFaq() { return faq; }
    public void setFaq(List<java.util.Map<String, String>> faq) { this.faq = faq; }
    public String getDosage_text() { return dosage_text; }
    public void setDosage_text(String dosage_text) { this.dosage_text = dosage_text; }
    public String getTiming_text() { return timing_text; }
    public void setTiming_text(String timing_text) { this.timing_text = timing_text; }
    public String getDisplay_title() { return display_title; }
    public void setDisplay_title(String display_title) { this.display_title = display_title; }
    public Map<String, String> getDosage_text_i18n() { return dosage_text_i18n; }
    public void setDosage_text_i18n(Map<String, String> dosage_text_i18n) { this.dosage_text_i18n = dosage_text_i18n; }
    public Map<String, String> getTiming_text_i18n() { return timing_text_i18n; }
    public void setTiming_text_i18n(Map<String, String> timing_text_i18n) { this.timing_text_i18n = timing_text_i18n; }
}


