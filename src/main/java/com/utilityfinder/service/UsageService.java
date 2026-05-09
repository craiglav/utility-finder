package com.utilityfinder.service;

import com.utilityfinder.model.UsageRecord;
import com.utilityfinder.repository.UsageRepository;

import java.util.List;

public class UsageService {

    private final UsageRepository repo;

    public UsageService(UsageRepository repo) {
        this.repo = repo;
    }

    public List<UsageRecord> findByWorkspace(long workspaceId) {
        throw new UnsupportedOperationException("TODO");
    }

    public UsageRecord save(UsageRecord record) {
        throw new UnsupportedOperationException("TODO");
    }

    public void update(UsageRecord record) {
        throw new UnsupportedOperationException("TODO");
    }

    public void delete(long id) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Returns the averaged monthly kWh profile for the workspace.
     * Index 0 = January, index 11 = December.
     * Months with no data carry {@code Double.NaN}; ComparisonService
     * substitutes the global average for those.
     */
    public double[] getAveragedProfile(long workspaceId) {
        throw new UnsupportedOperationException("TODO");
    }
}
