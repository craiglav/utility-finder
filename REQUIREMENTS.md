# Utility Rate Comparison App — Requirements v1

## Platform & Distribution
- JavaFX desktop app, distributed as a self-contained executable JAR
- All data stored locally — no cloud, no network dependency
- Data files in a configurable local directory

## Workspaces
- Multiple named workspaces (e.g., "My House", "Mom & Dad")
- Workspace switcher accessible from the main UI
- Each workspace stores independently:
  - Multi-year monthly usage history
  - Saved provider rate plans

## Usage Data Management
- Manual entry: month, year, kWh consumed
- Multi-year data per workspace
- **Averaged usage profile**: for each calendar month (Jan–Dec), average kWh across all years on record — this is the baseline used for all cost calculations
- Full edit and delete of existing entries

## Provider Rate Management
- Manual entry of rate plans, fields:
  - Provider name, plan name, contract term
  - Monthly base charge ($)
  - Per-kWh rate (¢/kWh)
  - Usage-tier discounts — multiple rules supported (e.g., "$100 credit when usage ≥ 1,000 kWh")
  - Optional notes
- Full edit and delete of saved plans
- CenterPoint delivery charges excluded (identical across all TX providers, not modeled)

## Rate Calculation
- Apply the averaged monthly usage profile to each saved rate plan
- Monthly cost = base charge + (avg kWh × rate) ± applicable tier discounts
- Computed for all 12 months → summed to annual total

## Comparison Overview
Side-by-side table of all saved rate plans showing per plan:
- **Annual estimated cost** (primary figure)
- Highest-cost month (name + $)
- Lowest-cost month (name + $)
- Effective average per-kWh cost for the year

Best and worst values highlighted across plans.

## Drill-Down View
- From overview, navigate into any plan to see all 12 months (kWh + cost)
- Navigate back to overview

## UX
- **Full keyboard navigation** — all primary workflows completable without mouse/touchpad
- Fast form traversal for data entry (tab order, enter to confirm, escape to cancel)
- Arrow keys for list/table navigation

## Data Storage
- H2 embedded database (pure Java, zero native dependencies — ideal for executable JAR)

---

## Out of Scope — v1
- EFL PDF import
- Free nights / time-of-use rate plans
- Hourly usage data
- Tesla / EV charging integration
- Cloud sync

## Backlog
- EFL PDF import
- Free nights plan support (needs hourly data)
- Tesla charge history import for nightly usage estimation
