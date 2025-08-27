# Goals Modeling (Taxonomy + AI)

This document describes how fitness/health goals are derived and scored in the index.

## Canonical Goals

We use a fixed vocabulary:

- preworkout
- strength
- endurance
- lean_muscle
- recovery
- weight_loss
- wellness

These are not raw WooCommerce fields; they are derived, canonical tags used for filtering and ranking.

## Deterministic Taxonomy (baseline)

- Source signals:
  - Woo categories (names/slugs)
  - Product text (`search_text`)
  - Dynamic attributes (e.g., `attr_pa_milleks`)
- Extraction: `TaxonomyParser` matches multilingual keywords/phrases to canonical goals.
- Output:
  - `goal_tags` (array)
  - Baseline goal scores for matched goals: `goal_*_score = 0.7`
  - Default 0.0 scores for all goals to ensure explicit presence in every document

Benefits:
- Fast, cheap, deterministic and explainable
- Always-on (works without AI)
- Provides seeds that AI can refine

## AI Refinement (optional)

- Prompt extended to return `goal_scores` for all goals:
  - `goal_scores.{goal} = { score (0..1), confidence (0..1) }`
- Application rules (in pipeline):
  - Accept if `confidence ≥ AI_GOAL_CONF_THRESHOLD` (default 0.7)
  - Only override when AI score > existing score (baseline or prior AI)
- AI continues to fill `goal_tags` when missing, but deterministic tags are preferred when present.

## Indexed Schema (partial)

- `goal_tags: string[]`
- `goal_preworkout_score: number`
- `goal_strength_score: number`
- `goal_endurance_score: number`
- `goal_lean_muscle_score: number`
- `goal_recovery_score: number`
- `goal_weight_loss_score: number`
- `goal_wellness_score: number`

Index settings:
- Filterable: `goal_tags`
- Sortable: all `goal_*_score` fields

## Search Behavior

- Filters: UI applies `goal_tags` (and keeps `in_stock=true` by default)
- Sorting:
  - When exactly one goal is selected, backend sets sort to `goal_{g}_score:desc`
  - When multiple goals are selected, future work: combine with weighted rerank

## Configuration

- `AI_GOAL_CONF_THRESHOLD` (env): confidence threshold for accepting AI goal scores; default `0.7`
- AI enablement: `OPENAI_API_KEY`, `AI_ENRICH=true`

## Rationale: Keep Taxonomy + AI

- Deterministic baseline guarantees usable facets and ranking without AI
- AI augments with nuanced scores and fills gaps when confident
- Clear provenance and guardrails reduce hallucinations and keep UX stable
