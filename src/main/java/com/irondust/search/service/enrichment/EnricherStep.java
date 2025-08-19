package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.List;

/**
 * Interface for enrichment steps in the product enrichment pipeline.
 * 
 * <p>Each enrichment step implements this interface to provide a consistent
 * way to process raw product data and add enriched fields. The interface
 * supports both deterministic parsing steps and AI enrichment steps.
 * 
 * <h3>Enrichment Step Lifecycle</h3>
 * <ol>
 *   <li><strong>supports()</strong> - Check if this step can process the product</li>
 *   <li><strong>apply()</strong> - Apply enrichment and return field updates</li>
 *   <li><strong>getWarnings()</strong> - Get any warnings generated during processing</li>
 * </ol>
 * 
 * <h3>Implementation Guidelines</h3>
 * <ul>
 *   <li>Steps should be <strong>idempotent</strong> - running multiple times produces same result</li>
 *   <li>Steps should be <strong>deterministic</strong> - same input always produces same output</li>
 *   <li>Steps should <strong>not modify</strong> the input RawProduct</li>
 *   <li>Steps should return <strong>partial updates</strong> via EnrichmentDelta</li>
 *   <li>Steps should <strong>log warnings</strong> for issues and conflicts</li>
 * </ul>
 * 
 * <p>Examples of enrichment steps:
 * <ul>
 *   <li><strong>Normalizer</strong> - Convert locale-specific values to canonical forms</li>
 *   <li><strong>UnitParser</strong> - Extract and normalize units from text</li>
 *   <li><strong>PriceCalculator</strong> - Compute derived price fields</li>
 *   <li><strong>AIEnricher</strong> - Generate content using AI models</li>
 * </ul>
 * 
 * @see EnrichmentDelta
 * @see Warn
 * @see com.irondust.search.service.enrichment.EnrichmentPipeline
 */
public interface EnricherStep {
    /**
     * Checks if this enrichment step can process the given product.
     * 
     * <p>This method should perform a quick check to determine if the step
     * is applicable to the product. For example, a unit parser might check
     * if the product has any text content to parse.
     * 
     * @param raw The raw product data to check
     * @return true if this step can process the product, false otherwise
     */
    boolean supports(RawProduct raw);

    /**
     * Applies enrichment to the product and returns partial field updates.
     * 
     * <p>This method processes the raw product data and the current state
     * of the parsed product to generate enriched fields. The method should
     * not modify the input parameters directly, but instead return an
     * EnrichmentDelta containing the updates to apply.
     * 
     * <p>The method can access both the raw product data and the current
     * state of the parsed product to make enrichment decisions. For example,
     * a price calculator might need both the raw price data and the parsed
     * servings to calculate price per serving.
     * 
     * @param raw The raw product data from WooCommerce
     * @param soFar The current state of the parsed product (may be partially populated)
     * @return An EnrichmentDelta containing field updates, confidence scores, and provenance
     */
    EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar);

    /**
     * Gets any warnings generated during the enrichment process.
     * 
     * <p>This method should return warnings about issues encountered during
     * processing, such as missing data, ambiguous values, or conflicts.
     * Warnings are collected by the pipeline for monitoring and debugging.
     * 
     * <p>The warnings should be specific and actionable, including:
     * <ul>
     *   <li>Product ID for identification</li>
     *   <li>Warning code for categorization</li>
     *   <li>Field name if applicable</li>
     *   <li>Descriptive message</li>
     *   <li>Evidence or context</li>
     * </ul>
     * 
     * @return A list of warnings from this enrichment step
     */
    List<Warn> getWarnings();

    /**
     * Gets the name of this enrichment step for logging and debugging.
     * 
     * <p>This method provides a human-readable name for the enrichment step
     * that can be used in logs and error messages. The default implementation
     * returns the simple class name.
     * 
     * @return The name of this enrichment step
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
