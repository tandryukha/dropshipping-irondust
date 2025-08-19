package com.irondust.search.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class IngestDtos {
    public static class TargetedIngestRequest {
        @NotNull
        @NotEmpty
        private List<Long> ids; // Woo product numeric IDs

        public List<Long> getIds() { return ids; }
        public void setIds(List<Long> ids) { this.ids = ids; }
    }
}




