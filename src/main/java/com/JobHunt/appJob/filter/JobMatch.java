package com.JobHunt.appJob.filter;

import com.JobHunt.appJob.model.Job;

import java.util.List;

/**
 * Result of matching a {@link Job} against a {@link Profile}.
 *
 * <p>Kept separate from {@link Job} so the normalized source representation never carries
 * scoring/relevance metadata.</p>
 *
 * @param job         the matched job
 * @param score       rule-based relevance score (higher = more relevant)
 * @param matchedOn   the actual keywords that matched, explaining <em>why</em> (e.g. ["java","kafka"])
 * @param profileName the profile that produced this match
 */
public record JobMatch(
        Job job,
        int score,
        List<String> matchedOn,
        String profileName
) {
    public JobMatch {
        matchedOn = matchedOn == null ? List.of() : List.copyOf(matchedOn);
    }
}

