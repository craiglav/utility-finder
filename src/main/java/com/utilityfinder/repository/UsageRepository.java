package com.utilityfinder.repository;

import com.utilityfinder.model.UsageRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsageRepository {

    public List<UsageRecord> findByWorkspace(long workspaceId) {
        String sql = "SELECT id, workspace_id, record_year, record_month, kwh_used " +
                     "FROM usage_record WHERE workspace_id = ? ORDER BY record_year DESC, record_month ASC";
        List<UsageRecord> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public Optional<UsageRecord> findByWorkspaceYearMonth(long workspaceId, int record_year, int month) {
        String sql = "SELECT id, workspace_id, record_year, record_month, kwh_used " +
                     "FROM usage_record WHERE workspace_id = ? AND record_year = ? AND record_month = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            ps.setInt(2, record_year);
            ps.setInt(3, month);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public UsageRecord save(UsageRecord record) {
        String sql = "INSERT INTO usage_record (workspace_id, record_year, record_month, kwh_used) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, record.getWorkspaceId());
            ps.setInt(2, record.getYear());
            ps.setInt(3, record.getMonth());
            ps.setDouble(4, record.getKwhUsed());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) record.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return record;
    }

    public void update(UsageRecord record) {
        String sql = "UPDATE usage_record SET kwh_used = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, record.getKwhUsed());
            ps.setLong(2, record.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM usage_record WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private UsageRecord map(ResultSet rs) throws SQLException {
        return new UsageRecord(
                rs.getLong("id"),
                rs.getLong("workspace_id"),
                rs.getInt("record_year"),
                rs.getInt("record_month"),
                rs.getDouble("kwh_used"));
    }
}
