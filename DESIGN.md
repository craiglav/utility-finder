# Utility Rate Comparison App — Design v1

## Architecture Overview

```
com.utilityfinder
├── app/              — JavaFX Application entry point
├── model/            — Domain entities
├── repository/       — Data access (JDBC, H2)
├── service/          — Business logic and calculation engine
├── ui/
│   ├── controller/   — JavaFX controllers (one per view)
│   ├── view/         — FXML layout files
│   └── component/    — Reusable custom controls
└── util/             — Shared helpers
```

**Build tool:** Maven
**UI:** JavaFX + FXML
**Database:** H2 embedded (file-mode, stored in `~/.utility-finder/` or user-configurable path)
**Packaging:** Maven Shade Plugin (fat JAR). Note: JavaFX native libs are platform-specific — the JAR will target the host platform. `jpackage` is a future option for a bundled native installer.

---

## Data Model

### Entity: Workspace
| Column      | Type    | Notes                  |
|-------------|---------|------------------------|
| id          | BIGINT  | PK, auto-increment     |
| name        | VARCHAR | User-defined label     |
| created_at  | DATE    |                        |

### Entity: UsageRecord
| Column       | Type    | Notes                              |
|--------------|---------|------------------------------------|
| id           | BIGINT  | PK, auto-increment                 |
| workspace_id | BIGINT  | FK → Workspace                     |
| year         | INT     |                                    |
| month        | INT     | 1–12                               |
| kwh_used     | DOUBLE  |                                    |

Unique constraint on `(workspace_id, year, month)`.

### Entity: RatePlan
| Column               | Type    | Notes                          |
|----------------------|---------|--------------------------------|
| id                   | BIGINT  | PK, auto-increment             |
| workspace_id         | BIGINT  | FK → Workspace                 |
| provider_name        | VARCHAR |                                |
| plan_name            | VARCHAR |                                |
| contract_term_months | INT     | Nullable                       |
| base_charge          | DOUBLE  | $ per month                    |
| rate_per_kwh         | DOUBLE  | $ per kWh (e.g. 0.112)        |
| notes                | VARCHAR | Nullable                       |
| is_current           | BOOLEAN | Marks the active plan          |

### Entity: TierDiscount
| Column        | Type    | Notes                                        |
|---------------|---------|----------------------------------------------|
| id            | BIGINT  | PK, auto-increment                           |
| rate_plan_id  | BIGINT  | FK → RatePlan                                |
| threshold_kwh | DOUBLE  | Usage must be >= this value to apply         |
| discount_amt  | DOUBLE  | Dollar amount subtracted from monthly cost   |
| sort_order    | INT     | Display/application order                    |

Multiple discounts per plan are supported. Each is evaluated independently — if usage meets the threshold, the discount is applied.

### Computed (not stored): MonthlyEstimate
```
month, avgKwh, baseCost, energyCost, discountsApplied, totalCost
```

### Computed (not stored): PlanSummary
```
ratePlan, annualCost, highestMonth{month,cost}, lowestMonth{month,cost}, effectiveAvgPerKwh
```

### Computed (not stored): AveragedUsageProfile
```
For each month 1–12: average of all UsageRecords for that month across all years in the workspace.
If a calendar month has no entries at all, substitute the global average
(mean of all available per-month averages). Flag substituted months for display.
```

---

## Calculation Engine

```
globalAvg = mean of all per-month averages that have data

For each month M (1–12):
  if UsageRecords exist for month M:
    avgKwh   = average kWh for month M across all years
    estimated = false
  else:
    avgKwh   = globalAvg
    estimated = true   ← flagged for warning banner

  energyCost  = avgKwh × rate_per_kwh
  discounts   = sum of discount_amt for all TierDiscounts where avgKwh >= threshold_kwh
  monthCost   = base_charge + energyCost − discounts

annualCost         = sum of monthCost for all 12 months
highestMonth       = month with max monthCost
lowestMonth        = month with min monthCost
effectiveAvgPerKwh = annualCost / sum(avgKwh for all months)

If any month is estimated, show warning banner on comparison screen:
  "Estimated data used for: [March, November] — no usage records found for those months."
```

---

## Navigation & Screen Map

```
┌─────────────────────────────────────────────────────┐
│  Workspace: [My House          ▼]                   │
├──────────────┬──────────────────────────────────────┤
│              │                                      │
│  > Usage     │   Content Area                       │
│  > Plans     │   (switches based on sidebar nav)    │
│  > Compare   │                                      │
│              │                                      │
└──────────────┴──────────────────────────────────────┘

Workspace dropdown → Manage Workspaces (add / rename / delete)
Sidebar: Usage | Plans | Compare
Compare view → select plan row → Plan Detail → Esc/Back → Compare
```

All navigation is keyboard-accessible. Sidebar items reachable via keyboard shortcut (Alt+1/2/3 or similar).

---

## Screen Designs

### Workspace Manager (dropdown panel or lightweight dialog)
```
Workspaces
─────────────────────────────
  ● My House          [Rename] [Delete]
    Mom & Dad         [Rename] [Delete]
    In-Laws           [Rename] [Delete]

  [+ New Workspace]       [Close]
─────────────────────────────
```
- Arrow keys to move between workspaces
- Enter to switch to selected workspace
- Inline rename (press F2 or Rename)

---

### Usage Data View
```
Usage Data — My House
────────────────────────────────────────────────────
  Year: [All ▼]                         [+ Add Entry]

  ┌───────┬───────────┬──────────┬──────────────────┐
  │ Year  │ Month     │ kWh      │                  │
  ├───────┼───────────┼──────────┼──────────────────┤
  │ 2024  │ January   │  1,245   │  [Edit] [Delete] │
  │ 2024  │ February  │  1,102   │  [Edit] [Delete] │
  │  ...  │           │          │                  │
  └───────┴───────────┴──────────┴──────────────────┘

  Averaged Profile (used in comparisons)
  ┌────────┬──────┬──────┬──────┬──────┬──────┬──────┐
  │        │ Jan  │ Feb  │ Mar  │ Apr  │ May  │ Jun  │
  │ Avg kWh│1,174 │1,089 │  842 │  901 │1,245 │1,680 │
  └────────┴──────┴──────┴──────┴──────┴──────┴──────┘
  ┌────────┬──────┬──────┬──────┬──────┬──────┬──────┐
  │        │ Jul  │ Aug  │ Sep  │ Oct  │ Nov  │ Dec  │
  │ Avg kWh│2,210 │2,450 │1,890 │1,102 │  987 │1,055 │
  └────────┴──────┴──────┴──────┴──────┴──────┴──────┘

  ─── Add / Edit Entry ────────────────────────────────
  Year  [2024    ]   Month  [January  ▼]   kWh  [     ]
  [Save]  [Cancel]
```
- Tab moves between Year / Month / kWh / Save
- Enter on Save commits entry; form clears for next entry
- Escape cancels

---

### Rate Plans View
```
Rate Plans — My House
────────────────────────────────────────────────────────────────
  ┌────────────┬────────────────┬───────┬────────┬─────────────┐
  │ Provider   │ Plan           │ Term  │ Base   │ Rate        │
  ├────────────┼────────────────┼───────┼────────┼─────────────┤
  │ Reliant  ★ │ Basic 12       │ 12 mo │ $9.95  │ 11.2¢/kWh   │
  │ TXU        │ Value 24       │ 24 mo │ $0.00  │ 12.1¢/kWh   │
  │ Gexa       │ Saver 12       │ 12 mo │ $4.95  │ 10.8¢/kWh   │
  └────────────┴────────────────┴───────┴────────┴─────────────┘
  ★ = current plan

  [+ Add Plan]   [Edit]   [Delete]   [Mark as Current]

  ─── Add / Edit Plan ─────────────────────────────────────────
  Provider   [               ]   Plan Name  [               ]
  Term (mo)  [               ]   Base ($)   [               ]
  Rate (¢)   [               ]

  Discounts:
  ┌────────────────────────────────────────────────┐
  │  Threshold (kWh)  │  Discount ($)  │           │
  │  [1000           ]│  [100         ]│ [Remove]  │
  │  [              ]│  [            ]│           │
  └────────────────────────────────────────────────┘
  [+ Add Discount]

  Notes  [                                          ]

  [Save Plan]  [Cancel]
```
- Tab order: Provider → Plan Name → Term → Base → Rate → Discounts → Notes → Save
- In discount rows: Tab moves Threshold → Discount → Remove → next row
- Enter on [+ Add Discount] appends a new row

---

### Comparison Overview
```
Comparison — My House
────────────────────────────────────────────────────────────────────────
  Based on averaged usage profile (3 years of data)

  ┌──────────────────┬─────────────────┬─────────────────┬────────────────┐
  │                  │ Reliant         │ TXU             │ Gexa           │
  │                  │ Basic 12        │ Value 24         │ Saver 12       │
  ├──────────────────┼─────────────────┼─────────────────┼────────────────┤
  │ Annual Cost      │   $1,842.15     │   $1,967.30     │  $1,798.44 ◀ ★ │
  │ Avg ¢/kWh        │      12.1¢      │      12.9¢      │     11.8¢ ◀ ★  │
  │ Highest Month    │ Aug  $284.50    │ Aug  $303.80    │ Aug $262.10 ◀★  │
  │ Lowest Month     │ Mar   $89.12    │ Mar   $95.40    │ Mar  $84.95 ◀★  │
  └──────────────────┴─────────────────┴─────────────────┴────────────────┘
  ★ = best value in row       ◀ = best value highlighted

  Arrow keys to move. Enter on a plan column to drill in.
```

---

### Plan Detail (Drill-Down)
```
Plan Detail — Gexa / Saver 12
────────────────────────────────────────────────────────────────────
  [← Back to Comparison]   (or Esc)

  Base charge: $4.95/mo    Rate: 10.8¢/kWh
  Discount: $100 when usage ≥ 1,000 kWh

  ┌────────────┬──────────┬──────────┬──────────────┬──────────────┐
  │ Month      │ Avg kWh  │ Base     │ Energy       │ Monthly Cost │
  ├────────────┼──────────┼──────────┼──────────────┼──────────────┤
  │ January    │  1,174   │   $4.95  │  $126.79     │  $31.74  *   │
  │ February   │  1,089   │   $4.95  │  $117.61     │  $22.56  *   │
  │ March      │    842   │   $4.95  │   $90.94     │  $95.89      │
  │ April      │    901   │   $4.95  │   $97.31     │ $102.26      │
  │ May        │  1,245   │   $4.95  │  $134.46     │  $39.41  *   │
  │ June       │  1,680   │   $4.95  │  $181.44     │  $86.39  *   │
  │ July       │  2,210   │   $4.95  │  $238.68     │ $143.63  *   │
  │ August     │  2,450   │   $4.95  │  $264.60     │ $169.55  *   │  ← highest
  │ September  │  1,890   │   $4.95  │  $204.12     │ $109.07  *   │
  │ October    │  1,102   │   $4.95  │  $119.02     │  $23.97  *   │
  │ November   │    987   │   $4.95  │  $106.60     │ $111.55      │
  │ December   │  1,055   │   $4.95  │  $113.94     │  $18.89  *   │  ← lowest
  ├────────────┼──────────┼──────────┼──────────────┼──────────────┤
  │ ANNUAL     │ 16,585   │  $59.40  │$1,795.51     │$1,798.44 *   │
  └────────────┴──────────┴──────────┴──────────────┴──────────────┘
  * discount applied ($100 off)
```

---

## Keyboard Navigation Summary

| Context            | Key              | Action                        |
|--------------------|------------------|-------------------------------|
| Global             | Alt+1            | Go to Usage view              |
| Global             | Alt+2            | Go to Plans view              |
| Global             | Alt+3            | Go to Compare view            |
| Global             | Alt+W            | Open workspace switcher       |
| Tables             | Arrow keys       | Move selection                |
| Tables             | Enter            | Edit / drill-in selected row  |
| Tables             | Delete           | Delete selected row           |
| Tables             | Insert / Ctrl+N  | Add new entry                 |
| Forms              | Tab / Shift+Tab  | Move between fields           |
| Forms              | Enter            | Save                          |
| Forms              | Escape           | Cancel                        |
| Plan Detail        | Escape           | Back to Comparison            |

---

## Decisions Log

| # | Decision |
|---|----------|
| 1 | **Packaging**: Mac-only fat JAR (Option A) for v1. Maven build can be extended to multi-platform later via GitHub Actions matrix builds. |
| 2 | **Data directory**: `~/.utility-finder/data.mv.db` — no configuration UI for v1. |
| 3 | **Missing months**: Substitute global average kWh for months with no data; show warning banner listing affected months on the comparison screen. |
