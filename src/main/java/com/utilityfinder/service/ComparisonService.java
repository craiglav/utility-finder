package com.utilityfinder.service;

import com.utilityfinder.model.MonthlyEstimate;
import com.utilityfinder.model.PlanSummary;
import com.utilityfinder.model.RatePlan;
import com.utilityfinder.model.TierDiscount;
import com.utilityfinder.model.TouWindow;

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
        double[] profile    = substituteGlobalAverage(rawProfile);
        List<RatePlan> plans = ratePlanService.findByWorkspace(workspaceId);

        // Only fetch hourly data when at least one plan needs it
        double[][] rawHourly = null;
        double[][] hourly    = null;
        if (plans.stream().anyMatch(RatePlan::hasTouWindows)) {
            rawHourly = intervalService.getHourlyProfileByMonth(workspaceId);
            hourly    = substituteHourlyAverage(rawHourly);
        }

        long remainingMonths = plans.stream()
                .filter(RatePlan::isCurrent)
                .findFirst()
                .map(p -> p.getContractEndDate() != null
                        ? Math.max(0, ChronoUnit.MONTHS.between(
                                YearMonth.now(), YearMonth.from(p.getContractEndDate())))
                        : 0L)
                .orElse(0L);

        final double[][] fRawHourly = rawHourly;
        final double[][] fHourly    = hourly;

        return plans.stream()
                .map(plan -> calculate(plan, rawProfile, profile, fRawHourly, fHourly, remainingMonths))
                .toList();
    }

    // ── Per-plan calculation ──────────────────────────────────────────────────

    private PlanSummary calculate(RatePlan plan,
                                  double[] rawProfile, double[] profile,
                                  double[][] rawHourly, double[][] hourly,
                                  long remainingMonths) {
        List<MonthlyEstimate> estimates = new ArrayList<>();
        List<String> estimatedNames = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            MonthlyEstimate est;
            if (plan.hasTouWindows() && hourly != null) {
                boolean estimated = rawHourly[i] == null;
                est = calculateTouMonth(plan, hourly[i], i, estimated);
            } else {
                boolean estimated = Double.isNaN(rawProfile[i]);
                est = calculateFlatMonth(plan, profile[i], i, estimated);
            }
            estimates.add(est);
            if (est.estimated()) {
                estimatedNames.add(Month.of(i + 1)
                        .getDisplayName(TextStyle.FULL, Locale.getDefault()));
            }
        }

        double annual = estimates.stream().mapToDouble(MonthlyEstimate::totalCost).sum();
        MonthlyEstimate highest = estimates.stream()
                .max((a, b) -> Double.compare(a.totalCost(), b.totalCost())).orElseThrow();
        MonthlyEstimate lowest = estimates.stream()
                .min((a, b) -> Double.compare(a.totalCost(), b.totalCost())).orElseThrow();
        double totalKwh = estimates.stream().mapToDouble(MonthlyEstimate::avgKwh).sum();
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

    // ── Month calculation helpers ─────────────────────────────────────────────

    private MonthlyEstimate calculateFlatMonth(RatePlan plan, double kwh, int monthIdx, boolean estimated) {
        double energy    = kwh * plan.getRatePerKwh();
        double discounts = applicableDiscounts(plan, kwh);
        double total     = plan.getBaseCharge() + energy - discounts;
        return new MonthlyEstimate(monthIdx + 1, kwh, estimated,
                plan.getBaseCharge(), energy, discounts, total);
    }

    private MonthlyEstimate calculateTouMonth(RatePlan plan, double[] hourlyAvg,
                                              int monthIdx, boolean estimated) {
        double totalKwh  = 0;
        double energy    = 0;
        for (int h = 0; h < 24; h++) {
            double kwh = hourlyAvg[h];
            totalKwh += kwh;
            energy   += kwh * rateForHour(plan, h);
        }
        double discounts = applicableDiscounts(plan, totalKwh);
        double total     = plan.getBaseCharge() + energy - discounts;
        return new MonthlyEstimate(monthIdx + 1, totalKwh, estimated,
                plan.getBaseCharge(), energy, discounts, total);
    }

    // ── TOU helpers ───────────────────────────────────────────────────────────

    /** Returns the $/kWh rate for the given hour: first matching TOU window, or the base rate. */
    private double rateForHour(RatePlan plan, int hour) {
        for (TouWindow w : plan.getTouWindows()) {
            if (w.containsHour(hour)) return w.getRatePerKwh();
        }
        return plan.getRatePerKwh();
    }

    private double applicableDiscounts(RatePlan plan, double kwh) {
        return plan.getDiscounts().stream()
                .filter(d -> kwh >= d.getThresholdKwh())
                .mapToDouble(TierDiscount::getDiscountAmt)
                .sum();
    }

    // ── Profile substitution ──────────────────────────────────────────────────

    /** Replaces NaN entries with the mean of all valid months. */
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

    /**
     * For hourly profiles, fills null month rows with the average hourly profile
     * computed across all months that do have data.
     */
    private double[][] substituteHourlyAverage(double[][] raw) {
        double[] globalHourly = new double[24];
        int count = 0;
        for (double[] month : raw) {
            if (month != null) {
                for (int h = 0; h < 24; h++) globalHourly[h] += month[h];
                count++;
            }
        }
        if (count > 0) {
            for (int h = 0; h < 24; h++) globalHourly[h] /= count;
        }

        double[][] result = new double[12][];
        for (int m = 0; m < 12; m++) {
            result[m] = raw[m] != null ? raw[m] : globalHourly;
        }
        return result;
    }
}
