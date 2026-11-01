package com.JobHunt.appJob.store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * JDBC repository for the {@code companies} table — the dynamically discovered ATS roster
 * (Phase 6 / FR-1a/FR-1b). Replaces the hardcoded {@code jobwatch.sources.*.companies} lists.
 */
@Repository
public class CompanyRepository {

    private final JdbcClient jdbc;

    public CompanyRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /**
     * Inserts a discovered board, or (if it already exists) re-enables it and refreshes
     * {@code last_ok_at}, clearing the 404 counter. Idempotent.
     */
    public void upsertDiscovered(String source, String slug, String displayName, Instant when) {
        String ts = when.toString();
        jdbc.sql("""
                INSERT INTO companies (source, slug, display_name, discovered_at, last_ok_at,
                                       consecutive_404, enabled)
                VALUES (:source, :slug, :displayName, :when, :when, 0, 1)
                ON CONFLICT(source, slug) DO UPDATE SET
                    last_ok_at = :when,
                    consecutive_404 = 0,
                    enabled = 1,
                    display_name = COALESCE(excluded.display_name, companies.display_name)
                """)
                .param("source", source)
                .param("slug", slug)
                .param("displayName", displayName)
                .param("when", ts)
                .update();
    }

    /** Slugs currently enabled for the given ATS source. */
    public List<String> findEnabledSlugs(String source) {
        return jdbc.sql("SELECT slug FROM companies WHERE source = :source AND enabled = 1 ORDER BY slug")
                .param("source", source)
                .query(String.class)
                .list();
    }

    /** All rows (mainly for diagnostics/tests). */
    public List<Company> findAll() {
        return jdbc.sql("SELECT * FROM companies ORDER BY source, slug")
                .query(CompanyRepository::mapRow)
                .list();
    }

    /** True once at least one board has been discovered (used to trigger first-run discovery). */
    public boolean isEmpty() {
        Integer n = jdbc.sql("SELECT COUNT(*) FROM companies")
                .query(Integer.class)
                .single();
        return n == null || n == 0;
    }

    /**
     * Records that a board returned no jobs / 404'd. Increments the counter and, once it reaches
     * {@code pruneThreshold}, disables the board (likely changed ATS).
     */
    public void recordMiss(String source, String slug, int pruneThreshold) {
        jdbc.sql("""
                UPDATE companies
                   SET consecutive_404 = consecutive_404 + 1,
                       enabled = CASE WHEN consecutive_404 + 1 >= :threshold THEN 0 ELSE enabled END
                 WHERE source = :source AND slug = :slug
                """)
                .param("threshold", pruneThreshold)
                .param("source", source)
                .param("slug", slug)
                .update();
    }

    private static Company mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Company(
                rs.getLong("id"),
                rs.getString("source"),
                rs.getString("slug"),
                rs.getString("display_name"),
                parse(rs.getString("discovered_at")),
                parse(rs.getString("last_ok_at")),
                rs.getInt("consecutive_404"),
                rs.getInt("enabled") == 1
        );
    }

    private static Instant parse(String s) {
        return (s == null || s.isBlank()) ? null : Instant.parse(s);
    }
}

