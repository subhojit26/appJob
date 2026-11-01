package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.store.JobRepository;
import com.JobHunt.appJob.store.SourceRunRepository;
import com.JobHunt.appJob.store.StoredJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Handles first-run seeding for a source (PRD 7.6).
 *
 * <p>On the very first successful fetch of a source, inserting every job unnotified would send
 * hundreds of old postings in one email. This service applies the seeding rule:
 * <ul>
 *   <li>Jobs whose real {@code postedAt} is within the last 48 hours are left un-notified
 *       (they are genuinely new and should be emailed).</li>
 *   <li>Everything else is inserted with {@code notified_at = now}, so it is never emailed
 *       but still blocks re-processing.</li>
 * </ul>
 * </p>
 *
 * <p>The gate is {@link SourceRunRepository#sourceHasRuns(String)} — if any previous run exists
 * for the source, seeding is skipped and the caller uses normal insert logic.</p>
 */
@Service
public class FirstRunSeeder {

    private static final Logger log = LoggerFactory.getLogger(FirstRunSeeder.class);
    private static final Duration RECENT_THRESHOLD = Duration.ofHours(48);

    private final SourceRunRepository sourceRunRepository;
    private final JobRepository jobRepository;

    public FirstRunSeeder(SourceRunRepository sourceRunRepository, JobRepository jobRepository) {
        this.sourceRunRepository = sourceRunRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * Returns {@code true} if this is the first run for the given source (no prior
     * {@code source_runs} rows exist).
     */
    public boolean isFirstRun(String source) {
        return !sourceRunRepository.sourceHasRuns(source);
    }

    /**
     * Seeds a batch of jobs for a source that has never run before.
     *
     * <p>Recent jobs (postedAt within 48h) are inserted un-notified so they reach the digest.
     * Older/unknown jobs are inserted already notified so they are silently skipped.</p>
     *
     * @param source the source name, e.g. "greenhouse"
     * @param jobs   all jobs fetched from the source on this first run
     * @param now    the reference time (injected for testability)
     * @return number of jobs inserted as "new" (eligible for notification)
     */
    public int seed(String source, List<Job> jobs, Instant now) {
        if (jobs == null || jobs.isEmpty()) {
            return 0;
        }

        log.info("[{}] First run — seeding {} jobs (recent ones will still be emailed)", source, jobs.size());

        int newCount = 0;
        for (Job job : jobs) {
            boolean recent = isRecent(job, now);
            // notifiedAt = null  → eligible for digest
            // notifiedAt = now   → silently suppressed (already "seen")
            Instant notifiedAt = recent ? null : now;
            StoredJob stored = StoredJob.fromJob(job).withNotifiedAt(notifiedAt);
            boolean inserted = jobRepository.insertIfNew(stored);
            if (inserted && recent) {
                newCount++;
            }
        }

        log.info("[{}] Seeding complete: {} recent (will be emailed), {} suppressed",
                source, newCount, jobs.size() - newCount);
        return newCount;
    }

    private static boolean isRecent(Job job, Instant now) {
        // On first run, only jobs with a real known postedAt within 48h are emailed.
        // Unknown dates (postedAt == null) are suppressed — don't guess their age.
        if (job.postedAt() == null) {
            return false;
        }
        return Duration.between(job.postedAt(), now).compareTo(RECENT_THRESHOLD) <= 0;
    }
}

