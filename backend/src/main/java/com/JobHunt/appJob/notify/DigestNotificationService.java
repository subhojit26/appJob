package com.JobHunt.appJob.notify;

import com.JobHunt.appJob.filter.JobMatch;
import com.JobHunt.appJob.store.JobRepository;
import com.JobHunt.appJob.store.ProfileRepository;
import com.JobHunt.appJob.store.StoredJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the digest email pipeline: fetch pending jobs, build digest, send email, mark notified.
 *
 * <p>This is the final stage of the job-watch pipeline (PRD 6 and Phase 5). It:
 * <ol>
 *   <li>Queries pending jobs from the database</li>
 *   <li>Converts them to JobMatch objects (with default score/profile for now)</li>
 *   <li>Builds a digest via DigestBuilder</li>
 *   <li>Sends via EmailSender</li>
 *   <li>Only on success, marks jobs notified via JobRepository</li>
 * </ol>
 * </p>
 *
 * <p><strong>Note:</strong> Score and profile metadata are currently not persisted to the DB.
 * A future enhancement would store these alongside the job so they can be displayed in the digest.
 * For now, all pending jobs are assumed to have the same default score (0) and no profile.</p>
 */
@Service
public class DigestNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DigestNotificationService.class);
    private static final int DIGEST_LIMIT = 50;

    private final JobRepository jobRepository;
    private final DigestBuilder digestBuilder;
    private final EmailSender emailSender;
    private final ProfileRepository profileRepository;

    public DigestNotificationService(JobRepository jobRepository,
                                    DigestBuilder digestBuilder,
                                    EmailSender emailSender,
                                    ProfileRepository profileRepository) {
        this.jobRepository = jobRepository;
        this.digestBuilder = digestBuilder;
        this.emailSender = emailSender;
        this.profileRepository = profileRepository;
    }

    /**
     * Sends a digest email for all pending matched jobs (up to the 50-job cap).
     *
     * <p>Atomicity: jobs are only marked notified if SMTP succeeds. If the send fails,
     * they remain pending and will be retried on the next run.</p>
     *
     * @param sourceHealthLine optional health status line to include in the digest
     * @return true if digest was sent (or no jobs pending), false if send failed
     */
    public boolean sendPendingDigest(String sourceHealthLine) {
        // FR-39a: if the user activated one or more profiles, only email jobs for those profiles.
        List<String> activeProfiles = profileRepository.activeProfileNames();
        // Fetch pending jobs (up to 50), gated by the active profile(s) when any are set.
        List<StoredJob> pending = jobRepository.findPendingNotifications(DIGEST_LIMIT, activeProfiles);
        if (pending.isEmpty()) {
            log.info("No pending jobs to notify (active profiles: {})",
                    activeProfiles.isEmpty() ? "all" : activeProfiles);
            return true;
        }

        log.info("Building digest for {} pending jobs", pending.size());

        // Convert to JobMatch objects. For now, all have default score=0 and no profile.
        // A future version would read score/profile from the DB.
        List<JobMatch> matches = pending.stream()
                .map(stored -> new JobMatch(
                        stored.toJob(),
                        0,  // default score
                        List.of(),  // no matched_on for now
                        "all"  // placeholder profile
                ))
                .collect(Collectors.toList());

        // Build digest
        DigestEmail digest = digestBuilder.build(matches, sourceHealthLine);
        if (digest == null) {
            log.info("No jobs qualified for digest");
            return true;
        }

        // Send email
        boolean sent = emailSender.send(digest);
        if (!sent) {
            log.error("Failed to send digest email; jobs remain pending");
            return false;
        }

        // Mark as notified only after successful send
        List<Long> jobIds = pending.stream()
                .map(StoredJob::id)
                .collect(Collectors.toList());

        if (!jobIds.isEmpty()) {
            jobRepository.markNotified(jobIds, Instant.now());
            log.info("Marked {} jobs as notified", jobIds.size());
        }

        return true;
    }
}


