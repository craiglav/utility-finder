package com.utilityfinder.model;

import java.util.List;

/**
 * Computed annual comparison summary for a single rate plan.
 * {@code estimatedMonthNames} lists any months where the global-average kWh
 * was substituted for missing usage data (used to drive the warning banner).
 */
public record PlanSummary(
        RatePlan plan,
        double annualCost,
        double terminationFee,
        double remainingMonthsCost,
        MonthlyEstimate highestMonth,
        MonthlyEstimate lowestMonth,
        double effectiveAvgPerKwh,
        List<MonthlyEstimate> monthlyEstimates,
        List<String> estimatedMonthNames
) {}
