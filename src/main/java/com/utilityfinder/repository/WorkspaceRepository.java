package com.utilityfinder.repository;

import com.utilityfinder.model.Workspace;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WorkspaceRepository {

    public List<Workspace> findAll() {
        String sql = "SELECT id, name, created_at FROM workspace ORDER BY name";
        List<Workspace> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public Optional<Workspace> findById(long id) {
        String sql = "SELECT id, name, created_at FROM workspace WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Workspace save(Workspace workspace) {
        String sql = "INSERT INTO workspace (name, created_at) VALUES (?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, workspace.getName());
            ps.setDate(2, Date.valueOf(workspace.getCreatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) workspace.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return workspace;
    }

    public void update(Workspace workspace) {
        String sql = "UPDATE workspace SET name = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workspace.getName());
            ps.setLong(2, workspace.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM workspace WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Workspace map(ResultSet rs) throws SQLException {
        return new Workspace(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDate("created_at").toLocalDate());
    }
}
