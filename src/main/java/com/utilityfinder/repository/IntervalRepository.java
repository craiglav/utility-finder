package com.utilityfinder.repository;

import com.utilityfinder.model.IntervalRecord;
import com.utilityfinder.model.MonthSummary;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class IntervalRepository {

    /**
     * Upserts a batch of interval records for the workspace inside a single
     * transaction.  If any batch fails the entire import is rolled back so the
     * DB is never left in a partial state.
     *
     * @return int[]{inserted, overwritten}
     */
    public int[] saveBatch(long workspaceId, List<IntervalRecord> records) {
        if (records.isEmpty()) return new int[]{0, 0};

        String sql = """
                MERGE INTO interval_record
                    (workspace_id, esiid, reading_date, start_minute, kwh, estimated, imported_at)
                KEY (workspace_id, reading_date, start_minute)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        int inserted = 0, overwritten = 0;
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int batchCount = 0;
                for (IntervalRecord r : records) {
                    ps.setLong(1, workspaceId);
                    ps.setString(2, r.getEsiid());
                    ps.setDate(3, Date.valueOf(r.getReadingDate()));
                    ps.setInt(4, r.getStartMinute());
                    ps.setDouble(5, r.getKwh());
                    ps.setBoolean(6, r.isEstimated());
                    ps.addBatch();

                    if (++batchCount % 2000 == 0) {
                        for (int c : ps.executeBatch()) {
                            if (c >= 1) inserted++; else overwritten++;
                        }
                    }
                }
                for (int c : ps.executeBatch()) {
                    if (c >= 1) inserted++; else overwritten++;
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException(
                        "Failed to save interval records: " + e.getMessage(), e);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to save interval records: " + e.getMessage(), e);
        }
        return new int[]{inserted, overwritten};
    }

    /** Monthly summary rows, sorted newest first. */
    public List<MonthSummary> getMonthlySummaries(long workspaceId) {
        String monthlySql = """
                SELECT YEAR(reading_date)  AS yr,
                       MONTH(reading_date) AS mo,
                       SUM(kwh)            AS total_kwh,
                       COUNT(DISTINCT reading_date) AS days_cnt
                FROM interval_record
                WHERE workspace_id = ?
                GROUP BY YEAR(reading_date), MONTH(reading_date)
                ORDER BY yr DESC, mo DESC
                """;

        String peakSql = """
                SELECT YEAR(reading_date)  AS yr,
                       MONTH(reading_date) AS mo,
                       reading_date,
                       SUM(kwh) AS day_kwh
                FROM interval_record
                WHERE workspace_id = ?
                GROUP BY reading_date
                ORDER BY day_kwh DESC
                """;

        // peak_date per (yr, mo) — first match wins since sorted by day_kwh DESC
        Map<String, LocalDate> peakDate = new LinkedHashMap<>();
        Map<String, Double>    peakKwh  = new LinkedHashMap<>();

        try (Connection conn = Database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(peakSql)) {
                ps.setLong(1, workspaceId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String key = rs.getInt("yr") + "-" + rs.getInt("mo");
                    if (!peakDate.containsKey(key)) {
                        peakDate.put(key, rs.getDate("reading_date").toLocalDate());
                        peakKwh.put(key, rs.getDouble("day_kwh"));
                    }
                }
            }

            List<MonthSummary> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(monthlySql)) {
                ps.setLong(1, workspaceId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int yr   = rs.getInt("yr");
                    int mo   = rs.getInt("mo");
                    double total = rs.getDouble("total_kwh");
                    int days = rs.getInt("days_cnt");
                    String key = yr + "-" + mo;
                    result.add(new MonthSummary(
                            yr, mo, total, days,
                            days > 0 ? total / days : 0.0,
                            peakDate.getOrDefault(key, null),
                            peakKwh.getOrDefault(key, 0.0)));
                }
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load monthly summaries", e);
        }
    }

    /**
     * Returns a 12-element array (index 0 = Jan … 11 = Dec) of average monthly
     * kWh across all years of data.  {@code Double.NaN} where no data exists.
     */
    public double[] getAveragedMonthlyProfile(long workspaceId) {
        String sql = """
                SELECT mo, AVG(monthly_kwh) AS avg_kwh
                FROM (
                    SELECT YEAR(reading_date)  AS yr,
                           MONTH(reading_date) AS mo,
                           SUM(kwh)            AS monthly_kwh
                    FROM interval_record
                    WHERE workspace_id = ?
                    GROUP BY YEAR(reading_date), MONTH(reading_date)
                ) sub
                GROUP BY mo
                ORDER BY mo
                """;

        double[] profile = new double[12];
        Arrays.fill(profile, Double.NaN);

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                profile[rs.getInt("mo") - 1] = rs.getDouble("avg_kwh");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute monthly profile", e);
        }
        return profile;
    }

    public List<Integer> getDistinctYears(long workspaceId) {
        String sql = """
                SELECT DISTINCT YEAR(reading_date) AS yr
                FROM interval_record
                WHERE workspace_id = ?
                ORDER BY yr ASC
                """;
        List<Integer> years = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) years.add(rs.getInt("yr"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load distinct years", e);
        }
        return years;
    }

    public boolean hasData(long workspaceId) {
        String sql = "SELECT COUNT(*) FROM interval_record WHERE workspace_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getLong(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check interval data", e);
        }
    }

    public void deleteByWorkspace(long workspaceId) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM interval_record WHERE workspace_id = ?")) {
            ps.setLong(1, workspaceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete interval data", e);
        }
    }

    public Optional<String> getEsiid(long workspaceId) {
        String sql = "SELECT esiid FROM interval_record WHERE workspace_id = ? LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.ofNullable(rs.getString("esiid")) : Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get ESIID", e);
        }
    }

    public Optional<LocalDate> getMinDate(long workspaceId) {
        return queryDate(workspaceId, "MIN(reading_date)");
    }

    public Optional<LocalDate> getMaxDate(long workspaceId) {
        return queryDate(workspaceId, "MAX(reading_date)");
    }

    private Optional<LocalDate> queryDate(long workspaceId, String expr) {
        String sql = "SELECT " + expr + " AS d FROM interval_record WHERE workspace_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Date d = rs.getDate("d");
                return d != null ? Optional.of(d.toLocalDate()) : Optional.empty();
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query date", e);
        }
    }

    public long getTotalCount(long workspaceId) {
        String sql = "SELECT COUNT(*) FROM interval_record WHERE workspace_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count interval records", e);
        }
    }

    public Optional<LocalDateTime> getLastImportTime(long workspaceId) {
        String sql = "SELECT MAX(imported_at) AS t FROM interval_record WHERE workspace_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp("t");
                return ts != null ? Optional.of(ts.toLocalDateTime()) : Optional.empty();
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get last import time", e);
        }
    }
}
