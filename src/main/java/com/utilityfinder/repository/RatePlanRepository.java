package com.utilityfinder.repository;

import com.utilityfinder.model.RatePlan;
import com.utilityfinder.model.TierDiscount;

import java.sql.*;
import java.util.*;

public class RatePlanRepository {

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<RatePlan> findByWorkspace(long workspaceId) {
        String planSql =
            "SELECT id, workspace_id, provider_name, plan_name, contract_term_months, " +
            "       base_charge, rate_per_kwh, notes, is_current " +
            "FROM rate_plan WHERE workspace_id = ? ORDER BY provider_name, plan_name";
        String discSql =
            "SELECT td.id, td.rate_plan_id, td.threshold_kwh, td.discount_amt, td.sort_order " +
            "FROM tier_discount td JOIN rate_plan rp ON td.rate_plan_id = rp.id " +
            "WHERE rp.workspace_id = ? ORDER BY td.rate_plan_id, td.sort_order";

        List<RatePlan> plans = new ArrayList<>();
        Map<Long, RatePlan> byId = new LinkedHashMap<>();

        try (Connection conn = Database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(planSql)) {
                ps.setLong(1, workspaceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        RatePlan p = mapPlan(rs);
                        plans.add(p);
                        byId.put(p.getId(), p);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(discSql)) {
                ps.setLong(1, workspaceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        RatePlan p = byId.get(rs.getLong("rate_plan_id"));
                        if (p != null) p.getDiscounts().add(mapDiscount(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return plans;
    }

    public Optional<RatePlan> findById(long id) {
        String planSql =
            "SELECT id, workspace_id, provider_name, plan_name, contract_term_months, " +
            "       base_charge, rate_per_kwh, notes, is_current FROM rate_plan WHERE id = ?";
        String discSql =
            "SELECT id, rate_plan_id, threshold_kwh, discount_amt, sort_order " +
            "FROM tier_discount WHERE rate_plan_id = ? ORDER BY sort_order";

        try (Connection conn = Database.getConnection()) {
            RatePlan plan;
            try (PreparedStatement ps = conn.prepareStatement(planSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    plan = mapPlan(rs);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(discSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) plan.getDiscounts().add(mapDiscount(rs));
                }
            }
            return Optional.of(plan);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public RatePlan save(RatePlan plan) {
        String sql =
            "INSERT INTO rate_plan (workspace_id, provider_name, plan_name, contract_term_months, " +
            "                       base_charge, rate_per_kwh, notes, is_current) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindPlan(ps, plan);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) plan.setId(keys.getLong(1));
            }
            insertDiscounts(conn, plan);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return plan;
    }

    public void update(RatePlan plan) {
        String sql =
            "UPDATE rate_plan SET provider_name = ?, plan_name = ?, contract_term_months = ?, " +
            "                     base_charge = ?, rate_per_kwh = ?, notes = ?, is_current = ? " +
            "WHERE id = ?";
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, plan.getProviderName());
                    ps.setString(2, plan.getPlanName());
                    setNullableInt(ps, 3, plan.getContractTermMonths());
                    ps.setDouble(4, plan.getBaseCharge());
                    ps.setDouble(5, plan.getRatePerKwh());
                    setNullableString(ps, 6, plan.getNotes());
                    ps.setBoolean(7, plan.isCurrent());
                    ps.setLong(8, plan.getId());
                    ps.executeUpdate();
                }
                deleteDiscounts(conn, plan.getId());
                insertDiscounts(conn, plan);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(long id) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM rate_plan WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Clears the is_current flag on every plan in the workspace. */
    public void clearCurrentFlags(long workspaceId) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE rate_plan SET is_current = FALSE WHERE workspace_id = ?")) {
            ps.setLong(1, workspaceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Atomically marks one plan as current and clears the flag on all others in the workspace. */
    public void markAsCurrent(long planId, long workspaceId) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE rate_plan SET is_current = FALSE WHERE workspace_id = ?")) {
                    ps.setLong(1, workspaceId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE rate_plan SET is_current = TRUE WHERE id = ?")) {
                    ps.setLong(1, planId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Replaces all discounts for the given plan (used externally when needed). */
    public void saveDiscounts(long planId, List<TierDiscount> discounts) {
        try (Connection conn = Database.getConnection()) {
            deleteDiscounts(conn, planId);
            RatePlan stub = new RatePlan();
            stub.setId(planId);
            stub.setDiscounts(discounts);
            insertDiscounts(conn, stub);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<TierDiscount> findDiscountsByPlan(long planId) {
        String sql = "SELECT id, rate_plan_id, threshold_kwh, discount_amt, sort_order " +
                     "FROM tier_discount WHERE rate_plan_id = ? ORDER BY sort_order";
        List<TierDiscount> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapDiscount(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void insertDiscounts(Connection conn, RatePlan plan) throws SQLException {
        if (plan.getDiscounts().isEmpty()) return;
        String sql = "INSERT INTO tier_discount (rate_plan_id, threshold_kwh, discount_amt, sort_order) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < plan.getDiscounts().size(); i++) {
                TierDiscount d = plan.getDiscounts().get(i);
                ps.setLong(1, plan.getId());
                ps.setDouble(2, d.getThresholdKwh());
                ps.setDouble(3, d.getDiscountAmt());
                ps.setInt(4, i);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteDiscounts(Connection conn, long planId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM tier_discount WHERE rate_plan_id = ?")) {
            ps.setLong(1, planId);
            ps.executeUpdate();
        }
    }

    private void bindPlan(PreparedStatement ps, RatePlan plan) throws SQLException {
        ps.setLong(1, plan.getWorkspaceId());
        ps.setString(2, plan.getProviderName());
        ps.setString(3, plan.getPlanName());
        setNullableInt(ps, 4, plan.getContractTermMonths());
        ps.setDouble(5, plan.getBaseCharge());
        ps.setDouble(6, plan.getRatePerKwh());
        setNullableString(ps, 7, plan.getNotes());
        ps.setBoolean(8, plan.isCurrent());
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.INTEGER);
        else ps.setInt(idx, value);
    }

    private void setNullableString(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null || value.isBlank()) ps.setNull(idx, Types.VARCHAR);
        else ps.setString(idx, value);
    }

    private RatePlan mapPlan(ResultSet rs) throws SQLException {
        RatePlan p = new RatePlan();
        p.setId(rs.getLong("id"));
        p.setWorkspaceId(rs.getLong("workspace_id"));
        p.setProviderName(rs.getString("provider_name"));
        p.setPlanName(rs.getString("plan_name"));
        int term = rs.getInt("contract_term_months");
        p.setContractTermMonths(rs.wasNull() ? null : term);
        p.setBaseCharge(rs.getDouble("base_charge"));
        p.setRatePerKwh(rs.getDouble("rate_per_kwh"));
        p.setNotes(rs.getString("notes"));
        p.setCurrent(rs.getBoolean("is_current"));
        return p;
    }

    private TierDiscount mapDiscount(ResultSet rs) throws SQLException {
        TierDiscount d = new TierDiscount();
        d.setId(rs.getLong("id"));
        d.setRatePlanId(rs.getLong("rate_plan_id"));
        d.setThresholdKwh(rs.getDouble("threshold_kwh"));
        d.setDiscountAmt(rs.getDouble("discount_amt"));
        d.setSortOrder(rs.getInt("sort_order"));
        return d;
    }
}
