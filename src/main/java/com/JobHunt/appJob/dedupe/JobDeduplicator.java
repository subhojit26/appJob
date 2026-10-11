package com.JobHunt.appJob.dedupe;

import com.JobHunt.appJob.model.Job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, two-pass job deduplicator. No Spring, HTTP, or database dependencies.
 *
 * <pre>
 *   jobs
 *     ├── 1. exact dedupe     (source + sourceJobId)
 *     └── 2. cross-source     (normalize(company) | normalize(title) | normalize(city))
 *   -> deduplicated jobs (first occurrence kept, input order preserved)
 * </pre>
 *
 * <h2>Pass 1 — exact</h2>
 * A posting is uniquely identified within a source by {@code (source, sourceJobId)}. Jobs with
 * a missing/blank {@code sourceJobId} are never collapsed by this pass (they cannot be proven
 * identical), so unrelated jobs are not accidentally merged.
 *
 * <h2>Pass 2 — cross-source</h2>
 * The same opening posted on different boards is detected via a conservative normalized key:
 * {@code normalize(company) | normalize(title) | normalize(city)}. If either company or title
 * is missing/blank, no cross-source key is formed (avoiding an unsafe "universal" key that
 * would collapse unrelated jobs). Deliberately no fuzzy matching yet — false positives are far
 * harder to recover from than missed duplicates.
 *
 * <h2>Merge policy</h2>
 * When two sources yield the same normalized opening, the <strong>first</strong> job is kept
 * and later ones are dropped. This is intentionally simple for now; a field-aware merge (e.g.
 * keeping Adzuna's salary alongside Greenhouse's apply URL) can replace this later.
 */
public class JobDeduplicator {

    /**
     * Removes exact and cross-source duplicates, keeping the first occurrence of each.
     *
     * @param jobs input jobs (may be empty/null)
     * @return a new list with duplicates removed, in the original order
     */
    public List<Job> deduplicate(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return List.of();
        }
        Set<String> seenExact = new HashSet<>();
        Set<String> seenCross = new HashSet<>();
        List<Job> result = new ArrayList<>(jobs.size());

        for (Job job : jobs) {
            if (job == null) {
                continue;
            }
            // Pass 1: exact (source, sourceJobId). Skip if we've seen this exact posting.
            String exactKey = exactKey(job);
            if (exactKey != null && !seenExact.add(exactKey)) {
                continue;
            }
            // Pass 2: cross-source normalized opening.
            String crossKey = crossKey(job);
            if (crossKey != null && !seenCross.add(crossKey)) {
                continue;
            }
            result.add(job);
        }
        return result;
    }

    /** {@code source \u0000 sourceJobId}, or {@code null} when the id is missing/blank. */
    private static String exactKey(Job job) {
        String id = trimToNull(job.sourceJobId());
        if (id == null) {
            return null;
        }
        String source = job.source() == null ? "" : job.source().toLowerCase();
        return source + "\u0000" + id;
    }

    /**
     * {@code normalize(company) | normalize(title) | city}, or {@code null} when company or
     * title is missing (so unrelated jobs are never collapsed by an empty key).
     */
    private static String crossKey(Job job) {
        String company = normalize(job.company());
        String title = normalize(job.title());
        if (company == null || title == null) {
            return null;
        }
        return company + "|" + title + "|" + cityKey(job);
    }

    /**
     * Extracts a normalized city from the job. Remote roles collapse to {@code "remote"};
     * otherwise the first comma-separated segment of the location is used. Returns an empty
     * string when no location is present (company + title still form a valid key).
     */
    private static String cityKey(Job job) {
        if (job.remote()) {
            return "remote";
        }
        String location = job.location();
        if (location == null || location.isBlank()) {
            return "";
        }
        if (location.toLowerCase().contains("remote")) {
            return "remote";
        }
        String first = location.split(",", 2)[0];
        String city = normalize(first);
        return city == null ? "" : city;
    }

    /** Lowercase, trim, and collapse internal whitespace; returns {@code null} if blank. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

