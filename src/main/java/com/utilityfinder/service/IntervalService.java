package com.utilityfinder.service;

import com.utilityfinder.model.MonthSummary;
import com.utilityfinder.repository.IntervalRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class IntervalService {

    private final IntervalRepository repo;

    public IntervalService(IntervalRepository repo) {
        this.repo = repo;
    }

    /** 12-element averaged monthly kWh profile (index 0 = Jan). NaN = no data. */
    public double[] getAveragedProfile(long workspaceId) {
        return repo.getAveragedMonthlyProfile(workspaceId);
    }

    /** 12×24 hourly profile [month][hour]. Null rows = months with no data. */
    public double[][] getHourlyProfileByMonth(long workspaceId) {
        return repo.getHourlyProfileByMonth(workspaceId);
    }

    public boolean hasData(long workspaceId) {
        return repo.hasData(workspaceId);
    }

    public List<Integer> getDistinctYears(long workspaceId) {
        return repo.getDistinctYears(workspaceId);
    }

    public List<MonthSummary> getMonthlySummaries(long workspaceId) {
        return repo.getMonthlySummaries(workspaceId);
    }

    public Optional<String> getEsiid(long workspaceId) {
        return repo.getEsiid(workspaceId);
    }

    public Optional<LocalDate> getMinDate(long workspaceId) {
        return repo.getMinDate(workspaceId);
    }

    public Optional<LocalDate> getMaxDate(long workspaceId) {
        return repo.getMaxDate(workspaceId);
    }

    public long getTotalCount(long workspaceId) {
        return repo.getTotalCount(workspaceId);
    }

    public Optional<LocalDateTime> getLastImportTime(long workspaceId) {
        return repo.getLastImportTime(workspaceId);
    }

    public void deleteByWorkspace(long workspaceId) {
        repo.deleteByWorkspace(workspaceId);
    }
}
