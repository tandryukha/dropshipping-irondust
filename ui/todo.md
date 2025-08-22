This is a big step up—way clearer and feels fast. Here’s the high-impact feedback, ordered by priority.

Must fix / clarity
	•	Duplicate applied chips. I see Powder twice in the applied bar. Dedupe by facet+value and normalize synonyms (e.g., form=powder).
	•	Goals semantics. Are goals exclusive (one at a time) or stackable? Make it explicit:
	•	If exclusive → use radio-style selection (single “active” state) and deselect others on click.
	•	If stackable → show a small checkmark on cards and a counter on “More goals” (e.g., More goals (2)).
	•	State sync. When a goal is active, mirror it in the All Goals modal and vice-versa (selected styling + checkmark). Same for filters ↔ applied bar.
	•	Keyboard & a11y. Chips, cards, and sliders need visible focus rings, Space/Enter activation, and labels (e.g., “Remove filter: Powder”). Range slider must be operable via keyboard.

Should improve (UX wins)
	•	Applied bar behavior. Keep it single-line with overflow to +N more. Clicking expands a small sheet listing the rest with “Remove all”.
	•	Counts = confidence.
	•	Show product counts in All Goals cards (e.g., “Strength · 124”).
	•	In All Filters, show counts per option and disable 0-result items.
	•	Sorting. Replace Price ↑ chip with a Sort dropdown: Best match • Price ↑ • Price ↓ • Popular • New. Default to Best match when any goal is set.
	•	Facet coverage. Add high-signal facets for this category:
	•	Brand (searchable list)
	•	Caffeine (None • Low • High)
	•	Flavors (searchable)
	•	Form already there; consider Servings (>=30, >=60).
	•	Price & cost controls. Use a two-handle range slider for Price (min/max). Keep the quick steps (≤€20, ≤€40) as presets that update the slider.
	•	Quick Filters row. Cap to one line; overflow as +N. Consider pinning In stock first and keep monetary chips grouped at the end.
	•	Microcopy consistency. Prefer “All goals” (matches modal title) over “More goals”. Use €/serv everywhere; round to 2 decimals.
	•	Variant flow. If an item requires choices (size/flavor), use “Choose options” instead of “Add”. Open a compact chooser (size • flavor • qty) inline.
	•	Empty ratings. Replace ★ 0.0 (0) with “—” to avoid implying poor quality.

Visual polish
	•	Goal cards hierarchy. Increase icon size slightly, tighten label to one line, and use a more saturated focus/active outline so the selected card pops.
	•	Chip density. 28–32px height, 6–8px gaps; numeric chips like ≤€20 benefit from a monospace or tabular lining number style if available.
	•	Iconography. Add a small funnel icon to “All filters” for affordance; chevron on Sort.

Mobile / responsiveness
	•	Sticky applied bar under the search.
	•	Filters → bottom sheet with Apply/Reset as a sticky footer.
	•	Quick Filters row becomes a horizontally scrollable “pill rail”.
	•	Preserve state to URL params so results are shareable and survive reloads.

“Nice to have” (boosts conversion)
	•	Preview on hover. Hovering a Goal shows the filters it will apply (tiny tooltip like: “Adds: Strength, Protein, ≤€0.50/serv”).
	•	Recently used goals/filters. Surface the last 2–3 the user applied.
	•	Personalized default. If the user clicked “Pre-workout” last session, surface it first in the 2×2 goal grid.

⸻

If you want, I can sketch the component contracts for:
	•	AppliedBar({maxVisible})
	•	ChipTray({items, maxVisible}) with +N popover
	•	GoalsGrid({mode: 'single'|'multi', counts})
	•	FiltersDrawer({facets, onApply})
	•	useUrlState() to sync state ⇄ URL

But as-is, the new structure is solid—fix the dedupe, clarify goal selection rules, add counts, and you’re in great shape.