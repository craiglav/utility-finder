package com.utilityfinder.service;

import com.utilityfinder.model.MonthlyEstimate;
import com.utilityfinder.model.PlanSummary;
import com.utilityfinder.model.RatePlan;
import com.utilityfinder.model.TierDiscount;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ComparisonService {

    private final IntervalService intervalService;
    private final RatePlanService ratePlanService;

    public ComparisonService(IntervalService intervalService, RatePlanService ratePlanService) {
        this.intervalService = intervalService;
        this.ratePlanService = ratePlanService;
    }

    public List<PlanSummary> compare(long workspaceId) {
        double[] rawProfile = intervalService.getAveragedProfile(workspaceId);
        double[] profile = substituteGlobalAverage(rawProfile);
        List<RatePlan> plans = ratePlanService.findByWorkspace(workspaceId);

        long remainingMonths = plans.stream()
                .filter(RatePlan::isCurrent)
                .findFirst()
                .map(p -> p.getContractEndDate() != null
                        ? Math.max(0, ChronoUnit.MONTHS.between(
                                YearMonth.now(), YearMonth.from(p.getContractEndDate())))
                        : 0L)
                .orElse(0L);

        return plans.stream()
                .map(plan -> calculate(plan, rawProfile, profile, remainingMonths))
                .toList();
    }

    private PlanSummary calculate(RatePlan plan, double[] rawProfile, double[] profile, long remainingMonths) {
        List<MonthlyEstimate> estimates = new ArrayList<>();
        List<String> estimatedNames = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            boolean estimated = Double.isNaN(rawProfile[i]);
            double kwh = profile[i];
            double energy = kwh * plan.getRatePerKwh();
            double discounts = plan.getDiscounts().stream()
                    .filter(d -> kwh >= d.getThresholdKwh())
                    .mapToDouble(TierDiscount::getDiscountAmt)
                    .sum();
            double total = plan.getBaseCharge() + energy - discounts;

            estimates.add(new MonthlyEstimate(i + 1, kwh, estimated,
                    plan.getBaseCharge(), energy, discounts, total));

            if (estimated) {
                estimatedNames.add(Month.of(i + 1)
                        .getDisplayName(TextStyle.FULL, Locale.getDefault()));
            }
        }

        double annual = estimates.stream().mapToDouble(MonthlyEstimate::totalCost).sum();
        MonthlyEstimate highest = estimates.stream()
                .max((a, b) -> Double.compare(a.totalCost(), b.totalCost())).orElseThrow();
        MonthlyEstimate lowest = estimates.stream()
                .min((a, b) -> Double.compare(a.totalCost(), b.totalCost())).orElseThrow();
        double totalKwh = Arrays.stream(profile).sum();
        double effectiveRate = totalKwh > 0 ? annual / totalKwh : 0;

        double terminationFee = 0;
        if (plan.isCurrent()) {
            long remaining = plan.getContractEndDate() != null
                    ? Math.max(0, ChronoUnit.MONTHS.between(
                            YearMonth.now(), YearMonth.from(plan.getContractEndDate())))
                    : 0;
            if (plan.getTerminationFeeFlat() != null)     terminationFee += plan.getTerminationFeeFlat();
            if (plan.getTerminationFeePerMonth() != null) terminationFee += plan.getTerminationFeePerMonth() * remaining;
        }

        double remainingMonthsCost = 0;
        if (remainingMonths > 0) {
            YearMonth start = YearMonth.now().plusMonths(1);
            for (long m = 0; m < remainingMonths; m++) {
                int monthIdx = start.plusMonths(m).getMonthValue() - 1;
                remainingMonthsCost += estimates.get(monthIdx).totalCost();
            }
        }

        return new PlanSummary(plan, annual, terminationFee, remainingMonthsCost, highest, lowest,
                effectiveRate, estimates, estimatedNames);
    }

    /** Replaces NaN entries (months with no data) with the mean of all valid months. */
    private double[] substituteGlobalAverage(double[] raw) {
        double[] result = Arrays.copyOf(raw, raw.length);
        double globalAvg = Arrays.stream(raw)
                .filter(v -> !Double.isNaN(v))
                .average()
                .orElse(0.0);
        for (int i = 0; i < result.length; i++) {
            if (Double.isNaN(result[i])) result[i] = globalAvg;
        }
        return result;
    }
}
