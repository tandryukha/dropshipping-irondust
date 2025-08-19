package com.irondust.search.service.enrichment;

import com.irondust.search.model.RawProduct;
import com.irondust.search.model.ParsedProduct;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnitParser implements EnricherStep {
    private final List<Warn> warnings = new ArrayList<>();

    // Regex patterns for extracting units
    private static final Pattern SERVING_SIZE_PATTERN = Pattern.compile(
        "(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\s*(?:per|/)?\\s*(serving|portsjon)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SERVINGS_PATTERN = Pattern.compile(
        "(\\d+)\\s*(servings|annust|portsjonit|порций)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern NET_WEIGHT_PATTERN = Pattern.compile(
        "(\\d+(?:[.,]\\d+)?)\\s*(kg|g|l|ml)\\b", 
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean supports(RawProduct raw) {
        return true; // UnitParser supports all products
    }

    @Override
    public EnrichmentDelta apply(RawProduct raw, ParsedProduct soFar) {
        Map<String, Object> updates = new HashMap<>();
        Map<String, Double> confidence = new HashMap<>();
        Map<String, String> sources = new HashMap<>();

        // Parse net weight from attributes first, then fallback to regex
        Double netWeight = parseNetWeight(raw);
        if (netWeight != null) {
            updates.put("net_weight_g", netWeight);
            confidence.put("net_weight_g", 0.95);
            sources.put("net_weight_g", "attribute");
        } else {
            netWeight = parseNetWeightFromText(raw);
            if (netWeight != null) {
                updates.put("net_weight_g", netWeight);
                confidence.put("net_weight_g", 0.8);
                sources.put("net_weight_g", "regex");
            }
        }

        // Parse servings from attributes first, then fallback to regex
        Integer servings = parseServings(raw);
        if (servings != null) {
            updates.put("servings", servings);
            confidence.put("servings", 0.95);
            sources.put("servings", "attribute");
        } else {
            servings = parseServingsFromText(raw);
            if (servings != null) {
                updates.put("servings", servings);
                confidence.put("servings", 0.8);
                sources.put("servings", "regex");
            }
        }

        // Parse serving size from text
        Double servingSize = parseServingSizeFromText(raw);
        if (servingSize != null) {
            updates.put("serving_size_g", servingSize);
            confidence.put("serving_size_g", 0.8);
            sources.put("serving_size_g", "regex");
        }

        return new EnrichmentDelta(updates, confidence, sources, null);
    }

    private Double parseNetWeight(RawProduct raw) {
        if (raw.getDynamic_attrs() == null) return null;
        
        List<String> weightAttrs = raw.getDynamic_attrs().get("attr_pa_grammide-arv");
        if (weightAttrs != null && !weightAttrs.isEmpty()) {
            try {
                String weightStr = weightAttrs.get(0).replace(",", ".");
                return Double.parseDouble(weightStr);
            } catch (NumberFormatException e) {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "net_weight_g", 
                    "Failed to parse weight attribute: " + weightAttrs.get(0)));
            }
        }
        return null;
    }

    private Double parseNetWeightFromText(RawProduct raw) {
        String text = raw.getSearch_text();
        if (text == null) return null;

        Matcher matcher = NET_WEIGHT_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1).replace(",", "."));
                String unit = matcher.group(2).toLowerCase();
                
                // Convert to grams
                if (unit.equals("kg")) {
                    return value * 1000;
                } else if (unit.equals("l")) {
                    return value * 1000; // Assume 1:1 conversion for now
                } else {
                    return value; // Already in g or ml
                }
            } catch (NumberFormatException e) {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "net_weight_g", 
                    "Failed to parse weight from text: " + matcher.group()));
            }
        }
        return null;
    }

    private Integer parseServings(RawProduct raw) {
        if (raw.getDynamic_attrs() == null) return null;
        
        List<String> servingsAttrs = raw.getDynamic_attrs().get("attr_pa_portsjonite-arv");
        if (servingsAttrs != null && !servingsAttrs.isEmpty()) {
            try {
                return Integer.parseInt(servingsAttrs.get(0));
            } catch (NumberFormatException e) {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "servings", 
                    "Failed to parse servings attribute: " + servingsAttrs.get(0)));
            }
        }
        return null;
    }

    private Integer parseServingsFromText(RawProduct raw) {
        String text = raw.getSearch_text();
        if (text == null) return null;

        Matcher matcher = SERVINGS_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "servings", 
                    "Failed to parse servings from text: " + matcher.group()));
            }
        }
        return null;
    }

    private Double parseServingSizeFromText(RawProduct raw) {
        String text = raw.getSearch_text();
        if (text == null) return null;

        Matcher matcher = SERVING_SIZE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1).replace(",", "."));
                String unit = matcher.group(2).toLowerCase();
                
                // Convert to grams (assume 1:1 for ml for now)
                return value;
            } catch (NumberFormatException e) {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "serving_size_g", 
                    "Failed to parse serving size from text: " + matcher.group()));
            }
        }
        return null;
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
