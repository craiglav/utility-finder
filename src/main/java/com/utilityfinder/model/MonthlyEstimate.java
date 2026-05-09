package com.utilityfinder.model;

/**
 * Computed cost breakdown for a single calendar month under a given rate plan.
 * {@code estimated} is true when the month had no usage records and the global
 * average kWh was substituted.
 */
public record MonthlyEstimate(
        int month,
        double avgKwh,
        boolean estimated,
        double baseCost,
        double energyCost,
        double discountsApplied,
        double totalCost
) {}
