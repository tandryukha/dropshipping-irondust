package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;

/**
 * Scans for simple contradictions between attributes and text-derived fields.
 * Emits WARN entries but does not modify fields.
 */
public class ConflictDetector implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    @Override
    public boolean supports(RawProduct raw) { return true; }

    @Override
    public EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar) {
        // Example: attribute says capsules but title mentions powder
        if (soFar.getForm() != null && raw.getName() != null) {
            String name = raw.getName().toLowerCase();
            String form = soFar.getForm();
            if (form.equals("capsules") && name.contains("powder")) {
                warnings.add(Warn.fieldConflict(raw.getId(), "form", "capsules", "powder",
                        "Title mentions powder while attribute maps to capsules"));
            }
            if (form.equals("powder") && (name.contains("capsule") || name.contains("tablet"))) {
                warnings.add(Warn.fieldConflict(raw.getId(), "form", "powder", "capsules/tabs",
                        "Title mentions capsules/tabs while attribute maps to powder"));
            }
        }

        // Missing criticals: allow either exact servings or a range to satisfy
        // For count-based forms (capsules/tabs), do not require servings unless units_per_serving is present
        boolean isCountBased = "capsules".equals(soFar.getForm()) || "tabs".equals(soFar.getForm());
        boolean hasServingRange = (soFar.getServings_min() != null && soFar.getServings_max() != null);
        if (!isCountBased) {
            if (soFar.getServings() == null && !hasServingRange) {
                warnings.add(Warn.missingCritical(raw.getId(), "servings"));
            }
        } else {
            // For capsules/tabs, only warn if units_per_serving is available (meaning servings is expected) but missing
            if (soFar.getUnits_per_serving() != null && soFar.getServings() == null && !hasServingRange) {
                warnings.add(Warn.missingCritical(raw.getId(), "servings"));
            }
        }
        // net_weight_g is critical for weight-/volume-based forms (e.g., powder, drink, gel, bar)
        // but not for unit-count forms (capsules, tabs)
        String form = soFar.getForm();
        isCountBased = "capsules".equals(form) || "tabs".equals(form);
        if (soFar.getNet_weight_g() == null && !isCountBased) {
            warnings.add(Warn.missingCritical(raw.getId(), "net_weight_g"));
        }
        if (form == null) warnings.add(Warn.missingCritical(raw.getId(), "form"));

        return new EnrichmentDelta();
    }

    @Override
    public List<Warn> getWarnings() { return warnings; }
}


