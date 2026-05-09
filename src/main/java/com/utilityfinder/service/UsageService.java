package com.utilityfinder.service;

import com.utilityfinder.model.UsageRecord;
import com.utilityfinder.repository.UsageRepository;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

public class UsageService {

    private final UsageRepository repo;

    public UsageService(UsageRepository repo) {
        this.repo = repo;
    }

    public List<UsageRecord> findByWorkspace(long workspaceId) {
        return repo.findByWorkspace(workspaceId);
    }

    public UsageRecord save(UsageRecord record) {
        if (record.getKwhUsed() <= 0)
            throw new IllegalArgumentException("kWh must be greater than zero.");

        boolean duplicate = repo.findByWorkspaceYearMonth(
                record.getWorkspaceId(), record.getYear(), record.getMonth()).isPresent();
        if (duplicate) {
            String name = Month.of(record.getMonth())
                    .getDisplayName(TextStyle.FULL, Locale.getDefault());
            throw new IllegalArgumentException(
                    name + " " + record.getYear() + " already has a record for this workspace.");
        }
        return repo.save(record);
    }

    public void update(UsageRecord record) {
        if (record.getKwhUsed() <= 0)
            throw new IllegalArgumentException("kWh must be greater than zero.");
        repo.update(record);
    }

    public void delete(long id) {
        repo.delete(id);
    }

    /**
     * Returns the averaged monthly kWh profile for the workspace.
     * Index 0 = January … index 11 = December.
     * {@code Double.NaN} for any month that has no recorded data.
     */
    public double[] getAveragedProfile(long workspaceId) {
        double[] profile = new double[12];
        Arrays.fill(profile, Double.NaN);

        Map<Integer, DoubleSummaryStatistics> byMonth = repo.findByWorkspace(workspaceId)
                .stream()
                .collect(Collectors.groupingBy(
                        UsageRecord::getMonth,
                        Collectors.summarizingDouble(UsageRecord::getKwhUsed)));

        byMonth.forEach((month, stats) -> profile[month - 1] = stats.getAverage());
        return profile;
    }
}
