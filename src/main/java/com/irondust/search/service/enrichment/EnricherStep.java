package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.List;

public interface EnricherStep {
    /**
     * Check if this enricher can process the given product
     */
    boolean supports(RawProduct raw);

    /**
     * Apply enrichment to the product, returning partial updates
     */
    EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar);

    /**
     * Get any warnings generated during processing
     */
    List<Warn> getWarnings();

    /**
     * Get the name of this enricher for logging
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
