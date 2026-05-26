package com.utilityfinder.service;

import com.utilityfinder.repository.InsightsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    @Mock InsightsRepository repo;
    InsightsService service;

    @BeforeEach void setUp() {
        service = new InsightsService(repo);
    }

    // ── getRollingAverage ─────────────────────────────────────────────────────

    @Test void rollingAverage_emptyInput_returnsEmpty() {
        List<double[]> result = service.getRollingAverage(List.of(), 7);
        assertTrue(result.isEmpty());
    }

    @Test void rollingAverage_singleDay_returnsThatDay() {
        var input = List.of(entry(LocalDate.of(2026, 1, 1), 10.0));
        List<double[]> result = service.getRollingAverage(input, 7);

        assertEquals(1, result.size());
        assertEquals(10.0, result.get(0)[1], 0.001);
    }

    @Test void rollingAverage_windowLargerThanData_usesAvailableDays() {
        // 3 days, 30-day window — each day averages only the days available so far
        var input = List.of(
                entry(LocalDate.of(2026, 1, 1), 10.0),
                entry(LocalDate.of(2026, 1, 2), 20.0),
                entry(LocalDate.of(2026, 1, 3), 30.0)
        );
        List<double[]> result = service.getRollingAverage(input, 30);

        assertEquals(10.0, result.get(0)[1], 0.001);              // avg(10) = 10
        assertEquals(15.0, result.get(1)[1], 0.001);              // avg(10,20) = 15
        assertEquals(20.0, result.get(2)[1], 0.001);              // avg(10,20,30) = 20
    }

    @Test void rollingAverage_fullWindow_averagesCorrectly() {
        // 5 days, 3-day window
        var input = List.of(
                entry(LocalDate.of(2026, 1, 1), 10.0),
                entry(LocalDate.of(2026, 1, 2), 20.0),
                entry(LocalDate.of(2026, 1, 3), 30.0),
                entry(LocalDate.of(2026, 1, 4), 40.0),
                entry(LocalDate.of(2026, 1, 5), 50.0)
        );
        List<double[]> result = service.getRollingAverage(input, 3);

        assertEquals(10.0,  result.get(0)[1], 0.001);  // avg(10)
        assertEquals(15.0,  result.get(1)[1], 0.001);  // avg(10,20)
        assertEquals(20.0,  result.get(2)[1], 0.001);  // avg(10,20,30)
        assertEquals(30.0,  result.get(3)[1], 0.001);  // avg(20,30,40)
        assertEquals(40.0,  result.get(4)[1], 0.001);  // avg(30,40,50)
    }

    @Test void rollingAverage_windowOfOne_returnsEachDayUnchanged() {
        var input = List.of(
                entry(LocalDate.of(2026, 6, 1), 5.0),
                entry(LocalDate.of(2026, 6, 2), 15.0)
        );
        List<double[]> result = service.getRollingAverage(input, 1);

        assertEquals(5.0,  result.get(0)[1], 0.001);
        assertEquals(15.0, result.get(1)[1], 0.001);
    }

    @Test void rollingAverage_epochDayMatchesDate() {
        LocalDate date = LocalDate.of(2026, 5, 1);
        var input = List.of(entry(date, 8.0));

        List<double[]> result = service.getRollingAverage(input, 7);

        assertEquals(date.toEpochDay(), (long) result.get(0)[0]);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Map.Entry<LocalDate, Double> entry(LocalDate date, double kwh) {
        return new AbstractMap.SimpleEntry<>(date, kwh);
    }
}
