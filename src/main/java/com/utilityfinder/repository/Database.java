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
        "ALTER TABLE rate_plan ADD COLUMN IF NOT EXISTS renewable_percent DOUBLE",
        "ALTER TABLE rate_plan ADD COLUMN IF NOT EXISTS termination_fee_flat DOUBLE",
        "ALTER TABLE rate_plan ADD COLUMN IF NOT EXISTS termination_fee_per_month DOUBLE",
        "ALTER TABLE rate_plan ADD COLUMN IF NOT EXISTS contract_end_date DATE",
        """
        CREATE TABLE IF NOT EXISTS tier_discount (
            id            BIGINT AUTO_INCREMENT PRIMARY KEY,
            rate_plan_id  BIGINT NOT NULL,
            threshold_kwh DOUBLE NOT NULL,
            discount_amt  DOUBLE NOT NULL,
            sort_order    INT    NOT NULL DEFAULT 0,
            FOREIGN KEY (rate_plan_id) REFERENCES rate_plan(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS tou_window (
            id           BIGINT   AUTO_INCREMENT PRIMARY KEY,
            rate_plan_id BIGINT   NOT NULL,
            start_hour   TINYINT  NOT NULL CHECK (start_hour BETWEEN 0 AND 23),
            end_hour     TINYINT  NOT NULL CHECK (end_hour BETWEEN 0 AND 23),
            rate_per_kwh DOUBLE   NOT NULL,
            sort_order   INT      NOT NULL DEFAULT 0,
            FOREIGN KEY (rate_plan_id) REFERENCES rate_plan(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS interval_record (
            id           BIGINT    AUTO_INCREMENT PRIMARY KEY,
            workspace_id BIGINT    NOT NULL,
            esiid        VARCHAR(50),
            reading_date DATE      NOT NULL,
            start_minute SMALLINT  NOT NULL,
            kwh          DOUBLE    NOT NULL,
            estimated    BOOLEAN   NOT NULL DEFAULT FALSE,
            imported_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
            UNIQUE (workspace_id, reading_date, start_minute)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS tdsp (
            id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
            name           VARCHAR(100) NOT NULL,
            esiid_prefix   VARCHAR(10),
            pdf_filename   VARCHAR(100),
            base_charge    DOUBLE       NOT NULL DEFAULT 0.0,
            per_kwh_charge DOUBLE       NOT NULL DEFAULT 0.0,
            effective_date DATE,
            last_verified  DATE
        )
        """,
        // Seed the ERCOT TDSPs on first run only.  Uses WHEN NOT MATCHED so existing rows
        // (with user-updated rates) are never overwritten on subsequent startups.
        // Rates sourced from PUCT TDR reports (March 2026 cycle) — verify via Check for Updates.
        """
        MERGE INTO tdsp AS t
        USING (
          SELECT 1 AS id, 'CenterPoint'          AS name, '1008' AS esiid_prefix, 'CenterPoint_Rate_Report.pdf' AS pdf_filename, 6.49  AS base_charge, 0.04616 AS per_kwh_charge, DATE '2026-03-01' AS effective_date, DATE '2026-03-01' AS last_verified
          UNION ALL
          SELECT 2,       'Oncor',                      '1007', 'Oncor_Rate_Report.pdf',        3.42,  0.03971, DATE '2026-03-01', DATE '2026-03-01'
          UNION ALL
          SELECT 3,       'AEP Texas Central',           '1004', 'AEP_Rate_Report.pdf',          7.85,  0.04489, DATE '2026-03-01', DATE '2026-03-01'
          UNION ALL
          SELECT 4,       'AEP Texas North',             '1003', 'AEP_Rate_Report.pdf',          7.85,  0.04489, DATE '2026-03-01', DATE '2026-03-01'
          UNION ALL
          SELECT 5,       'TNMP',                        '1044', 'TNMP_Rate_Report.pdf',         7.85,  0.06218, DATE '2026-03-01', DATE '2026-03-01'
          UNION ALL
          SELECT 6,       'Lubbock Power & Light',       '1022', NULL,                           5.50,  0.03500, DATE '2026-03-01', DATE '2026-03-01'
        ) AS s ON t.id = s.id
        WHEN NOT MATCHED THEN INSERT (id, name, esiid_prefix, pdf_filename, base_charge, per_kwh_charge, effective_date, last_verified)
          VALUES (s.id, s.name, s.esiid_prefix, s.pdf_filename, s.base_charge, s.per_kwh_charge, s.effective_date, s.last_verified)
        """,
        "ALTER TABLE workspace ADD COLUMN IF NOT EXISTS tdsp_id BIGINT"
    );
}
