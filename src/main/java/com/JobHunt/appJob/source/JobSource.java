package com.JobHunt.appJob.source;

import com.JobHunt.appJob.model.Job;

import java.util.List;

/**
 * A provider of job postings (Greenhouse, Lever, Ashby, Adzuna, ...).
 *
 * <p>Each implementation owns its API-specific parsing logic and converts the
 * provider payload into a list of normalized {@link Job} records. The rest of the
 * application only ever sees {@code List<Job>} and never the raw provider JSON.</p>
 *
 * <p><strong>Failure isolation:</strong> a single source failing must never abort
 * the whole run. Implementations should surface fetch failures in a way the
 * orchestrator can catch and log per-source, while other sources keep working.</p>
 */
public interface JobSource {

    /**
     * Stable, lowercase logical name of this source, e.g. {@code "greenhouse"}.
     * Used for logging, dedupe keys, and the {@link Job#source()} field.
     */
    String name();

    /**
     * Fetches and normalizes all currently available jobs from this source.
     *
     * @return the normalized jobs; never {@code null}, may be empty
     * @throws Exception if fetching or parsing fails; the orchestrator handles this
     *                   per-source so other sources are unaffected
     */
    List<Job> fetchJobs() throws Exception;
}

