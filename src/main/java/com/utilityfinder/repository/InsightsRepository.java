package com.utilityfinder.repository;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class InsightsRepository {

    /**
     * Average kWh per hour-of-day (index 0 = midnight, 23 = 11 pm).
     * Computed as: sum(kWh) per (date, hour), then average across days.
     * {@code seasonMonths} is null for all data, or an array of MONTH() values (1–12).
     */
    public double[] getHourlyProfile(long workspaceId, int[] seasonMonths) {
        StringBuilder sql = new StringBuilder("""
                SELECT hr, AVG(hourly_kwh) AS avg_kwh
                FROM (
                    SELECT reading_date,
                           FLOOR(start_minute / 60) AS hr,
                           SUM(kwh) AS hourly_kwh
                    FROM interval_record
                    WHERE workspace_id = ?
                """);
        if (seasonMonths != null && seasonMonths.length > 0) {
            sql.append("  AND MONTH(reading_date) IN (");
            for (int i = 0; i < seasonMonths.length; i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
            }
            sql.append(")\n");
        }
        sql.append("""
                    GROUP BY reading_date, FLOOR(start_minute / 60)
                ) sub
                GROUP BY hr
                ORDER BY hr
                """);

        double[] result = new double[24];
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, workspaceId);
            if (seasonMonths != null) {
                for (int i = 0; i < seasonMonths.length; i++) {
                    ps.setInt(2 + i, seasonMonths[i]);
                }
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int hr = rs.getInt("hr");
                if (hr >= 0 && hr < 24) result[hr] = rs.getDouble("avg_kwh");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load hourly profile", e);
        }
        return result;
    }

    /**
     * Average daily kWh by day-of-week.
     * Index 0 = Sunday, matching H2's DAYOFWEEK() convention (1-based, 1=Sun).
     */
    /**
     * Optional {@code seasonMonths} narrows to those calendar months (null = all).
     */
    public double[] getDayOfWeekProfile(long workspaceId, int[] seasonMonths) {
        StringBuilder sql = new StringBuilder("""
                SELECT DAYOFWEEK(reading_date) AS dow, AVG(day_kwh) AS avg_kwh
                FROM (
                    SELECT reading_date, SUM(kwh) AS day_kwh
                    FROM interval_record
                    WHERE workspace_id = ?
                """);
        if (seasonMonths != null && seasonMonths.length > 0) {
            sql.append("  AND MONTH(reading_date) IN (");
            for (int i = 0; i < seasonMonths.length; i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
            }
            sql.append(")\n");
        }
        sql.append("""
                    GROUP BY reading_date
                ) sub
                GROUP BY dow
                ORDER BY dow
                """);
        double[] result = new double[7];
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, workspaceId);
            if (seasonMonths != null) {
                for (int i = 0; i < seasonMonths.length; i++) ps.setInt(2 + i, seasonMonths[i]);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int dow = rs.getInt("dow") - 1; // 1–7 → 0–6
                if (dow >= 0 && dow < 7) result[dow] = rs.getDouble("avg_kwh");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load day-of-week profile", e);
        }
        return result;
    }

    /** Total kWh per day, sorted ascending by date. */
    public List<Map.Entry<LocalDate, Double>> getDailyTotals(long workspaceId) {
        String sql = """
                SELECT reading_date, SUM(kwh) AS day_kwh
                FROM interval_record
                WHERE workspace_id = ?
                GROUP BY reading_date
                ORDER BY reading_date
                """;
        List<Map.Entry<LocalDate, Double>> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(Map.entry(
                        rs.getDate("reading_date").toLocalDate(),
                        rs.getDouble("day_kwh")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load daily totals", e);
        }
        return result;
    }

    /**
     * Total kWh per (year, month).
     * Returns Map&lt;year, double[12]&gt; (index 0 = Jan).
     */
    public Map<Integer, double[]> getYearlyMonthlyTotals(long workspaceId) {
        String sql = """
                SELECT YEAR(reading_date) AS yr, MONTH(reading_date) AS mo, SUM(kwh) AS total_kwh
                FROM interval_record
                WHERE workspace_id = ?
                GROUP BY yr, mo
                ORDER BY yr, mo
                """;
        Map<Integer, double[]> result = new TreeMap<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int yr = rs.getInt("yr");
                int mo = rs.getInt("mo") - 1; // 1–12 → 0–11
                result.computeIfAbsent(yr, k -> new double[12])[mo] = rs.getDouble("total_kwh");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load yearly monthly totals", e);
        }
        return result;
    }

    /**
     * Average kWh per hour-of-day, grouped by season.
     * Returns Map&lt;seasonIndex, double[24]&gt;: 0=Winter, 1=Spring, 2=Summer, 3=Fall.
     */
    public Map<Integer, double[]> getSeasonalHourlyProfiles(long workspaceId) {
        String sql = """
                SELECT season, hr, AVG(hourly_kwh) AS avg_kwh
                FROM (
                    SELECT reading_date,
                           CASE
                               WHEN MONTH(reading_date) IN (12, 1, 2) THEN 0
                               WHEN MONTH(reading_date) IN (3, 4, 5)  THEN 1
                               WHEN MONTH(reading_date) IN (6, 7, 8)  THEN 2
                               ELSE 3
                           END AS season,
                           FLOOR(start_minute / 60) AS hr,
                           SUM(kwh) AS hourly_kwh
                    FROM interval_record
                    WHERE workspace_id = ?
                    GROUP BY reading_date, FLOOR(start_minute / 60)
                ) sub
                GROUP BY season, hr
                ORDER BY season, hr
                """;
        Map<Integer, double[]> result = new TreeMap<>();
        for (int i = 0; i < 4; i++) result.put(i, new double[24]);
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int season = rs.getInt("season");
                int hr = rs.getInt("hr");
                if (season >= 0 && season < 4 && hr >= 0 && hr < 24) {
                    result.get(season)[hr] = rs.getDouble("avg_kwh");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load seasonal profiles", e);
        }
        return result;
    }

    /**
     * Total kWh split between estimated and actual readings.
     * Returns double[]{estimatedKwh, actualKwh}.
     */
    public double[] getEstimatedVsActual(long workspaceId) {
        String sql = """
                SELECT
                    SUM(CASE WHEN estimated = TRUE  THEN kwh ELSE 0 END) AS est_kwh,
                    SUM(CASE WHEN estimated = FALSE THEN kwh ELSE 0 END) AS actual_kwh
                FROM interval_record
                WHERE workspace_id = ?
                """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new double[]{rs.getDouble("est_kwh"), rs.getDouble("actual_kwh")};
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load estimated vs actual", e);
        }
        return new double[]{0.0, 0.0};
    }

    /**
     * Average kWh per hour-of-day, grouped by day of week.
     * Returns Map&lt;DAYOFWEEK (1=Sun…7=Sat), double[24]&gt;.
     * Optional {@code seasonMonths} narrows to those calendar months.
     */
    public Map<Integer, double[]> getDayOfWeekHourlyProfiles(long workspaceId, int[] seasonMonths) {
        StringBuilder sql = new StringBuilder("""
                SELECT dow, hr, AVG(hourly_kwh) AS avg_kwh
                FROM (
                    SELECT reading_date,
                           DAYOFWEEK(reading_date) AS dow,
                           FLOOR(start_minute / 60) AS hr,
                           SUM(kwh) AS hourly_kwh
                    FROM interval_record
                    WHERE workspace_id = ?
                """);
        if (seasonMonths != null && seasonMonths.length > 0) {
            sql.append("  AND MONTH(reading_date) IN (");
            for (int i = 0; i < seasonMonths.length; i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
            }
            sql.append(")\n");
        }
        sql.append("""
                    GROUP BY reading_date, FLOOR(start_minute / 60)
                ) sub
                GROUP BY dow, hr
                ORDER BY dow, hr
                """);

        Map<Integer, double[]> result = new TreeMap<>();
        for (int i = 1; i <= 7; i++) result.put(i, new double[24]);
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, workspaceId);
            if (seasonMonths != null) {
                for (int i = 0; i < seasonMonths.length; i++) ps.setInt(2 + i, seasonMonths[i]);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int dow = rs.getInt("dow");
                int hr  = rs.getInt("hr");
                if (dow >= 1 && dow <= 7 && hr >= 0 && hr < 24) {
                    result.get(dow)[hr] = rs.getDouble("avg_kwh");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load DOW hourly profiles", e);
        }
        return result;
    }

    /**
     * Total kWh split across TOU-relevant time windows (all days, all time).
     * Returns double[]{offPeakKwh, peakKwh} where peak = hours 15–18 (3 pm–7 pm).
     */
    public double[] getTimeBlockTotals(long workspaceId) {
        String sql = """
                SELECT
                    SUM(CASE WHEN FLOOR(start_minute / 60) BETWEEN 15 AND 18
                             THEN kwh ELSE 0 END) AS peak_kwh,
                    SUM(kwh) AS total_kwh
                FROM interval_record
                WHERE workspace_id = ?
                """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                double peak = rs.getDouble("peak_kwh");
                double total = rs.getDouble("total_kwh");
                return new double[]{total - peak, peak};
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load time block totals", e);
        }
        return new double[]{0.0, 0.0};
    }
}
