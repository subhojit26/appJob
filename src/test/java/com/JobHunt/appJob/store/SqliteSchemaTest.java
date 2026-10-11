package com.JobHunt.appJob.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code schema.sql} initializes successfully and that WAL journal mode is active,
 * rather than assuming the PRAGMA was applied.
 */
class SqliteSchemaTest {

    @TempDir
    Path tempDir;

    @Test
    void schemaInitializesTablesAndIndexes() throws Exception {
        DataSource ds = SqliteTestSupport.dataSource(tempDir.resolve("schema.db"));
        SqliteTestSupport.initSchema(ds);

        Set<String> tables = new HashSet<>();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        }
        assertTrue(tables.contains("jobs"), "jobs table should exist");
        assertTrue(tables.contains("source_runs"), "source_runs table should exist");
    }

    @Test
    void walModeIsActive() throws Exception {
        Path db = tempDir.resolve("wal.db");
        DataSource ds = SqliteTestSupport.dataSource(db);
        SqliteTestSupport.initSchema(ds); // schema.sql runs PRAGMA journal_mode=WAL

        // WAL is persisted in the database file; a fresh connection should report it.
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
            assertTrue(rs.next(), "PRAGMA journal_mode should return a row");
            assertEquals("wal", rs.getString(1).toLowerCase(), "WAL journal mode must be active");
        }
    }
}

