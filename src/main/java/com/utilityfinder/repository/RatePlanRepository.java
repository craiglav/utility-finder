package com.utilityfinder.repository;

import com.utilityfinder.model.RatePlan;
import com.utilityfinder.model.TierDiscount;

import java.util.List;
import java.util.Optional;

public class RatePlanRepository {

    public List<RatePlan> findByWorkspace(long workspaceId) {
        throw new UnsupportedOperationException("TODO");
    }

    public Optional<RatePlan> findById(long id) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Inserts a new plan (without discounts) and returns it with the generated id set. */
    public RatePlan save(RatePlan plan) {
        throw new UnsupportedOperationException("TODO");
    }

    public void update(RatePlan plan) {
        throw new UnsupportedOperationException("TODO");
    }

    public void delete(long id) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Replaces all discounts for the given plan. */
    public void saveDiscounts(long planId, List<TierDiscount> discounts) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<TierDiscount> findDiscountsByPlan(long planId) {
        throw new UnsupportedOperationException("TODO");
    }
}
