package com.JobHunt.appJob.filter;

/**
 * Table-driven scoring weights so relevance points live in data, not condition-heavy code.
 *
 * @param titleInclude       points per matched title keyword
 * @param descriptionInclude points per matched description (stack/skill) keyword
 * @param remoteBonus        bonus points when the job is remote (only if it already matched)
 */
public record ScoringWeights(
        int titleInclude,
        int descriptionInclude,
        int remoteBonus
) {
    /** Defaults mirroring the design table: title +5, description +2, remote +1. */
    public static ScoringWeights defaults() {
        return new ScoringWeights(5, 2, 1);
    }
}

