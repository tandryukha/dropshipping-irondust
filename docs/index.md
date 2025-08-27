# IronDust Dropshipping Search

Spring Boot API that ingests WooCommerce Store API data into Meilisearch and provides enriched, searchable product information for irondust.eu.

## Architecture at a Glance

```mermaid
graph TB
    subgraph "WooCommerce Store"
        WC["WooCommerce Products"]
    end

    subgraph "Enrichment Pipeline"
        subgraph "Stage 1: Raw Data"
            RP["RawProduct\nRaw WooCommerce data"]
        end

        subgraph "Stage 2: Deterministic Parsing"
            PP["ParsedProduct\nRule-based enrichment"]
        end

        subgraph "Stage 3: AI Enrichment"
            EP["EnrichedProduct\nAI-generated content"]
        end
    end

    subgraph "Enrichment Steps"
        N["Normalizer\nLocale/slug mappings"]
        UP["UnitParser\nExtract units"]
        SC["ServingCalculator\nCompute servings"]
        PC["PriceCalculator\nDerived prices"]
        TP["TaxonomyParser\nGoal/diet tags"]
        VG["VariationGrouper\nProduct grouping"]
    end

    subgraph "Search Engine"
        MS["Meilisearch\nSearch index"]
    end

    subgraph "Frontend"
        UI["React UI\nProduct search"]
    end

    WC --> RP
    RP --> N
    N --> UP
    UP --> SC
    SC --> PC
    PC --> TP
    TP --> VG
    VG --> PP
    PP --> EP
    EP --> MS
    MS --> UI

    style RP fill:#e1f5fe
    style PP fill:#f3e5f5
    style EP fill:#e8f5e8
    style MS fill:#fff3e0
```

## Quick Start

```bash
./rebuild-and-watch.sh
```

Once running: API http://localhost:4000, Meilisearch http://localhost:7700.

## Next Steps

- See Getting Started for local setup details
- Explore the Architecture for enrichment and indexing design
- Use the API reference to ingest and search


