package com.utilityfinder.service;

import com.utilityfinder.repository.InsightsRepository;

import java.time.LocalDate;
import java.util.*;

public class InsightsService {

    private final InsightsRepository repo;

    public InsightsService(InsightsRepository repo) {
        this.repo = repo;
    }

    public double[] getHourlyProfile(long workspaceId, int[] seasonMonths) {
        return repo.getHourlyProfile(workspaceId, seasonMonths);
    }

    public double[] getDayOfWeekProfile(long workspaceId, int[] seasonMonths) {
        return repo.getDayOfWeekProfile(workspaceId, seasonMonths);
    }

    public List<Map.Entry<LocalDate, Double>> getDailyTotals(long workspaceId) {
        return repo.getDailyTotals(workspaceId);
    }

    public Map<Integer, double[]> getYearlyMonthlyTotals(long workspaceId) {
        return repo.getYearlyMonthlyTotals(workspaceId);
    }

    public Map<Integer, double[]> getSeasonalHourlyProfiles(long workspaceId) {
        return repo.getSeasonalHourlyProfiles(workspaceId);
    }

    public double[] getEstimatedVsActual(long workspaceId) {
        return repo.getEstimatedVsActual(workspaceId);
    }

    public Map<Integer, double[]> getDayOfWeekHourlyProfiles(long workspaceId, int[] seasonMonths) {
        return repo.getDayOfWeekHourlyProfiles(workspaceId, seasonMonths);
    }

    public double[] getTimeBlockTotals(long workspaceId) {
        return repo.getTimeBlockTotals(workspaceId);
    }

    /**
     * Computes a trailing N-day rolling average from a sorted list of daily totals.
     * Returns a parallel list of double[]{epochDay, rollingAvgKwh}.
     */
    public List<double[]> getRollingAverage(List<Map.Entry<LocalDate, Double>> dailyTotals, int days) {
        List<double[]> result = new ArrayList<>(dailyTotals.size());
        int n = dailyTotals.size();
        for (int i = 0; i < n; i++) {
            int start = Math.max(0, i - days + 1);
            double sum = 0;
            for (int j = start; j <= i; j++) sum += dailyTotals.get(j).getValue();
            long epochDay = dailyTotals.get(i).getKey().toEpochDay();
            result.add(new double[]{epochDay, sum / (i - start + 1)});
        }
        return result;
    }
}
