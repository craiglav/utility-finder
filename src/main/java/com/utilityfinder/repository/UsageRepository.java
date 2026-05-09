package com.utilityfinder.repository;

import com.utilityfinder.model.UsageRecord;

import java.util.List;
import java.util.Optional;

public class UsageRepository {

    public List<UsageRecord> findByWorkspace(long workspaceId) {
        throw new UnsupportedOperationException("TODO");
    }

    public Optional<UsageRecord> findByWorkspaceYearMonth(long workspaceId, int year, int month) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Inserts a new record and returns it with the generated id set. */
    public UsageRecord save(UsageRecord record) {
        throw new UnsupportedOperationException("TODO");
    }

    public void update(UsageRecord record) {
        throw new UnsupportedOperationException("TODO");
    }

    public void delete(long id) {
        throw new UnsupportedOperationException("TODO");
    }
}
