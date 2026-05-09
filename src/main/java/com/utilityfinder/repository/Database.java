package com.utilityfinder.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class Database {

    private static final Path DATA_DIR =
            Path.of(System.getProperty("user.home"), ".utility-finder");

    private static final String JDBC_URL =
            "jdbc:h2:" + DATA_DIR.resolve("data").toAbsolutePath();

    public static void initialize() {
        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create data directory: " + DATA_DIR, e);
        }
        try (Connection conn = getConnection()) {
            for (String ddl : DDL_STATEMENTS) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(ddl);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, "sa", "");
    }

    private static final List<String> DDL_STATEMENTS = List.of(
        """
        CREATE TABLE IF NOT EXISTS workspace (
            id         BIGINT AUTO_INCREMENT PRIMARY KEY,
            name       VARCHAR(255) NOT NULL,
            created_at DATE         NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS usage_record (
            id           BIGINT AUTO_INCREMENT PRIMARY KEY,
            workspace_id BIGINT NOT NULL,
            record_year  INT    NOT NULL,
            record_month INT    NOT NULL CHECK (record_month BETWEEN 1 AND 12),
            kwh_used     DOUBLE NOT NULL,
            FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
            UNIQUE (workspace_id, record_year, record_month)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS rate_plan (
            id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
            workspace_id         BIGINT       NOT NULL,
            provider_name        VARCHAR(255) NOT NULL,
            plan_name            VARCHAR(255) NOT NULL,
            contract_term_months INT,
            base_charge          DOUBLE       NOT NULL DEFAULT 0.0,
            rate_per_kwh         DOUBLE       NOT NULL,
            notes                VARCHAR(1000),
            is_current           BOOLEAN      NOT NULL DEFAULT FALSE,
            FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS tier_discount (
            id            BIGINT AUTO_INCREMENT PRIMARY KEY,
            rate_plan_id  BIGINT NOT NULL,
            threshold_kwh DOUBLE NOT NULL,
            discount_amt  DOUBLE NOT NULL,
            sort_order    INT    NOT NULL DEFAULT 0,
            FOREIGN KEY (rate_plan_id) REFERENCES rate_plan(id) ON DELETE CASCADE
        )
        """
    );
}
