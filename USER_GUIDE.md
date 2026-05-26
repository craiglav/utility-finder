# Utility Finder — User Guide

Utility Finder helps Texas residents compare electricity plans using their actual usage history. Import your Smart Meter Texas data, enter the plans you're considering, and get a side-by-side annual cost comparison — including delivery charges, tier discounts, and time-of-use rates.

> **Note:** This tool is for use in Texas deregulated energy markets (ERCOT) only.

---

## Table of Contents

1. [Installation](#1-installation)
2. [Concepts](#2-concepts)
3. [Workspaces](#3-workspaces)
4. [Importing Usage Data](#4-importing-usage-data)
5. [Rate Plans](#5-rate-plans)
6. [Delivery (TDSP)](#6-delivery-tdsp)
7. [Comparing Plans](#7-comparing-plans)
8. [Insights](#8-insights)
9. [Keyboard Shortcuts](#9-keyboard-shortcuts)

---

## 1. Installation

See [README.md](README.md) for platform-specific installation instructions, including the one-time macOS Gatekeeper step. No separate Java installation is required — the runtime is bundled.

---

## 2. Concepts

| Term | What it means |
|---|---|
| **Workspace** | An isolated container for one property or scenario. Each workspace has its own usage data, rate plans, and delivery carrier. |
| **ESIID** | Your meter's unique identifier — printed on your electricity bill and used to auto-detect your delivery carrier. |
| **Rate Plan** | A retail electricity plan: base monthly charge, per-kWh rate, any discounts or time-of-use windows, and optional contract terms. |
| **Current Plan** | The plan you're on now, marked with ★. Marking a plan current unlocks contract cost and switch-savings calculations. |
| **TDSP / Delivery** | Transmission and Distribution Service Provider — the monopoly operator that owns the wires (e.g., CenterPoint, Oncor). Delivery charges are separate from your retail rate and appear on every bill. |
| **Tier Discount** | A per-kWh credit that kicks in when your monthly usage exceeds a threshold (common in Texas plans). |
| **Time-of-Use (TOU)** | A rate structure where certain hours have a different per-kWh rate — often free or very cheap overnight. |
| **ETF** | Early Termination Fee — the cost to exit your current contract early. Texas law (PUCT §25.272) waives this fee if your contract ends within 14 days. |

---

## 3. Workspaces

A workspace keeps everything together for one address or analysis scenario. When you first launch the app, a default workspace is created automatically.

### Creating a workspace

1. Click **Manage Workspaces** in the header.
2. Click **+ New Workspace**.
3. Enter a name (e.g., "Home", "Rental Property") and press Enter or click **Create**.

### Switching workspaces

Use the dropdown in the top-left header. The active workspace is shown with a **●** prefix.

### Renaming or deleting

Open **Manage Workspaces**, select a workspace, then:
- Press **F2** or click **Rename** to rename it.
- Press **Del** or click **Delete** to remove it — this permanently deletes all usage data and rate plans for that workspace.

> **Tip:** Create a separate workspace for each scenario you want to model — e.g., one for your current home and one for a property you're considering.

---

## 4. Importing Usage Data

Utility Finder uses 15-minute interval data from Smart Meter Texas to calculate realistic monthly cost projections.

### Getting your data from Smart Meter Texas

1. Go to [smartmetertexas.com](https://www.smartmetertexas.com) and log in (or create a free account).
2. Navigate to **My Energy Data** → **Green Button Download My Data**.
3. Select a date range — more history produces more accurate comparisons. One to two years is ideal.
4. Download the CSV file.

### Importing the CSV

1. In Utility Finder, navigate to the **Usage Data** tab.
2. Click **Import CSV...** and select the file you downloaded.
3. The import runs in the background. When complete, a summary dialog shows how many records were added or updated.

### What you'll see after importing

- **Status banner**: Your ESIID, the date range covered, total interval count, and when the data was last imported.
- **Monthly bar chart**: Total kWh per month, newest on the left.
- **Monthly summary table**: Total kWh, daily average, days with data, and peak consumption day for each month.

### Notes

- Importing the same file a second time is safe — duplicate records are overwritten, not doubled.
- Daylight saving time transitions are handled automatically.
- Months with no usage records will use an average in comparisons — those months are flagged with a warning on the Compare tab.

### Clearing usage data

Click **Clear Data** in the status banner. This permanently removes all interval records for the current workspace. Your rate plans and TDSP selection are not affected.

---

## 5. Rate Plans

Add every plan you want to compare — including your current one.

### Adding a plan

1. Navigate to the **Rate Plans** tab.
2. Click **+ Add Plan**.
3. Fill in the fields in the form at the bottom of the screen.
4. Click **Save Plan**.

### Plan fields

**Basic fields**

| Field | Description |
|---|---|
| Provider | The retail electric provider name (e.g., "Reliant", "TXU Energy"). |
| Plan Name | The plan's marketing name (e.g., "Simple Rate 12"). |
| Term (months) | Contract length, if any. Optional. |
| Base Charge | Fixed monthly fee in dollars (e.g., `9.95`). |
| Rate (¢/kWh) | Energy rate in cents per kWh (e.g., `10.5`). |
| Renewable % | Percentage of renewable energy. Optional, informational only. |

**Usage discounts**

Some Texas plans offer a per-kWh credit when monthly usage exceeds a threshold — for example, a $0.06/kWh discount when you use more than 1,000 kWh in a month.

- Click **+ Add Discount** to add a tier.
- Enter the **Threshold (kWh)** and **Discount Amount ($/kWh)**.
- Leave a row blank to ignore it.

**Time-of-Use windows**

Plans with free nights or other hourly rate variations use TOU windows.

- Click **+ Add TOU Window** to add a window.
- Set the **Start Hour** and **End Hour** (0–23, e.g., 21 = 9 PM, 7 = 7 AM).
- Set the **Rate (¢/kWh)** — enter `0` for free.
- Hours wrap midnight, so a 21–7 window covers 9 PM through 7 AM.

**Early termination fees**

If the plan has a contract, enter the exit cost so switch-savings calculations on the Compare tab are accurate.

- **Flat Fee**: A fixed dollar amount.
- **Per Month**: A per-remaining-month charge.
- Both fields are optional; leave blank if there's no ETF.

**Marking your current plan**

Check **Current plan** to mark the plan you're on now. Only one plan per workspace can be current at a time. Optionally enter your **Contract End** date to unlock the 14-day ETF waiver indicator.

Marking a plan current:
- Shows a **★** prefix in the plans table.
- Enables remaining-contract cost and switch-savings figures on the Compare tab.

### Editing and deleting plans

- Select a plan in the table and press **F2** (or click **Edit**) to edit it.
- Press **Del** (or click **Delete**) to remove it — confirmation required.

---

## 6. Delivery (TDSP)

Delivery charges are added by your wires company and appear on every bill, regardless of which retail provider you choose. Including them in your comparison gives a more accurate picture of total cost.

### Selecting your carrier

1. Navigate to the **Delivery** tab.
2. If you've imported usage data, Utility Finder may have already detected your carrier from your ESIID — check the carrier dropdown.
3. If not, enter your ZIP code and click **Look Up** to find your carrier, or select it manually from the dropdown.

Texas TDSPs:

| Carrier | Service area |
|---|---|
| CenterPoint Energy | Greater Houston area |
| Oncor | Dallas / Fort Worth, West Texas |
| AEP Texas Central | Corpus Christi, Rio Grande Valley |
| AEP Texas North | Abilene, Amarillo, Lubbock |
| Texas-New Mexico Power (TNMP) | Portions of West and South Texas |

### Reviewing rates

Once a carrier is selected, the rate display shows:
- **Base charge** ($/month)
- **Distribution rate** (¢/kWh)
- **Last verified date**

Below that, a monthly delivery cost table breaks down what you're paying for delivery each month based on your imported usage.

### Updating rates

TDSP rates are filed with the Public Utility Commission of Texas (PUCT) twice per year — on **March 1** and **September 1**. When rates change:

- Click **Check for Updates** to fetch the latest PUCT rate report automatically (where supported).
- Or click **Edit Rates** to enter updated rates manually.

---

## 7. Comparing Plans

The **Compare** tab calculates what each plan would have cost using your actual usage history.

### Running a comparison

Navigate to the **Compare** tab. The comparison runs automatically using the usage data and rate plans in the current workspace. Click **⟳ Refresh** if you've made changes and want to recalculate.

### Overview grid

Each plan appears as a column. For each plan you'll see:
- **Annual cost** — projected total for 12 months.
- **Effective rate** — the blended ¢/kWh including base charge, discounts, and TOU adjustments.
- **Monthly cost cells** — one row per calendar month; lowest cost is highlighted green, highest is red.
- **★ Current** tag on your marked current plan.
- **Lowest** / **Highest** tags on the cheapest and most expensive plans.

### Including delivery charges

Toggle **Include Delivery** in the header to add TDSP charges to each plan's costs. This requires a carrier to be selected on the Delivery tab.

### Switch savings and contract costs

If you've marked a current plan with a contract end date, the Compare tab shows:
- How much you'd pay to finish out your current contract.
- The early termination fee (if applicable).
- Texas law waives the ETF if your contract ends within **14 days** — the app flags this automatically.

### Estimated months

If some months have no usage records, the app uses the average of your other months as a substitute. A warning banner lists those months so you know the projection is partial.

### Drilling into a plan

Click **Detail ▶** in any plan's header to see a full month-by-month breakdown:

- **Table**: Each month's average kWh, base cost, energy cost, discounts applied, and total.
- **Line chart**: Monthly cost trend over the year.
- **Bar chart**: Cost split by category (base, energy, discounts, delivery).

Press **Esc** or click **← Back** to return to the overview grid.

---

## 8. Insights

The **Insights** tab visualises your usage patterns. It requires imported usage data.

### Season filter

Use the **Season** dropdown to filter all hourly and day-of-week charts to a specific season:
- All Seasons
- Winter (Dec – Feb)
- Spring (Mar – May)
- Summer (Jun – Aug)
- Fall (Sep – Nov)

### What's shown

**Hourly profile by day of week**
Average consumption for each hour of the day, with one line per day of the week. Useful for identifying whether you're a good candidate for a free-nights plan.

**Peak windows & data quality**
The hour of day with the highest average load, and what percentage of your readings are estimated vs. actual meter readings.

**Seasonal load curves**
Separate 24-hour profiles for each season, showing how your usage pattern shifts between summer cooling and winter heating.

**Year-over-year comparison**
Monthly totals plotted as one line per year, making it easy to spot whether consumption is trending up or down.

**30-day usage trend**
A rolling 30-day average smooths out day-to-day volatility to show longer-term changes in your usage.

**Usage calendar (heatmap)**
A full grid of every day in your data — colour intensity shows consumption level (blue = low, red = high). Missing days appear blank, making it easy to spot gaps in your data.

---

## 9. Keyboard Shortcuts

| Key | Where | Action |
|---|---|---|
| **F2** | Workspace list | Rename selected workspace |
| **Del** | Workspace list | Delete selected workspace |
| **↑ / ↓** | Workspace list | Navigate list |
| **F2** | Rate Plans table | Edit selected plan |
| **Del** | Rate Plans table | Delete selected plan |
| **Tab** | Rate Plans form | Move to next field |
| **Esc** | Compare detail view | Return to overview grid |
