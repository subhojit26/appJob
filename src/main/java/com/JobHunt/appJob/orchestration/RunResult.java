package com.JobHunt.appJob.orchestration;

import com.JobHunt.appJob.model.Job;

import java.util.List;

/**
 * Combined result of one orchestration run.
 *
 * @param jobs        all jobs from every successful source, combined (order not guaranteed)
 * @param sourceStats one entry per source invocation, in the same order the sources were given;
 *                    duplicate source names each keep their own entry (never merged/overwritten)
 */
public record RunResult(
        List<Job> jobs,
        List<SourceStats> sourceStats
) {
    public RunResult {
        jobs = List.copyOf(jobs);
        sourceStats = List.copyOf(sourceStats);
    }

    /** Total number of jobs collected across all sources. */
    public int totalJobs() {
        return jobs.size();
    }

    /** Number of sources that completed successfully. */
    public long successfulSources() {
        return sourceStats.stream().filter(SourceStats::success).count();
    }

    /** Number of sources that failed. */
    public long failedSources() {
        return sourceStats.stream().filter(s -> !s.success()).count();
    }
}

