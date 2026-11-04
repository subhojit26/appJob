package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.notify.DigestNotificationService;
import com.JobHunt.appJob.notify.HealthcheckPinger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled entry-point for JobWatch.
 *
 * <p>One cron-triggered method runs the complete pipeline end-to-end:</p>
 * <pre>
 *   JobWatchPipeline.run()
 *        ↓  fetch → dedupe → extract → filter → match → persist
 *   DigestNotificationService.sendPendingDigest()
 *        ↓  query pending → build → email → markNotified
 *   HealthcheckPinger.pingSuccess() / pingFail()
 * </pre>
 *
 * <p>The cron expression is configured via {@code jobwatch.schedule.cron} (default every 30 min).
 * Override with the {@code JOBWATCH_CRON} environment variable.</p>
 */
@Component
public class JobWatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobWatchScheduler.class);

    private final JobWatchPipeline pipeline;
    private final DigestNotificationService notificationService;
    private final HealthcheckPinger healthcheckPinger;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JobWatchScheduler(JobWatchPipeline pipeline,
                             DigestNotificationService notificationService,
                             HealthcheckPinger healthcheckPinger) {
        this.pipeline = pipeline;
        this.notificationService = notificationService;
        this.healthcheckPinger = healthcheckPinger;
    }

    /**
     * Main scheduled run. The cron expression defaults to every 30 minutes.
     *
     * <p>Fatal errors (pipeline throws) cause the healthcheck to be pinged as failed.
     * Source-level failures (one source down) are tolerated — they appear in the health
     * line included in the digest.</p>
     */
    @Scheduled(cron = "${jobwatch.schedule.cron:0 0 6 * * *}")
    public void runPipeline() {
        runPipelineInternal("scheduled");
    }

    /** Triggered by the dashboard UI to run fetch/match immediately. */
    public TriggerResult runPipelineNow() {
        return runPipelineInternal("manual");
    }

    public boolean isRunning() {
        return running.get();
    }

    private TriggerResult runPipelineInternal(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("Skipping {} pipeline run because another run is already in progress", trigger);
            return new TriggerResult(false, false, null, null);
        }

        log.info("=== JobWatch {} run starting ===", trigger);
        try {
            // 1. Fetch → dedupe → extract → filter → match → persist
            RunSummary summary = pipeline.run();

            log.info("Pipeline summary: rawFetched={} afterDedupe={} eligible={} matched={} new={}",
                    summary.rawFetched(), summary.afterDedupe(), summary.eligible(),
                    summary.matched(), summary.newJobs());

            // 2. Build and send digest for pending jobs (only after persist)
            boolean sent = notificationService.sendPendingDigest(summary.healthLine());

            if (!sent) {
                // Email failed: do NOT ping success — jobs remain pending for next run
                log.error("Digest send failed — skipping healthcheck ping");
                healthcheckPinger.pingFail();
                return new TriggerResult(true, false, summary, "Digest send failed");
            }

            // 3. All good — ping healthchecks.io
            healthcheckPinger.pingSuccess();
            log.info("=== JobWatch {} run complete ===", trigger);
            return new TriggerResult(true, true, summary, null);

        } catch (Exception e) {
            log.error("Pipeline run threw an unexpected exception", e);
            healthcheckPinger.pingFail();
            return new TriggerResult(true, false, null, e.getMessage());
        } finally {
            running.set(false);
        }
    }

    public record TriggerResult(boolean started, boolean success, RunSummary summary, String error) {
    }
}

