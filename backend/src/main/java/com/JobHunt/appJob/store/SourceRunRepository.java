package com.JobHunt.appJob.store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

/**
 * JDBC repository for the {@code source_runs} table.
 *
 * <p>{@link #sourceHasRuns(String)} answers "has this source ever run?" — the gate used by
 * bootstrap first-run seeding (kept out of this repository).</p>
 */
@Repository
public class SourceRunRepository {

    private final JdbcClient jdbc;

    public SourceRunRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Persists a run row. */
    public void save(SourceRun run) {
        jdbc.sql("""
                INSERT INTO source_runs (source, started_at, completed_at, success, jobs_fetched, error)
                VALUES (:source, :startedAt, :completedAt, :success, :jobsFetched, :error)
                """)
                .param("source", run.source())
                .param("startedAt", run.startedAt() == null ? null : run.startedAt().toString())
                .param("completedAt", run.completedAt() == null ? null : run.completedAt().toString())
                .param("success", run.success() ? 1 : 0)
                .param("jobsFetched", run.jobsFetched())
                .param("error", run.error())
                .update();
    }

    /**
     * Whether a source has ever run (any row), regardless of success — the seeding gate.
     *
     * @return {@code true} if at least one run exists for the source
     */
    public boolean sourceHasRuns(String source) {
        Integer exists = jdbc.sql(
                "SELECT EXISTS(SELECT 1 FROM source_runs WHERE source = :source)")
                .param("source", source)
                .query(Integer.class)
                .single();
        return exists != null && exists != 0;
    }
}
