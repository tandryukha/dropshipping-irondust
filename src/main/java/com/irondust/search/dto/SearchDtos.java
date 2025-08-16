package com.irondust.search.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public class SearchDtos {
    public static class SearchRequestBody {
        private String q;
        private Map<String, Object> filters;
        private List<String> sort; // e.g., ["price_cents:asc"]
        @NotNull @Min(1)
        private Integer page = 1;
        @NotNull @Min(1)
        private Integer size = 24;

        public String getQ() { return q; }
        public void setQ(String q) { this.q = q; }
        public Map<String, Object> getFilters() { return filters; }
        public void setFilters(Map<String, Object> filters) { this.filters = filters; }
        public List<String> getSort() { return sort; }
        public void setSort(List<String> sort) { this.sort = sort; }
        public Integer getPage() { return page; }
        public void setPage(Integer page) { this.page = page; }
        public Integer getSize() { return size; }
        public void setSize(Integer size) { this.size = size; }
    }

    public static class SearchResponseBody<T> {
        private List<T> items;
        private long total;
        private Map<String, Map<String, Integer>> facets;

        public List<T> getItems() { return items; }
        public void setItems(List<T> items) { this.items = items; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public Map<String, Map<String, Integer>> getFacets() { return facets; }
        public void setFacets(Map<String, Map<String, Integer>> facets) { this.facets = facets; }
    }
}



