package com.JobHunt.appJob.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-shot execution mode.
 *
 * <p>When {@code jobwatch.run-now=true} (or env {@code JOBWATCH_RUN_NOW=true}) the application
 * runs the full pipeline + digest exactly once on startup and then exits. This supports:</p>
 * <ul>
 *   <li>A manual on-demand run / live end-to-end test, and</li>
 *   <li>The Phase 7 "one-shot jar via cron" deployment mode.</li>
 * </ul>
 *
 * <p>When the property is absent/false the app behaves normally (long-lived, {@link JobWatchScheduler}
 * fires on the configured cron).</p>
 */
@Component
@ConditionalOnProperty(name = "jobwatch.run-now", havingValue = "true")
@Order(1)
public class RunNowRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunNowRunner.class);

    private final JobWatchScheduler scheduler;
    private final ApplicationContext context;

    public RunNowRunner(JobWatchScheduler scheduler, ApplicationContext context) {
        this.scheduler = scheduler;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== run-now mode: executing a single pipeline run, then exiting ===");
        int exitCode = 0;
        try {
            scheduler.runPipeline();
        } catch (Exception e) {
            log.error("run-now pipeline execution failed", e);
            exitCode = 1;
        }
        final int finalExitCode = exitCode;
        int code = SpringApplication.exit(context, () -> finalExitCode);
        System.exit(code);
    }
}



