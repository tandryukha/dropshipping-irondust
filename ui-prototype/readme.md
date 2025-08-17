# Fitpoint — Rufus-lite Visual Prototype

**Purpose:** a single-file, visual-only mock to explore a “Rufus-lite” experience for Fitpoint.ee:  
- **Enhanced Search Panel** (Products • Goals • Content)  
- **Command Palette** with a Mac-like modal look (⚡ button + ⌘/Ctrl+K)  
- **PDP Copilot** (Q&A chips, alternatives, bundle)  
- **Flavor selection** (required when adding SKUs with variants)  
- **Tabs** for Description • Composition • Reviews  
- **Ref overlay** to compare with the current site screenshot

Everything is **hard-coded**. No integrations, no real search, no analytics.

---

## Quickstart

1. Save the prototype file as `index.html` (already self-contained).  
2. Open it directly in your browser (double-click). No server needed.  
3. (Optional) Put a low-opacity reference screenshot at:

../../assets/reference/fitpoint-home.png

Click **Ref** (bottom-right) to toggle the overlay. If the image is missing, the button does nothing.

---

## What’s in this prototype

### 1) Header & Search
- **Search bar** with icon, placeholder, ⚡ **bolt** button, and a `⌘K` badge.
- **Cart pill** (mock counter), language pill (static).
- **Brand tokens** (CSS variables):
```css
--fp-primary:#16a8c9; --fp-dark:#222; --fp-muted:#6b7280; --fp-bg:#f6f7f9; --fp-accent:#ffce3a;

2) Enhanced Search Panel (visual only)
	•	Opens on search focus (stays open by default on load for demo).
	•	3 columns:
	•	Products: 4 example cards with price, badges, and Add.
	•	Goals: toggle chips (e.g., Lean muscle, Strength) + Filters (Lactose-free, Vegan, Low sugar, Budget).
	•	Content: 4 mock guides/articles and an Open Coach button.
	•	Add opens the Flavor Popover (see below).

Edit products by duplicating a .product block. To enforce flavor selection, set a data-flavors array on the Add button.

Example:

<button class="add js-add"
  data-name="Whey Protein Isolate"
  data-flavors='["Vanilla","Chocolate","Cookies & Cream"]'>
  Add
</button>

For non-variant SKUs:

<button class="add js-add" data-name="Creatine Monohydrate" data-flavors='[]'>Add</button>

3) Flavor Popover (required when needed)
	•	Appears near the clicked Add button.
	•	Renders flavor chips from data-flavors. If flavors exist, Confirm is disabled until one is chosen.
	•	Includes a simple Qty control.
	•	Clicking Add to cart increments the mock cart counter.

4) PDP Copilot (left column)
	•	Title, rating, price, and PDP flavor selector (separate from the popover—this one changes the title label).
	•	Q&A chips (3): “Is this right for muscle gain?”, “Lactose friendly?”, “When to take?”. Clicking toggles a short answer block.
	•	Facts (badges): “24g protein/serving”, “Low sugar”, “Fast absorption”.
	•	Dosage/Timing mini cards.
	•	Bundle callout (Creatine + price).

5) PDP: Info Tabs (below Copilot)
	•	Description (short copy + bullets),
	•	Composition (table with per-serving values + ingredients),
	•	Reviews (empty state + placeholder button).
	•	Controlled by ARIA tabs (role="tablist", aria-selected), minimal JS.

6) Alternatives & Complements (right column)
	•	Alternatives: 2 cards (Isolate + Vegan Blend) with View buttons.
	•	More picks: 6 small cards (cookies, bars, BCAA, etc.).

7) Command Palette (Mac-like)
	•	Opens via ⚡ bolt or ⌘/Ctrl + K.
	•	Frosted overlay; simple list of Products and Actions (e.g., Apply discount code, Contact support).
	•	Purely visual—no navigation or side effects.

8) Reference Overlay
	•	Ref button toggles a full-page, 12–16% opacity background image for alignment checks (../../assets/reference/fitpoint-home.png).

⸻

Interactions (minimal by design)

Area	Interaction
Search	Focus shows panel. Clicking outside hides it.
Goal/Filter chips	Toggle aria-pressed. Purely visual.
Product “Add”	Opens Flavor Popover; requires flavor if provided; increments cart counter.
PDP flavor buttons	Toggle selection; update title flavor text.
Q&A chips	Toggle answer blocks (accordion-like).
Tabs	Switch active tab via click; updates ARIA states.
Command Palette	Open with ⚡ or ⌘/Ctrl+K; close with Esc or clicking backdrop.
Ref	Toggles overlay image if present.


⸻

Accessibility touchpoints
	•	Visible focus ring using box-shadow (token --ring).
	•	Chips and flavors use role="switch"/aria-pressed where appropriate.
	•	Tabs use role="tablist" + aria-selected + labelled tabpanels.
	•	Live region for cart count (aria-live="polite").
	•	label/aria-label for inputs and modals.

⸻

Responsive behavior
	•	Target: 360px–1280px.
	•	Below 900px: search panel stacks columns; PDP becomes a single column; alt grid collapses; “More picks” becomes 3-wide.

⸻

Editing Guide

Where to change colors/feel: edit CSS variables at the top.
Where to add products: duplicate .product in the “Products” column; update image, text, price, and data-flavors.
Where to change goals/filters: add/remove .chip buttons in the “Goals” column.
Where to change content items: duplicate .content-card in the “Content” column.
PDP content: edit headings, Q&A text, facts, dosage, bundle area.
Tabs content: edit the three .tabpanel blocks.
Alternatives/More picks: edit cards inside .alt-grid and .more.
Command palette rows: inside .pal-body.
Ref image: replace the file at ../../assets/reference/fitpoint-home.png or change the CSS background-image in .ref-img.

⸻

Known Intentional Gaps (for clarity)
	•	No real search, re-ranking, stock/pricing, or cart logic.
	•	No analytics/events (Segment) — explicitly ignored for MVP mock.
	•	No A/B testing scaffolding.
	•	No i18n switching; copy is EN-only.

⸻

If/When We Iterate (non-blocking ideas)
	•	Swap static product blocks for a tiny client-side JSON to speed edits.
	•	Add a “compare” flyout (Isolate vs Concentrate spec rows).
	•	Save dietary constraints (vegan/lactose-free) in localStorage to gate PDP suggestions.
	•	Add a mobile voice input button for search (visual).

⸻

Folder suggestion (optional)

prototypes/
  v2-search+pdp/
    index.html           # this single-file prototype
    README.md            # this document
assets/
  reference/
    fitpoint-home.png    # overlay reference (optional)


⸻

Definition of Done (for this mock)
	•	Single index.html opens locally with no errors.
	•	Enhanced panel shows Products • Goals • Content with working chips and Add → Flavor popover.
	•	PDP Copilot displays Q&A chips, facts, dosage/timing, bundle.
	•	Tabs switch between Description/Composition/Reviews.
	•	Command Palette opens via ⚡ and ⌘/Ctrl+K; closes with Esc/backdrop.
	•	Ref overlay toggles when screenshot is present.
	•	Layout works from 360px to 1280px.

⸻

License / Usage

Internal prototype for design exploration. Do not deploy publicly as-is.

