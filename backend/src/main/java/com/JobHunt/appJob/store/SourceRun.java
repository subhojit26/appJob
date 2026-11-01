package com.JobHunt.appJob.store;

import com.JobHunt.appJob.orchestration.SourceStats;

import java.time.Instant;

/**
 * A row in the {@code source_runs} table: per-source run statistics.
 *
 * @param id          primary key (null before insert)
 * @param source      source name
 * @param startedAt   when the run started
 * @param completedAt when the run finished (nullable)
 * @param success     whether the source completed successfully
 * @param jobsFetched number of jobs fetched (nullable)
 * @param error       error text on failure (nullable)
 */
public record SourceRun(
        Long id,
        String source,
        Instant startedAt,
        Instant completedAt,
        boolean success,
        Integer jobsFetched,
        String error
) {
    /** Convenience for a successful run. */
    public static SourceRun ok(String source, Instant startedAt, Instant completedAt, int jobsFetched) {
        return new SourceRun(null, source, startedAt, completedAt, true, jobsFetched, null);
    }

    /** Convenience for a failed run. */
    public static SourceRun failed(String source, Instant startedAt, Instant completedAt, String error) {
        return new SourceRun(null, source, startedAt, completedAt, false, null, error);
    }

    /** Maps a {@link SourceStats} (from RunOrchestrator) to a persistable run row. */
    public static SourceRun fromStats(SourceStats stats, Instant startedAt, Instant completedAt) {
        String error = stats.error() == null ? null : stats.error().toString();
        return new SourceRun(null, stats.sourceName(), startedAt, completedAt,
                stats.success(), stats.success() ? stats.jobsFetched() : null, error);
    }
}
