package com.utilityfinder.service;

import com.utilityfinder.model.RatePlan;
import com.utilityfinder.repository.RatePlanRepository;

import java.util.List;

public class RatePlanService {

    private final RatePlanRepository repo;

    public RatePlanService(RatePlanRepository repo) {
        this.repo = repo;
    }

    public List<RatePlan> findByWorkspace(long workspaceId) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Saves the plan and its tier discounts in one operation. */
    public RatePlan save(RatePlan plan) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Updates the plan and replaces its tier discounts. */
    public void update(RatePlan plan) {
        throw new UnsupportedOperationException("TODO");
    }

    public void delete(long id) {
        throw new UnsupportedOperationException("TODO");
    }

    public void markAsCurrent(long planId, long workspaceId) {
        throw new UnsupportedOperationException("TODO");
    }
}
