package com.utilityfinder.service;

import com.utilityfinder.model.RatePlan;
import com.utilityfinder.model.TierDiscount;
import com.utilityfinder.model.TouWindow;
import com.utilityfinder.repository.RatePlanRepository;

import java.util.List;

public class RatePlanService {

    private final RatePlanRepository repo;

    public RatePlanService(RatePlanRepository repo) {
        this.repo = repo;
    }

    public List<RatePlan> findByWorkspace(long workspaceId) {
        return repo.findByWorkspace(workspaceId);
    }

    public RatePlan save(RatePlan plan) {
        validate(plan);
        if (plan.isCurrent()) repo.clearCurrentFlags(plan.getWorkspaceId());
        return repo.save(plan);
    }

    public void update(RatePlan plan) {
        validate(plan);
        if (plan.isCurrent()) repo.clearCurrentFlags(plan.getWorkspaceId());
        repo.update(plan);
    }

    public void delete(long id) {
        repo.delete(id);
    }

    public void markAsCurrent(long planId, long workspaceId) {
        repo.markAsCurrent(planId, workspaceId);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validate(RatePlan plan) {
        if (plan.getProviderName() == null || plan.getProviderName().isBlank())
            throw new IllegalArgumentException("Provider name is required.");
        if (plan.getPlanName() == null || plan.getPlanName().isBlank())
            throw new IllegalArgumentException("Plan name is required.");
        if (plan.getRatePerKwh() <= 0)
            throw new IllegalArgumentException("Rate must be greater than zero.");
        if (plan.getBaseCharge() < 0)
            throw new IllegalArgumentException("Base charge cannot be negative.");
        if (plan.getContractTermMonths() != null && plan.getContractTermMonths() < 1)
            throw new IllegalArgumentException("Contract term must be at least 1 month.");

        for (TierDiscount d : plan.getDiscounts()) {
            if (d.getThresholdKwh() <= 0)
                throw new IllegalArgumentException("Discount threshold must be greater than zero.");
            if (d.getDiscountAmt() <= 0)
                throw new IllegalArgumentException("Discount amount must be greater than zero.");
        }

        for (TouWindow w : plan.getTouWindows()) {
            if (w.getStartHour() == w.getEndHour())
                throw new IllegalArgumentException(
                        "TOU window start and end hours cannot be the same.");
            if (w.getRatePerKwh() < 0)
                throw new IllegalArgumentException(
                        "TOU window rate cannot be negative.");
        }
    }
}
