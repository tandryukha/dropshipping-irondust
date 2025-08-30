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
        "(\\d+(?:[.,]\\d+)?)\\s*(mg|g|ml)\\s*(?:per|/)?\\s*(serving|portsjon|capsule|cap|kapsel|kaps|kapslid|tablet|tabs|tabletid)", 
        Pattern.CASE_INSENSITIVE
    );
    // Fallback: capture e.g., "(5 g)" close to the word portsjon/serving in the same sentence
    private static final Pattern SERVING_SIZE_FALLBACK = Pattern.compile(
        "portsjon[^.!?\\n]*?\\(\\s*(?:~|≈|u\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\s*\\)",
        Pattern.CASE_INSENSITIVE
    );
    // Reverse order serving size: "per serving 5 g", "serving size 5 g"
    private static final Pattern SERVING_SIZE_REVERSE = Pattern.compile(
        "(per\\s*serving|serving\\s*size|portsjoni\\s*suurus)[^0-9]{0,20}(\\d+(?:[.,]\\d+)?)\\s*(mg|g|ml)",
        Pattern.CASE_INSENSITIVE
    );

    // Broad parentheses grams pattern; will be used only if we can relate it to serving context
    private static final Pattern PAREN_G_PATTERN = Pattern.compile(
        "\\(\\s*(?:~|≈|u\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\s*\\)",
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
        "(\\d{1,4})\\s*(caps|capsules|softgels?|soft gels?|veg\\s*caps|kaps|kapslid|tablets|tabs)",
        Pattern.CASE_INSENSITIVE
    );

    // Units per serving: e.g., "2 capsules per serving", "2 kapslit portsjoni kohta"
    private static final Pattern UNITS_PER_SERVING_PATTERN = Pattern.compile(
        "(\\d{1,3})\\s*(caps|capsule|capsules|softgels?|soft gels?|veg\\s*caps|kaps|kapsel|kapslid|tab|tabs|tablet|tabletid)\\s*(?:per|/)?\\s*(serving|dose|portsjon|annus)",
        Pattern.CASE_INSENSITIVE
    );

    // Unit mass per cap/tab: e.g., "per capsule 500 mg", "per tab 0.5 g"
    private static final Pattern UNIT_MASS_PATTERN = Pattern.compile(
        "(per\\s*(capsule|cap|softgel|soft\\s*gel|veg\\s*caps|kapsel|kaps|kapslid|tablet|tab|tabs)[^0-9]{0,10}|\\bpro\\b[^0-9]{0,10})?(\\d+(?:[.,]\\d+)?)\\s*(mg|g)",
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

        // Parse net weight from attributes first, then fallback to regex or derivation
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

        // Parse unit_count (caps/tabs pieces)
        Integer unitCount = parseUnitCount(raw);
        if (unitCount != null) {
            updates.put("unit_count", unitCount);
            confidence.put("unit_count", 0.9);
            sources.put("unit_count", "attribute|regex");
        }

        // Parse units_per_serving
        Integer ups = parseUnitsPerServing(raw);
        if (ups != null) {
            updates.put("units_per_serving", ups);
            confidence.put("units_per_serving", 0.8);
            sources.put("units_per_serving", "regex");
        }

        // Parse unit_mass_g
        Double umg = parseUnitMassG(raw);
        if (umg != null) {
            updates.put("unit_mass_g", umg);
            confidence.put("unit_mass_g", 0.8);
            sources.put("unit_mass_g", "regex");
        }

        // Infer form from unit-related evidence if still missing (late fallback)
        if (!updates.containsKey("form") && (soFar.getForm() == null || soFar.getForm().isBlank())) {
            Integer ucHint = (Integer) updates.getOrDefault("unit_count", soFar.getUnit_count());
            boolean hasUnitEvidence = ucHint != null || ups != null || umg != null;
            if (hasUnitEvidence) {
                String text = (raw.getName() + " " + (raw.getSearch_text() != null ? raw.getSearch_text() : "")).toLowerCase();
                boolean looksTabs = text.contains("tablet") || text.contains("tabletid") || text.contains("tab ") || text.contains(" tabs");
                updates.put("form", looksTabs ? "tabs" : "capsules");
                confidence.put("form", 0.55);
                sources.put("form", "unit_evidence");
            }
        }

        // Sanity-correct net_weight_g when it equals serving_size or is clearly too small
        Double existingWeight = (Double) updates.getOrDefault("net_weight_g", soFar.getNet_weight_g());
        if (existingWeight != null) {
            Double ss = servingSize;
            Integer sv = (Integer) updates.getOrDefault("servings", soFar.getServings());
            Integer svMax = (Integer) updates.getOrDefault("servings_max", soFar.getServings_max());
            Integer svMin = (Integer) updates.getOrDefault("servings_min", soFar.getServings_min());
            Integer usedServings = sv != null ? sv : (svMax != null ? svMax : svMin);

            if (ss != null && usedServings != null && usedServings > 0) {
                double candidate = ss * usedServings;
                // If recorded weight looks like a per-serving weight, or far below expected total, correct it
                if (existingWeight <= ss * 1.5 || existingWeight < candidate * 0.6) {
                    double ratio = candidate > 0 ? candidate / Math.max(existingWeight, 1e-6) : 0.0;
                    updates.put("net_weight_g", candidate);
                    confidence.put("net_weight_g", 0.80);
                    sources.put("net_weight_g", "corrected");
                    // Suppress noise for clear per-serving mistakes (existing equals serving size)
                    boolean perServingMistake = Math.abs(existingWeight - ss) <= Math.max(0.2 * ss, 0.5);
                    boolean strongEvidence = usedServings >= 10; // typical realistic packaging
                    // Only emit ambiguity warning for extreme corrections that are not obvious per-serving mistakes
                    boolean shouldWarn = !perServingMistake && (ratio >= 8.0 || existingWeight < ss * 0.25) && !strongEvidence;
                    if (shouldWarn) {
                        warnings.add(Warn.unitAmbiguity(raw.getId(), "net_weight_g",
                                "Corrected net weight from " + existingWeight + "g to " + candidate + "g based on serving size × servings"));
                    }
                }
            }
        }

        // Derive net_weight_g from servings x serving_size_g if still missing
        if (!updates.containsKey("net_weight_g")) {
            Double ss = servingSize;
            Integer sv = (Integer) updates.getOrDefault("servings", soFar.getServings());
            if (ss != null && sv != null && sv > 0) {
                double derived = ss * sv;
                if (derived > 0 && derived <= 100000) {
                    updates.put("net_weight_g", derived);
                    confidence.put("net_weight_g", 0.75);
                    sources.put("net_weight_g", "derived");
                }
            }
        }

        // Derive net_weight_g from unit_count x unit_mass_g if still missing
        if (!updates.containsKey("net_weight_g")) {
            Integer effectiveUnitCount = (Integer) updates.getOrDefault("unit_count", soFar.getUnit_count());
            Double effectiveUnitMassG = (Double) updates.getOrDefault("unit_mass_g", soFar.getUnit_mass_g());
            if (effectiveUnitCount != null && effectiveUnitCount > 0 && effectiveUnitMassG != null && effectiveUnitMassG > 0) {
                double derived = effectiveUnitCount * effectiveUnitMassG;
                if (derived > 0 && derived <= 100000) {
                    updates.put("net_weight_g", derived);
                    confidence.put("net_weight_g", 0.7);
                    sources.put("net_weight_g", "derived_units");
                }
            }
        }

        // Sanity-correct servings when regex-derived value conflicts with size/weight
        // Goal: prevent tiny/huge servings that explode price-per-serving.
        try {
            Integer currentServings = (Integer) updates.getOrDefault("servings", soFar.getServings());
            Double ss = (Double) updates.getOrDefault("serving_size_g", soFar.getServing_size_g());
            Double nw = (Double) updates.getOrDefault("net_weight_g", soFar.getNet_weight_g());
            // Only attempt when we have both size and weight, and an existing servings value
            if (currentServings != null && currentServings > 0 && ss != null && ss > 0 && nw != null && nw > 0) {
                double expected = nw / ss;
                if (expected > 0 && expected < 2000) {
                    int rounded = (int) Math.round(expected);
                    // Consider it a conflict if relative error > 40% or servings looks unrealistic
                    double relErr = Math.abs(rounded - currentServings) / Math.max(1.0, (double) currentServings);
                    boolean unrealistic = currentServings < 8 || currentServings > 180;
                    if (relErr > 0.4 || unrealistic) {
                        updates.put("servings", rounded);
                        confidence.put("servings", Math.max(confidence.getOrDefault("servings", 0.8), 0.85));
                        sources.put("servings", (sources.containsKey("servings") ? sources.get("servings") + "|" : "") + "corrected");
                        // Do not emit a unit ambiguity warning for servings correction to reduce noise
                    }
                }
            }
        } catch (Exception ignored) {}

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

        // Collect all candidates and pick the most plausible (prefer g/ml; ignore tiny mg values)
        Double best = null;
        Matcher matcher = SERVING_SIZE_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1).replace(",", "."));
                String unit = matcher.group(2).toLowerCase();
                double grams = unit.equals("mg") ? value / 1000.0 : value;
                if (unit.equals("mg") && grams < 1.0) continue;
                if (grams <= 0 || grams > 500) continue;
                if (best == null || grams > best) best = grams;
            } catch (NumberFormatException e) {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "serving_size_g",
                        "Failed to parse serving size from text: " + matcher.group()));
            }
        }
        // Reverse order pattern
        Matcher rev = SERVING_SIZE_REVERSE.matcher(text);
        while (rev.find()) {
            try {
                double value = Double.parseDouble(rev.group(2).replace(",", "."));
                String unit = rev.group(3).toLowerCase();
                double grams = unit.equals("mg") ? value / 1000.0 : value;
                if (unit.equals("mg") && grams < 1.0) continue;
                if (grams <= 0 || grams > 500) continue;
                if (best == null || grams > best) best = grams;
            } catch (NumberFormatException ignored) {}
        }
        if (best != null) return best;

        // Fallback pattern (explicit (X g) near portsjon)
        Matcher fb = SERVING_SIZE_FALLBACK.matcher(text);
        if (fb.find()) {
            try {
                double value = Double.parseDouble(fb.group(1).replace(",", "."));
                if (value > 0 && value <= 500) return value;
            } catch (NumberFormatException e) {
                warnings.add(Warn.unitAmbiguity(raw.getId(), "serving_size_g",
                        "Failed to parse fallback serving size: " + fb.group()));
            }
        }

        // Final heuristic: pick grams in parentheses if occurring within ~60 chars of 'portsjon'
        int idxPortsjon = text.toLowerCase().indexOf("portsjon");
        if (idxPortsjon >= 0) {
            Matcher pm = PAREN_G_PATTERN.matcher(text);
            Double nearBest = null;
            while (pm.find()) {
                int start = pm.start();
                if (Math.abs(start - idxPortsjon) <= 120) { // within 120 chars window around 'portsjon'
                    try {
                        double value = Double.parseDouble(pm.group(1).replace(",", "."));
                        if (value > 0 && value <= 500) {
                            if (nearBest == null || value > nearBest) nearBest = value;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (nearBest != null) return nearBest;
        }
        return null;
    }

    private Integer parseUnitCount(RawProduct raw) {
        // Attribute: tablettide-arv or any taxonomy that implies count of units
        if (raw.getDynamic_attrs() != null) {
            // Known key
            List<String> cnt = raw.getDynamic_attrs().get("attr_pa_tablettide-arv");
            if (cnt != null && !cnt.isEmpty()) {
                try { int v = Integer.parseInt(cnt.get(0)); if (v > 0 && v <= 5000) return v; } catch (Exception ignored) {}
            }
            // Broad scan of other possible keys
            for (Map.Entry<String, List<String>> e : raw.getDynamic_attrs().entrySet()) {
                String k = e.getKey() != null ? e.getKey().toLowerCase() : "";
                if (!(k.contains("tablett") || k.contains("tabs") || k.contains("tab") || k.contains("kaps") || k.contains("caps"))) continue;
                List<String> vals = e.getValue();
                if (vals == null || vals.isEmpty()) continue;
                String first = vals.get(0);
                try { int v = Integer.parseInt(first); if (v > 0 && v <= 5000) return v; } catch (Exception ignored) {}
            }
        }
        // Pattern from title/text like "60 caps", "120 tablets"
        String text = (raw.getName() + " " + (raw.getSearch_text() != null ? raw.getSearch_text() : "")).toLowerCase();
        Matcher m = CAPS_SERVINGS_PATTERN.matcher(text);
        if (m.find()) {
            try { int v = Integer.parseInt(m.group(1)); if (v > 0 && v <= 5000) return v; } catch (Exception ignored) {}
        }
        return null;
    }

    private Integer parseUnitsPerServing(RawProduct raw) {
        String text = (raw.getName() + " " + (raw.getSearch_text() != null ? raw.getSearch_text() : "")).toLowerCase();
        Matcher m = UNITS_PER_SERVING_PATTERN.matcher(text);
        if (m.find()) {
            try { int v = Integer.parseInt(m.group(1)); if (v > 0 && v <= 50) return v; } catch (Exception ignored) {}
        }
        return null;
    }

    private Double parseUnitMassG(RawProduct raw) {
        String text = (raw.getName() + " " + (raw.getSearch_text() != null ? raw.getSearch_text() : "")).toLowerCase();
        Matcher m = UNIT_MASS_PATTERN.matcher(text);
        if (m.find()) {
            try {
                double v = Double.parseDouble(m.group(3).replace(",", "."));
                String unit = m.group(4).toLowerCase();
                if ("mg".equals(unit)) return v / 1000.0;
                return v;
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public List<Warn> getWarnings() {
        return warnings;
    }
}
