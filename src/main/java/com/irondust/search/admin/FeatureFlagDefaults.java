package com.irondust.search.admin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FeatureFlagDefaults {
    private FeatureFlagDefaults() {}

    public static Map<String, Boolean> defaults() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        // Core flags
        m.put("ai_search", Boolean.TRUE);
        return m;
    }
}


