package com.utilityfinder.service;

import com.utilityfinder.model.PlanSummary;
import com.utilityfinder.model.RatePlan;
import com.utilityfinder.model.TierDiscount;
import com.utilityfinder.model.TouWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    @Mock IntervalService intervalService;
    @Mock RatePlanService ratePlanService;

    ComparisonService service;

    static final long WS = 1L;

    @BeforeEach void setUp() {
        service = new ComparisonService(intervalService, ratePlanService);
    }

    // ── Flat plan — basic billing ─────────────────────────────────────────────

    @Test void flatPlan_annualCostIsBaseTimesMonthsPlusEnergy() {
        // 1000 kWh/month, base=$9.95, rate=$0.10/kWh → monthly=$109.95, annual=$1319.40
        double[] profile = uniform(1000.0);
        RatePlan plan = flatPlan(9.95, 0.10);

        when(intervalService.getAveragedProfile(WS)).thenReturn(profile);
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);

        assertEquals(1319.40, summary.annualCost(), 0.001);
    }

    @Test void flatPlan_effectiveRateIsAnnualDividedByTotalKwh() {
        double[] profile = uniform(1000.0);
        RatePlan plan = flatPlan(9.95, 0.10);

        when(intervalService.getAveragedProfile(WS)).thenReturn(profile);
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        double expectedEffective = summary.annualCost() / (1000.0 * 12);
        assertEquals(expectedEffective, summary.effectiveAvgPerKwh(), 0.0001);
    }

    // ── Tier discounts ────────────────────────────────────────────────────────

    @Test void discount_notAppliedWhenBelowThreshold() {
        // 900 kWh — threshold is 1000, so no discount
        RatePlan plan = flatPlan(9.95, 0.10);
        plan.setDiscounts(List.of(discount(1000, 50.0)));

        when(intervalService.getAveragedProfile(WS)).thenReturn(uniform(900.0));
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        double expected = (9.95 + 900 * 0.10) * 12;
        assertEquals(expected, summary.annualCost(), 0.001);
    }

    @Test void discount_appliedWhenAtThreshold() {
        // 1000 kWh — threshold is 1000 (>=), flat discount $50/month
        RatePlan plan = flatPlan(9.95, 0.10);
        plan.setDiscounts(List.of(discount(1000, 50.0)));

        when(intervalService.getAveragedProfile(WS)).thenReturn(uniform(1000.0));
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        // monthly = 9.95 + 1000*0.10 - 50.0 = 59.95
        assertEquals(59.95 * 12, summary.annualCost(), 0.001);
    }

    @Test void discount_multipleDiscountsStack() {
        // Two tiers: 500 kWh → $20 off, 1000 kWh → $30 off — both apply at 1000 kWh
        RatePlan plan = flatPlan(0, 0.12);
        plan.setDiscounts(List.of(discount(500, 20.0), discount(1000, 30.0)));

        when(intervalService.getAveragedProfile(WS)).thenReturn(uniform(1000.0));
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        // monthly = 0 + 1000*0.12 - (20 + 30) = 120 - 50 = 70
        assertEquals(70.0 * 12, summary.annualCost(), 0.001);
    }

    // ── TOU billing ───────────────────────────────────────────────────────────

    @Test void tou_freeNightHoursReduceCost() {
        // Night window 21–7 (10 hours) free; day hours at $0.12/kWh
        // 1.0 kWh/hour uniform: night = free, day (7–21 = 14h) = 14 * 0.12 = 1.68/day
        double[] hourly = uniform24(1.0);
        double[][] monthly = uniform12x24(hourly);

        RatePlan plan = flatPlan(0, 0.12);
        plan.setTouWindows(List.of(new TouWindow(21, 7, 0.0, 0)));

        when(intervalService.getAveragedProfile(WS)).thenReturn(new double[12]);
        when(intervalService.getHourlyProfileByMonth(WS)).thenReturn(monthly);
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        // 14 day-hours * 1.0 kWh * $0.12 = $1.68/month
        assertEquals(1.68 * 12, summary.annualCost(), 0.001);
    }

    @Test void tou_midnightWrappingWindowApplied() {
        // Window 23–1 (wraps midnight): hours 23 and 0 are free
        double[] hourly = new double[24];
        Arrays.fill(hourly, 1.0);
        double[][] monthly = uniform12x24(hourly);

        RatePlan plan = flatPlan(0, 0.10);
        plan.setTouWindows(List.of(new TouWindow(23, 1, 0.0, 0)));

        when(intervalService.getAveragedProfile(WS)).thenReturn(new double[12]);
        when(intervalService.getHourlyProfileByMonth(WS)).thenReturn(monthly);
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        // 22 paid hours * 1.0 kWh * $0.10 = $2.20/month
        assertEquals(2.20 * 12, summary.annualCost(), 0.001);
    }

    // ── Profile substitution ──────────────────────────────────────────────────

    @Test void missingMonths_substitutedWithGlobalAverage() {
        // 6 months with 1200 kWh, 6 months NaN → global avg = 1200
        double[] profile = new double[12];
        for (int i = 0; i < 6; i++) profile[i] = 1200.0;
        for (int i = 6; i < 12; i++) profile[i] = Double.NaN;

        RatePlan plan = flatPlan(0, 0.10);

        when(intervalService.getAveragedProfile(WS)).thenReturn(profile);
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        // All 12 months use 1200 kWh @ $0.10 = $120/month
        assertEquals(120.0 * 12, summary.annualCost(), 0.001);
    }

    @Test void missingMonths_estimatedNamesRecorded() {
        double[] profile = new double[12];
        profile[0] = 1000.0;
        for (int i = 1; i < 12; i++) profile[i] = Double.NaN;

        RatePlan plan = flatPlan(0, 0.10);

        when(intervalService.getAveragedProfile(WS)).thenReturn(profile);
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        assertEquals(11, summary.estimatedMonthNames().size());
    }

    @Test void allMonthsMissing_substitutedWithZero() {
        double[] profile = new double[12];
        Arrays.fill(profile, Double.NaN);

        RatePlan plan = flatPlan(9.95, 0.10);

        when(intervalService.getAveragedProfile(WS)).thenReturn(profile);
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        // 0 kWh, only base charges: 9.95 * 12 = 119.40
        assertEquals(9.95 * 12, summary.annualCost(), 0.001);
    }

    // ── Early termination fee ─────────────────────────────────────────────────

    @Test void etf_flatFeeApplied() {
        RatePlan plan = flatPlan(0, 0.10);
        plan.setCurrent(true);
        plan.setTerminationFeeFlat(150.0);
        plan.setContractEndDate(LocalDate.now().plusMonths(6));

        when(intervalService.getAveragedProfile(WS)).thenReturn(uniform(500.0));
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        assertEquals(150.0, summary.terminationFee(), 0.001);
    }

    @Test void etf_perMonthFeeMultipliedByRemainingMonths() {
        RatePlan plan = flatPlan(0, 0.10);
        plan.setCurrent(true);
        plan.setTerminationFeePerMonth(25.0);
        // 4 months from now — remaining = 4
        LocalDate endDate = LocalDate.now().plusMonths(4);
        plan.setContractEndDate(endDate);

        when(intervalService.getAveragedProfile(WS)).thenReturn(uniform(500.0));
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        assertEquals(25.0 * 4, summary.terminationFee(), 0.001);
    }

    @Test void etf_waivedWithin14Days() {
        RatePlan plan = flatPlan(0, 0.10);
        plan.setCurrent(true);
        plan.setTerminationFeeFlat(200.0);
        plan.setContractEndDate(LocalDate.now().plusDays(7));

        when(intervalService.getAveragedProfile(WS)).thenReturn(uniform(500.0));
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        assertEquals(0.0, summary.terminationFee(), 0.001);
    }

    @Test void etf_notWaivedBeyond14Days() {
        RatePlan plan = flatPlan(0, 0.10);
        plan.setCurrent(true);
        plan.setTerminationFeeFlat(200.0);
        plan.setContractEndDate(LocalDate.now().plusDays(15));

        when(intervalService.getAveragedProfile(WS)).thenReturn(uniform(500.0));
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        assertTrue(summary.terminationFee() > 0);
    }

    @Test void etf_noContractEndDate_flatFeeAppliedOnce() {
        RatePlan plan = flatPlan(0, 0.10);
        plan.setCurrent(true);
        plan.setTerminationFeeFlat(100.0);
        plan.setTerminationFeePerMonth(20.0);
        // no contractEndDate

        when(intervalService.getAveragedProfile(WS)).thenReturn(uniform(500.0));
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        // flat + perMonth (once, no multiplication)
        assertEquals(120.0, summary.terminationFee(), 0.001);
    }

    @Test void etf_nonCurrentPlan_alwaysZero() {
        RatePlan plan = flatPlan(0, 0.10);
        plan.setCurrent(false);
        plan.setTerminationFeeFlat(500.0);

        when(intervalService.getAveragedProfile(WS)).thenReturn(uniform(500.0));
        when(ratePlanService.findByWorkspace(WS)).thenReturn(List.of(plan));

        PlanSummary summary = service.compare(WS).get(0);
        assertEquals(0.0, summary.terminationFee(), 0.001);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RatePlan flatPlan(double base, double rate) {
        RatePlan p = new RatePlan();
        p.setProviderName("Test Provider");
        p.setPlanName("Test Plan");
        p.setBaseCharge(base);
        p.setRatePerKwh(rate);
        return p;
    }

    private static TierDiscount discount(double threshold, double amount) {
        TierDiscount d = new TierDiscount();
        d.setThresholdKwh(threshold);
        d.setDiscountAmt(amount);
        return d;
    }

    private static double[] uniform(double kwh) {
        double[] a = new double[12];
        Arrays.fill(a, kwh);
        return a;
    }

    private static double[] uniform24(double kwh) {
        double[] a = new double[24];
        Arrays.fill(a, kwh);
        return a;
    }

    private static double[][] uniform12x24(double[] hourly) {
        double[][] a = new double[12][];
        for (int i = 0; i < 12; i++) a[i] = hourly.clone();
        return a;
    }
}
