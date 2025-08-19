package com.irondust.search.service.enrichment;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a warning or issue encountered during the enrichment process.
 * 
 * <p>Warnings are used to track issues, conflicts, and problems that occur
 * during product enrichment. They provide structured information for monitoring,
 * debugging, and quality assurance.
 * 
 * <h3>Warning Types</h3>
 * <ul>
 *   <li><strong>FIELD_CONFLICT</strong> - Discrepancy between deterministic and AI values</li>
 *   <li><strong>MISSING_CRITICAL</strong> - Required field is missing</li>
 *   <li><strong>UNIT_AMBIGUITY</strong> - Unclear or ambiguous unit information</li>
 *   <li><strong>BAD_VARIATION_GROUP</strong> - Failed to group product variations</li>
 *   <li><strong>UNSUPPORTED_CLAIM</strong> - Unsupported health or safety claim</li>
 *   <li><strong>INGREDIENT_PARSE_FAIL</strong> - Failed to parse ingredient information</li>
 * </ul>
 * 
 * <p>Warnings are collected by the enrichment pipeline and can be:
 * <ul>
 *   <li>Logged for monitoring and debugging</li>
 *   <li>Stored with the product for transparency</li>
 *   <li>Exported for manual review and correction</li>
 *   <li>Used to trigger alerts or quality gates</li>
 * </ul>
 * 
 * @see com.irondust.search.service.enrichment.EnrichmentPipeline
 * @see com.irondust.search.service.enrichment.EnricherStep
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Warn {
    /** Product ID for identification (format: wc_<productId>) */
    private String productId;
    
    /** Warning code for categorization */
    private String code;
    
    /** Field name if the warning is field-specific */
    private String field;
    
    /** Human-readable warning message */
    private String message;
    
    /** Supporting evidence or context for the warning */
    private String evidence;

    /**
     * Default constructor for JSON deserialization.
     */
    public Warn() {}

    /**
     * Creates a warning with all components.
     * 
     * @param productId Product ID for identification
     * @param code Warning code for categorization
     * @param field Field name if field-specific (can be null)
     * @param message Human-readable warning message
     * @param evidence Supporting evidence or context (can be null)
     */
    public Warn(String productId, String code, String field, String message, String evidence) {
        this.productId = productId;
        this.code = code;
        this.field = field;
        this.message = message;
        this.evidence = evidence;
    }

    // Getters and setters
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

    /**
     * Creates a field conflict warning.
     * 
     * <p>Used when there's a discrepancy between deterministic parsing
     * and AI enrichment for the same field.
     * 
     * @param productId Product ID for identification
     * @param field Field name with the conflict
     * @param detValue Deterministic parsing value
     * @param aiValue AI enrichment value
     * @param evidence Supporting evidence for the conflict
     * @return A field conflict warning
     */
    public static Warn fieldConflict(String productId, String field, String detValue, String aiValue, String evidence) {
        return new Warn(productId, "FIELD_CONFLICT", field, 
            String.format("Deterministic value '%s' conflicts with AI value '%s'", detValue, aiValue), evidence);
    }

    /**
     * Creates a missing critical field warning.
     * 
     * <p>Used when a required field is missing from the product data.
     * 
     * @param productId Product ID for identification
     * @param field Name of the missing field
     * @return A missing critical field warning
     */
    public static Warn missingCritical(String productId, String field) {
        return new Warn(productId, "MISSING_CRITICAL", field, 
            String.format("Critical field '%s' is missing", field), null);
    }

    /**
     * Creates a unit ambiguity warning.
     * 
     * <p>Used when unit information is unclear or ambiguous.
     * 
     * @param productId Product ID for identification
     * @param field Field name with unit ambiguity
     * @param evidence Evidence of the ambiguity
     * @return A unit ambiguity warning
     */
    public static Warn unitAmbiguity(String productId, String field, String evidence) {
        return new Warn(productId, "UNIT_AMBIGUITY", field, 
            String.format("Unit ambiguity in field '%s'", field), evidence);
    }

    /**
     * Creates a bad variation group warning.
     * 
     * <p>Used when product variation grouping fails.
     * 
     * @param productId Product ID for identification
     * @param evidence Evidence of the grouping failure
     * @return A bad variation group warning
     */
    public static Warn badVariationGroup(String productId, String evidence) {
        return new Warn(productId, "BAD_VARIATION_GROUP", null, 
            "Failed to group product variations", evidence);
    }

    /**
     * Creates an unsupported claim warning.
     * 
     * <p>Used when an unsupported health or safety claim is detected.
     * 
     * @param productId Product ID for identification
     * @param claim The unsupported claim
     * @param evidence Evidence of the claim
     * @return An unsupported claim warning
     */
    public static Warn unsupportedClaim(String productId, String claim, String evidence) {
        return new Warn(productId, "UNSUPPORTED_CLAIM", null, 
            String.format("Unsupported claim: %s", claim), evidence);
    }

    /**
     * Creates an ingredient parse failure warning.
     * 
     * <p>Used when ingredient parsing fails.
     * 
     * @param productId Product ID for identification
     * @param evidence Evidence of the parsing failure
     * @return An ingredient parse failure warning
     */
    public static Warn ingredientParseFail(String productId, String evidence) {
        return new Warn(productId, "INGREDIENT_PARSE_FAIL", null, 
            "Failed to parse ingredients", evidence);
    }
}
