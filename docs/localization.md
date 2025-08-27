Short answer: yes—it fits. Your current pipeline (deterministic parsers → single LLM pass → Meili + vectors) extends cleanly to RU/ET later with a couple of i18n tweaks. You don’t need to re-architect.

Here’s how to future-proof it now and switch languages on when ready:

What stays the same
	•	Language-agnostic core (facts & numbers): form, servings, net_weight_g, price_per_serving, diet/goal tags, parent_id, alternatives logic, vector constraints—all unchanged.
	•	One LLM pass per product for validation/fill + flags—still one pass. (Facts are language-independent.)

Add these i18n hooks (minimal)
	1.	Per-locale text fields in the enriched doc
Keep canonical facts once, and store localized text alongside:

	•	name_{en|ru|et}, search_text_{en|ru|et}, benefit_snippet_{en|ru|et}, faq_{en|ru|et}[], synonyms_multi.{en|ru|et}[].
	•	Missing locales can be null until you add them.

	2.	Two Meili indexing patterns (pick one)

	•	A. One index, multi-field (fastest to ship):
	•	searchableAttributes includes the active locale fields only (e.g., name_en, search_text_en now).
	•	When you add RU/ET, switch the list at runtime per locale (or run per-locale queries).
	•	B. Per-locale indexes (recommended for production):
	•	products_enriched_en, products_enriched_ru, products_enriched_et (aliases point to them).
	•	Each index flattens locale fields into name, search_text, benefit_snippet, faq.
	•	Pros: simpler query layer, localized synonyms/stop-words/typo tolerances, cleaner analytics & A/B.

	3.	Synonyms per locale
Maintain synonyms_multi in the doc; when a locale goes live, install only that language’s list into the matching Meili index. (E.g., “креатин ↔ creatine”, “valk/valgud ↔ protein”.)
	4.	Vectors: choose a multilingual embedding model now
Use a cross-lingual model (e.g., BGE-M3/LaBSE class). Then you can:

	•	Store one vector per product (built from EN text today).
	•	Later, users querying in RU/ET embed their query in RU/ET and it still lands on the same vectors (no re-embed needed).
	•	PDP “Alternatives” are product→product and remain language-agnostic.

	5.	LLM enrichment split: facts vs. copy
Keep your single GPT-5-mini pass for facts/flags. For RU/ET later:

	•	Run a light translation pass (can also be GPT-5-mini) to populate benefit_snippet_{ru|et}, faq_{ru|et}, and expand synonyms_multi.
	•	Facts are not re-computed; only localized strings are added.
	•	Store provenance = translated_from: en + confidence.

	6.	Logging & QA
Add i18n warn codes: MISSING_LOCALIZED_COPY(locale), BAD_TRANSLATION_LENGTH(locale) (e.g., snippet >160 chars), UNSUPPORTED_CLAIM(locale) if translation amplifies claims.

Migration path (when you add RU/ET)
	1.	Keep current EN index running.
	2.	Generate RU/ET localized fields (LLM translation pass).
	3.	If using pattern B: build products_enriched_ru/et by flattening locale fields and registering locale-specific synonyms. Switch your storefront to the matching index by domain/path.
	4.	Vectors: unchanged. Query embeddings switch to the user’s locale automatically.

TL;DR
	•	Your architecture already supports multilingual with no structural changes.
	•	Add per-locale text fields, pick either one multi-field index or per-locale indexes, and use a multilingual embedding model so vectors don’t need rework.
	•	Keep the single LLM pass for facts; add a tiny translation pass only for display copy when RU/ET go live.

If you want, I can append a short “Multilingual plan” section to the indexing-architecture doc with the two Meili patterns and field names so it’s ready to implement.
