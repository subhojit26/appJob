package com.JobHunt.appJob.store;

import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Test support: builds a temp-file SQLite {@link DataSource} and applies {@code schema.sql}.
 * Uses a file (not in-memory) so behavior matches production, including the UNIQUE constraint.
 */
public final class SqliteTestSupport {

    private SqliteTestSupport() {
    }

    public static DataSource dataSource(Path dbFile) {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        return ds;
    }

    public static void initSchema(DataSource dataSource) {
        String schema = stripComments(readSchema());
        try (Connection conn = dataSource.getConnection()) {
            for (String stmt : schema.split(";")) {
                String sql = stmt.trim();
                if (sql.isEmpty()) {
                    continue;
                }
                try (Statement st = conn.createStatement()) {
                    st.execute(sql);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to init schema", e);
        }
    }

    /** Removes full-line {@code --} comments before statement splitting. */
    private static String stripComments(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String readSchema() {
        try (InputStream in = SqliteTestSupport.class.getClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read schema.sql", e);
        }
    }
}


