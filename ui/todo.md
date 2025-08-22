You’re right—the extra goals/filters made the chip wall feel noisy. Here’s a compact pattern that keeps the speed of chips but adds structure and progressive disclosure.

What I’d change
	1.	Turn “Goals” into Presets (top 4–6 only)

	•	Show 4–6 most relevant goals (contextual to category/query) as small cards with an icon (💪 Strength, ⚡️ Pre-workout, 🛡️ Wellness, 🧪 Creatine, etc.).
	•	Add a ghost button: “More goals” → opens a sheet/popover with the full list grouped by category (Build, Recover, Weight, Wellness…).
	•	Each preset applies multiple filters behind the scenes (and you show them as chips in the “applied bar”; see #4).

	2.	Make Filters faceted, with a “Quick row”

	•	Keep one Quick Filters row (single line, compact chips): In stock, Powder, Capsules, Vegan, ≤€20, ≤€0.50/serv.
	•	Move the rest into an Accordion / side panel with groups:
	•	Availability (toggle)
	•	Form (chips)
	•	Diet (checkboxes)
	•	Price (slider + quick steps: ≤€20, ≤€40)
	•	Serving cost (slider)
	•	Brand (searchable list)
	•	Stimulants (Caffeine free / Low / High)
	•	Desktop: right sidebar; Mobile/tablet: bottom drawer with Apply / Reset.

	3.	Overflow chips with “+N”

	•	Any chip row stays to one line. If it overflows, show a “+N” chip; tapping opens a popover with the full list (checkboxes + search within the facet).

	4.	Applied filters bar (just under search)

	•	Show only the selected filters as removable chips.
	•	If >3, collapse to three + a “+2 more” chip that expands a small list.
	•	This keeps the control visible while the control surfaces (Goals/Filters) stay compact.

	5.	Tighten copy + density

	•	Short labels: Caffeine boost → Caffeine, Daily essentials → Daily, Gluten-free → Gluten-free (ok), Weight loss → Weight loss.
	•	Numeric chips: ≤€20, ≤€0.50/serv. (Left-trim spaces; use ≤ and € consistently.)
	•	Chip size ~28–32px height, reduced horizontal padding, 6–8px gap.

	6.	“Sort” reduces filtering

	•	Add Sort right above results: Best match • Price ↑↓ • Popular • New. Many users pick sort instead of over-filtering.

	7.	Contextual defaults

	•	Pre-workout category → show Pre-workout, Caffeine, Electrolytes, Endurance.
	•	Protein category → Build muscle, Recovery, Lactose-free, Vegan, ≤€0.50/serv.
	•	Query-aware: when user types “creatine”, surface Creatine • Strength • Budget first.

Example of the layout (desktop)
	•	Search bar
	•	Applied filters bar: [In stock] [≤€20] [+2 more]
	•	Three columns:
	•	Products (left, as you have)
	•	Goals (presets): 2-column mini-cards, then “More goals”
	•	Content (right)
	•	Quick Filters (single line, with +N) under Goals
	•	Filter panel (accordion) on the right or in a drawer.

Why this works
	•	Progressive disclosure: quick actions visible; full power only when asked.
	•	Single-line rows: prevents visual ladders of chips.
	•	Separation of concerns: “Goals = presets” (meaningful bundles), “Filters = facets” (atomic toggles).
	•	Applied bar gives constant feedback without cluttering the control surface.

If you want a component plan (no code yet)
	•	<ChipTray maxVisible={6} overflowLabel={(n)=>+${n}}/>
	•	<PresetCard icon label onApply(filters[]) />
	•	<FacetGroup type="chips|checkbox|slider" searchable />
	•	<AppliedBar maxVisible={3} />
	•	<FiltersDrawer onApply onReset />

If you like this direction, I can draft the React components (with the overflow +N popover and an accessible drawer) to drop into your current mock.