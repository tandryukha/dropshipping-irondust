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
            RP["RawProduct<br/>Raw WooCommerce data"]
        end

        subgraph "Stage 2: Deterministic Parsing"
            PP["ParsedProduct<br/>Rule-based enrichment"]
        end

        subgraph "Stage 3: AI Enrichment"
            EP["EnrichedProduct<br/>AI-generated content"]
        end
    end

    subgraph "Enrichment Steps"
        N["Normalizer<br/>Locale/slug mappings"]
        UP["UnitParser<br/>Extract units"]
        SC["ServingCalculator<br/>Compute servings"]
        PC["PriceCalculator<br/>Derived prices"]
        TP["TaxonomyParser<br/>Goal/diet tags"]
        VG["VariationGrouper<br/>Product grouping"]
    end

    subgraph "Search Engine"
        MS["Meilisearch<br/>Search index"]
    end

    subgraph "Frontend"
        UI["React UI<br/>Product search"]
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


