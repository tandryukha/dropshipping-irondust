package com.irondust.search.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FilterStringBuilder {
    public static String build(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return null;
        List<String> andClauses = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof List<?> listVal) {
                List<String> orClauses = new ArrayList<>();
                for (Object v : listVal) {
                    orClauses.add(equalsExpr(field, v));
                }
                if (!orClauses.isEmpty()) {
                    andClauses.add("(" + String.join(" OR ", orClauses) + ")");
                }
            } else if (value instanceof Map<?, ?> mapVal) {
                Object op = mapVal.get("op");
                Object val = mapVal.get("value");
                if (op != null && val != null) {
                    andClauses.add(comparisonExpr(field, op.toString(), val));
                }
            } else {
                andClauses.add(equalsExpr(field, value));
            }
        }
        return andClauses.isEmpty() ? null : String.join(" AND ", andClauses);
    }

    private static String equalsExpr(String field, Object v) {
        if (v instanceof Boolean || v instanceof Number) {
            return field + " = " + v;
        }
        return field + " = '" + escape(String.valueOf(v)) + "'";
    }

    private static String comparisonExpr(String field, String op, Object v) {
        String operator = switch (op) {
            case ">=", "<=", ">", "<", "=", "!=", "NOT" -> op;
            default -> "=";
        };
        if (v instanceof Boolean || v instanceof Number) {
            return field + " " + operator + " " + v;
        }
        return field + " " + operator + " '" + escape(String.valueOf(v)) + "'";
    }

    private static String escape(String s) {
        return s.replace("'", "\\'");
    }
}



