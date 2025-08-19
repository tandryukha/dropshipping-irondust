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
    // Fallback: capture e.g., "(5 g)" close to the word portsjon/serving in the same sentence
    private static final Pattern SERVING_SIZE_FALLBACK = Pattern.compile(
        "portsjon[^.!?\n]*?\\(\\s*(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\s*\\)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SERVINGS_PATTERN = Pattern.compile(
        "(\\d+)\\s*(servings|annust|portsjonit|порций)", 
        Pattern.CASE_INSENSITIVE
    );
    // Range pattern: e.g., "30-60 servings", "30–60 portsjonit", "30 to 60 servings"
    private static final Pattern SERVINGS_RANGE_PATTERN = Pattern.compile(
        "(\\d{1,4})\\s*(?:-|–|to)\\s*(\\d{1,4})\\s*(servings|annust|portsjonit|порций)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern NET_WEIGHT_PATTERN = Pattern.compile(
        "(\\d+(?:[.,]\\d+)?)\\s*(kg|g|l|ml)\\b", 
        Pattern.CASE_INSENSITIVE
    );

    // Capsules/tablets servings pattern (title/text): e.g., "60 caps", "120 tablets"
    private static final Pattern CAPS_SERVINGS_PATTERN = Pattern.compile(
        "(\\d{1,4})\\s*(caps|capsules|kaps|kapslid|tablets|tabs)",
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
            // Try range first
            Integer[] srvRange = parseServingsRangeFromText(raw);
            if (srvRange != null) {
                updates.put("servings_min", srvRange[0]);
                updates.put("servings_max", srvRange[1]);
                confidence.put("servings_min", 0.85);
                confidence.put("servings_max", 0.85);
                sources.put("servings_min", "regex");
                sources.put("servings_max", "regex");
            } else {
                // Fallback to exact single value patterns
                servings = parseServingsFromText(raw);
                if (servings != null) {
                    updates.put("servings", servings);
                    confidence.put("servings", 0.8);
                    sources.put("servings", "regex");
                } else {
                // Capsule/tablet specific: from attribute count or caps pattern, only if product is capsules/tabs
                Integer caps = parseServingsForCapsOrTabs(raw, soFar);
                if (caps != null) {
                    updates.put("servings", caps);
                    confidence.put("servings", 0.9);
                    sources.put("servings", "attribute|regex");
                }
                }
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

    private Integer[] parseServingsRangeFromText(RawProduct raw) {
        String text = raw.getSearch_text();
        if (text == null) return null;
        Matcher matcher = SERVINGS_RANGE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int a = Integer.parseInt(matcher.group(1));
                int b = Integer.parseInt(matcher.group(2));
                int min = Math.min(a, b);
                int max = Math.max(a, b);
                if (min > 0 && max <= 2000 && min <= max) {
                    return new Integer[] { min, max };
                }
            } catch (NumberFormatException e) {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "servings", 
                    "Failed to parse servings range from text: " + matcher.group()));
            }
        }
        return null;
    }

    private Integer parseServingsForCapsOrTabs(RawProduct raw, ParsedProduct soFar) {
        // Try attribute-based count regardless of normalized form; the presence of this
        // attribute implies capsules/tablets packaging
        if (raw.getDynamic_attrs() != null) {
            List<String> cnt = raw.getDynamic_attrs().get("attr_pa_tablettide-arv");
            if (cnt != null && !cnt.isEmpty()) {
                try {
                    int v = Integer.parseInt(cnt.get(0));
                    if (v > 0 && v <= 2000) return v;
                } catch (NumberFormatException ignored) {}
            }
        }
        // Fallback from title/text pattern like "60 caps", "120 tablets", etc.,
        // even if form wasn't normalized yet
        String text = (raw.getName() + " " + (raw.getSearch_text() != null ? raw.getSearch_text() : "")).toLowerCase();
        Matcher m = CAPS_SERVINGS_PATTERN.matcher(text);
        if (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v > 0 && v <= 2000) return v;
            } catch (NumberFormatException ignored) {}
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
        // Fallback pattern
        Matcher fb = SERVING_SIZE_FALLBACK.matcher(text);
        if (fb.find()) {
            try {
                double value = Double.parseDouble(fb.group(1).replace(",", "."));
                return value;
            } catch (NumberFormatException e) {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "serving_size_g", 
                    "Failed to parse fallback serving size: " + fb.group()));
            }
        }
        return null;
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
