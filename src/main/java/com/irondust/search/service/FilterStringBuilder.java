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

            // Support synthetic suffix keys like price_min/price_max → map to base field with comparison
            if (field != null && field.endsWith("_min")) {
                String base = field.substring(0, field.length() - 4); // drop _min
                if (value instanceof Map<?,?> mv) {
                    Object v = mv.get("value");
                    if (v != null) andClauses.add(comparisonExpr(base, ">=", v));
                } else if (value != null) {
                    andClauses.add(comparisonExpr(base, ">=", value));
                }
                continue;
            }
            if (field != null && field.endsWith("_max")) {
                String base = field.substring(0, field.length() - 4); // drop _max
                if (value instanceof Map<?,?> mv) {
                    Object v = mv.get("value");
                    if (v != null) andClauses.add(comparisonExpr(base, "<=", v));
                } else if (value != null) {
                    andClauses.add(comparisonExpr(base, "<=", value));
                }
                continue;
            }

            if (value instanceof List<?> listVal) {
                // If this is a list of comparison maps, AND each comparison for the same field
                if (!listVal.isEmpty() && listVal.get(0) instanceof Map<?,?>) {
                    for (Object o : listVal) {
                        if (o instanceof Map<?,?> mv) {
                            Object op = mv.get("op");
                            Object val = mv.get("value");
                            if (op != null && val != null) {
                                andClauses.add(comparisonExpr(field, String.valueOf(op), val));
                            }
                        }
                    }
                } else {
                    String inList = toInList(listVal);
                    if (inList != null) andClauses.add(field + " IN " + inList);
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
        // Meilisearch filter syntax expects string values in double quotes
        return field + " = \"" + escape(String.valueOf(v)) + "\"";
    }

    private static String comparisonExpr(String field, String op, Object v) {
        String operator = switch (op) {
            case ">=", "<=", ">", "<", "=", "!=", "NOT" -> op;
            default -> "=";
        };
        if (v instanceof Boolean || v instanceof Number) {
            return field + " " + operator + " " + v;
        }
        // Meilisearch filter syntax expects string values in double quotes
        return field + " " + operator + " \"" + escape(String.valueOf(v)) + "\"";
    }

    private static String escape(String s) {
        // Escape backslashes first, then double quotes
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toInList(List<?> list) {
        if (list == null || list.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (Object v : list) {
            if (v == null) continue;
            if (v instanceof Boolean || v instanceof Number) {
                parts.add(String.valueOf(v));
            } else {
                parts.add("\"" + escape(String.valueOf(v)) + "\"");
            }
        }
        if (parts.isEmpty()) return null;
        return "[" + String.join(", ", parts) + "]";
    }
}



