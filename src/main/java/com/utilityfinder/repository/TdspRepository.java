package com.utilityfinder.repository;

import com.utilityfinder.model.Tdsp;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TdspRepository {

    public List<Tdsp> findAll() {
        String sql = "SELECT * FROM tdsp ORDER BY id";
        List<Tdsp> result = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load TDSPs", e);
        }
        return result;
    }

    public Optional<Tdsp> findById(long id) {
        String sql = "SELECT * FROM tdsp WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find TDSP " + id, e);
        }
    }

    public Optional<Tdsp> findByName(String name) {
        String sql = "SELECT * FROM tdsp WHERE name = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find TDSP by name", e);
        }
    }

    /** Matches the first 4 characters of the ESIID against each TDSP's esiid_prefix. */
    public Optional<Tdsp> findByEsiidPrefix(String prefix) {
        String sql = "SELECT * FROM tdsp WHERE esiid_prefix = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prefix);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find TDSP by ESIID prefix", e);
        }
    }

    /** Returns the TDSP linked to the workspace, or empty if none set. */
    public Optional<Tdsp> findForWorkspace(long workspaceId) {
        String sql = """
                SELECT t.* FROM tdsp t
                JOIN workspace w ON w.tdsp_id = t.id
                WHERE w.id = ?
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find TDSP for workspace", e);
        }
    }

    public void setForWorkspace(long workspaceId, Long tdspId) {
        String sql = "UPDATE workspace SET tdsp_id = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (tdspId == null) ps.setNull(1, Types.BIGINT);
            else                ps.setLong(1, tdspId);
            ps.setLong(2, workspaceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set TDSP for workspace", e);
        }
    }

    public void updateRates(long tdspId, double baseCharge, double perKwhCharge,
                            LocalDate effectiveDate, LocalDate lastVerified) {
        String sql = """
                UPDATE tdsp SET base_charge = ?, per_kwh_charge = ?,
                                effective_date = ?, last_verified = ?
                WHERE id = ?
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, baseCharge);
            ps.setDouble(2, perKwhCharge);
            ps.setObject(3, effectiveDate);
            ps.setObject(4, lastVerified);
            ps.setLong(5, tdspId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update TDSP rates", e);
        }
    }

    private Tdsp map(ResultSet rs) throws SQLException {
        Tdsp t = new Tdsp();
        t.setId(rs.getLong("id"));
        t.setName(rs.getString("name"));
        t.setEsiidPrefix(rs.getString("esiid_prefix"));
        t.setPdfFilename(rs.getString("pdf_filename"));
        t.setBaseCharge(rs.getDouble("base_charge"));
        t.setPerKwhCharge(rs.getDouble("per_kwh_charge"));
        t.setEffectiveDate(rs.getObject("effective_date", LocalDate.class));
        t.setLastVerified(rs.getObject("last_verified", LocalDate.class));
        return t;
    }
}
