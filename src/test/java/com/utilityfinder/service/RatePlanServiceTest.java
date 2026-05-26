package com.utilityfinder.service;

import com.utilityfinder.model.RatePlan;
import com.utilityfinder.model.TierDiscount;
import com.utilityfinder.model.TouWindow;
import com.utilityfinder.repository.RatePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RatePlanServiceTest {

    @Mock RatePlanRepository repo;
    RatePlanService service;

    @BeforeEach void setUp() {
        service = new RatePlanService(repo);
        // lenient: validation tests throw before save is called
        lenient().when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Valid plan passes validation ──────────────────────────────────────────

    @Test void validPlan_savesSuccessfully() {
        assertDoesNotThrow(() -> service.save(validPlan()));
        verify(repo).save(any());
    }

    // ── Provider / plan name ──────────────────────────────────────────────────

    @Test void nullProviderName_throws() {
        RatePlan p = validPlan();
        p.setProviderName(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void blankProviderName_throws() {
        RatePlan p = validPlan();
        p.setProviderName("   ");
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void nullPlanName_throws() {
        RatePlan p = validPlan();
        p.setPlanName(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void blankPlanName_throws() {
        RatePlan p = validPlan();
        p.setPlanName("");
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    // ── Rate / base charge ────────────────────────────────────────────────────

    @Test void zeroRate_throws() {
        RatePlan p = validPlan();
        p.setRatePerKwh(0.0);
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void negativeRate_throws() {
        RatePlan p = validPlan();
        p.setRatePerKwh(-0.01);
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void negativeBaseCharge_throws() {
        RatePlan p = validPlan();
        p.setBaseCharge(-1.0);
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void zeroBaseCharge_allowed() {
        RatePlan p = validPlan();
        p.setBaseCharge(0.0);
        assertDoesNotThrow(() -> service.save(p));
    }

    // ── Contract term ─────────────────────────────────────────────────────────

    @Test void contractTermZero_throws() {
        RatePlan p = validPlan();
        p.setContractTermMonths(0);
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void contractTermOne_allowed() {
        RatePlan p = validPlan();
        p.setContractTermMonths(1);
        assertDoesNotThrow(() -> service.save(p));
    }

    @Test void nullContractTerm_allowed() {
        RatePlan p = validPlan();
        p.setContractTermMonths(null);
        assertDoesNotThrow(() -> service.save(p));
    }

    // ── Tier discounts ────────────────────────────────────────────────────────

    @Test void discountThresholdZero_throws() {
        RatePlan p = validPlan();
        p.setDiscounts(List.of(discount(0, 0.05)));
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void discountAmountZero_throws() {
        RatePlan p = validPlan();
        p.setDiscounts(List.of(discount(1000, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void validDiscount_allowed() {
        RatePlan p = validPlan();
        p.setDiscounts(List.of(discount(1000, 0.05)));
        assertDoesNotThrow(() -> service.save(p));
    }

    // ── TOU windows ───────────────────────────────────────────────────────────

    @Test void touWindowSameHour_throws() {
        RatePlan p = validPlan();
        p.setTouWindows(List.of(new TouWindow(9, 9, 0.0, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void touWindowNegativeRate_throws() {
        RatePlan p = validPlan();
        p.setTouWindows(List.of(new TouWindow(21, 7, -0.01, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.save(p));
    }

    @Test void touWindowZeroRate_allowed() {
        RatePlan p = validPlan();
        p.setTouWindows(List.of(new TouWindow(21, 7, 0.0, 0)));
        assertDoesNotThrow(() -> service.save(p));
    }

    // ── Current flag clears others ────────────────────────────────────────────

    @Test void markingCurrent_clearsPreviousCurrentFlags() {
        RatePlan p = validPlan();
        p.setCurrent(true);
        p.setWorkspaceId(1L);

        service.save(p);

        verify(repo).clearCurrentFlags(1L);
    }

    @Test void nonCurrentPlan_doesNotClearFlags() {
        RatePlan p = validPlan();
        p.setCurrent(false);

        service.save(p);

        verify(repo, never()).clearCurrentFlags(anyLong());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RatePlan validPlan() {
        RatePlan p = new RatePlan();
        p.setProviderName("Acme Energy");
        p.setPlanName("12-Month Fixed");
        p.setBaseCharge(9.95);
        p.setRatePerKwh(0.10);
        return p;
    }

    private static TierDiscount discount(double threshold, double amount) {
        TierDiscount d = new TierDiscount();
        d.setThresholdKwh(threshold);
        d.setDiscountAmt(amount);
        return d;
    }
}
