package com.utilityfinder.model;

import java.time.LocalDate;

public record MonthSummary(
        int year,
        int month,
        double totalKwh,
        int daysWithData,
        double dailyAvgKwh,
        LocalDate peakDate,
        double peakKwh
) {}
