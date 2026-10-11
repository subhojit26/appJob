package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.orchestration.SourceStats;

import java.util.List;

/**
 * Summary of one complete pipeline run.
 *
 * @param sourceStats  per-source statistics from the orchestrator
 * @param rawFetched   total raw jobs fetched (before dedup)
 * @param afterDedupe  jobs remaining after in-memory deduplication
 * @param eligible     jobs passing global filter
 * @param matched      jobs that matched at least one profile
 * @param newJobs      jobs actually inserted as pending notification
 * @param healthLine   human-readable health warning line, or {@code null} if all sources OK
 */
public record RunSummary(
        List<SourceStats> sourceStats,
        int rawFetched,
        int afterDedupe,
        int eligible,
        int matched,
        int newJobs,
        String healthLine
) {}

