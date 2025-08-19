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

        // Missing criticals
        if (soFar.getServings() == null) warnings.add(Warn.missingCritical(raw.getId(), "servings"));
        if (soFar.getNet_weight_g() == null) warnings.add(Warn.missingCritical(raw.getId(), "net_weight_g"));
        if (soFar.getForm() == null) warnings.add(Warn.missingCritical(raw.getId(), "form"));

        return new EnrichmentDelta();
    }

    @Override
    public List<Warn> getWarnings() { return warnings; }
}


