package com.JobHunt.appJob.orchestration;

/**
 * Per-source outcome of a single run.
 *
 * <p>Captures just enough observability without mixing in logging/monitoring: which source
 * ran, how many jobs it produced, whether it succeeded, and the failure (if any).</p>
 *
 * @param sourceName  the source's {@code name()}
 * @param jobsFetched number of jobs returned (0 on failure or empty success)
 * @param success     whether the source completed without throwing
 * @param error       the failure, or {@code null} on success
 */
public record SourceStats(
        String sourceName,
        int jobsFetched,
        boolean success,
        Throwable error
) {
    /** Creates a successful stat with the given job count. */
    public static SourceStats success(String sourceName, int jobsFetched) {
        return new SourceStats(sourceName, jobsFetched, true, null);
    }

    /** Creates a failed stat (0 jobs) carrying the error. */
    public static SourceStats failure(String sourceName, Throwable error) {
        return new SourceStats(sourceName, 0, false, error);
    }
}

