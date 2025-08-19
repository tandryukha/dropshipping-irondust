package com.irondust.search.service.enrichment;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Warn {
    private String productId;
    private String code;
    private String field;
    private String message;
    private String evidence;

    public Warn() {}

    public Warn(String productId, String code, String field, String message, String evidence) {
        this.productId = productId;
        this.code = code;
        this.field = field;
        this.message = message;
        this.evidence = evidence;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    // Helper methods for common warning types
    public static Warn fieldConflict(String productId, String field, String detValue, String aiValue, String evidence) {
        return new Warn(productId, "FIELD_CONFLICT", field, 
            String.format("Deterministic value '%s' conflicts with AI value '%s'", detValue, aiValue), evidence);
    }

    public static Warn missingCritical(String productId, String field) {
        return new Warn(productId, "MISSING_CRITICAL", field, 
            String.format("Critical field '%s' is missing", field), null);
    }

    public static Warn unitAmbiguity(String productId, String field, String evidence) {
        return new Warn(productId, "UNIT_AMBIGUITY", field, 
            String.format("Unit ambiguity in field '%s'", field), evidence);
    }

    public static Warn badVariationGroup(String productId, String evidence) {
        return new Warn(productId, "BAD_VARIATION_GROUP", null, 
            "Failed to group product variations", evidence);
    }

    public static Warn unsupportedClaim(String productId, String claim, String evidence) {
        return new Warn(productId, "UNSUPPORTED_CLAIM", null, 
            String.format("Unsupported claim: %s", claim), evidence);
    }

    public static Warn ingredientParseFail(String productId, String evidence) {
        return new Warn(productId, "INGREDIENT_PARSE_FAIL", null, 
            "Failed to parse ingredients", evidence);
    }
}
