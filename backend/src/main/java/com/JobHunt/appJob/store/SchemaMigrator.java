package com.JobHunt.appJob.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * Idempotent, additive SQLite migrations for columns introduced after the initial schema
 * (SQLite's {@code CREATE TABLE IF NOT EXISTS} cannot add columns to an existing table).
 *
 * <p>Each {@code ALTER TABLE ... ADD COLUMN} is attempted and the "duplicate column" error is
 * swallowed, so this is safe to run on every startup against both fresh and existing databases.</p>
 */
@Component
public class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    private static final List<String> MIGRATIONS = List.of(
            "ALTER TABLE jobs ADD COLUMN stack TEXT",
            "ALTER TABLE jobs ADD COLUMN profile TEXT",
            "ALTER TABLE jobs ADD COLUMN score INTEGER",
            "ALTER TABLE jobs ADD COLUMN applied_at TEXT",
            // v1.3 FR-39a: single active profile that gates which jobs get emailed.
            "ALTER TABLE profiles ADD COLUMN is_active INTEGER NOT NULL DEFAULT 0"
    );

    private final JdbcClient jdbc;

    public SchemaMigrator(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @EventListener(ApplicationStartedEvent.class)
    public void migrate() {
        for (String sql : MIGRATIONS) {
            try {
                jdbc.sql(sql).update();
                log.info("Applied migration: {}", sql);
            } catch (Exception e) {
                // Expected when the column already exists ("duplicate column name").
                log.debug("Migration skipped (already applied): {}", sql);
            }
        }
    }
}

