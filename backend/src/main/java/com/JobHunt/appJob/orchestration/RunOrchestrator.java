package com.JobHunt.appJob.orchestration;

import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.source.JobSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Runs a set of {@link JobSource}s concurrently and combines their results.
 *
 * <p>Concurrency boundary:</p>
 * <pre>
 *   RunOrchestrator
 *        ├── virtual thread → source.fetchJobs()
 *        ├── virtual thread → source.fetchJobs()
 *        └── ...
 *                               │
 *                               ▼
 *                        HttpJobFetcher (owns per-host Semaphore)
 * </pre>
 *
 * <p>The orchestrator controls <em>source-level</em> concurrency only; per-host HTTP
 * concurrency and retries remain the responsibility of {@code HttpJobFetcher}. This class
 * deliberately does <strong>not</strong> do dedupe, filtering, persistence, scheduling, or
 * retries.</p>
 *
 * <p><strong>Failure isolation:</strong> a source that throws is recorded as a failed
 * {@link SourceStats} and contributes no jobs; it never cancels or prevents the others, and
 * the run itself completes normally.</p>
 */
public class RunOrchestrator {

    /**
     * Maximum number of sources fetched concurrently. Virtual threads are cheap, but each
     * in-flight fetch buffers its HTTP/JSON response in memory. On small (≈1GB) containers an
     * unbounded fan-out over a large discovered roster spikes heap usage and gets the JVM
     * OOM-killed (observed as 502s on dashboard reload). Bounding concurrency keeps peak memory
     * flat while still overlapping network I/O. Override via {@code JOBWATCH_FETCH_CONCURRENCY}.
     */
    private static final int MAX_CONCURRENT_SOURCES = readConcurrencyLimit();

    private static int readConcurrencyLimit() {
        String raw = System.getenv("JOBWATCH_FETCH_CONCURRENCY");
        if (raw != null && !raw.isBlank()) {
            try {
                int v = Integer.parseInt(raw.trim());
                if (v > 0) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return 6;
    }

    /** Internal per-source outcome pairing stats with the jobs it produced. */
    private record Outcome(SourceStats stats, List<Job> jobs) {
    }

    /**
     * Invokes every source concurrently and returns the combined result.
     *
     * @param sources the sources to run (may be empty)
     * @return a {@link RunResult} with all jobs and one {@link SourceStats} per source
     */
    public RunResult run(List<JobSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return new RunResult(List.of(), List.of());
        }

        // Bound how many sources fetch at once to keep peak memory flat on small containers.
        Semaphore gate = new Semaphore(Math.max(1, MAX_CONCURRENT_SOURCES));

        List<Outcome> outcomes;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Outcome>> futures = new ArrayList<>(sources.size());
            for (JobSource source : sources) {
                futures.add(executor.submit(() -> runOne(source, gate)));
            }
            outcomes = new ArrayList<>(futures.size());
            for (Future<Outcome> future : futures) {
                outcomes.add(join(future));
            }
        }

        List<Job> allJobs = new ArrayList<>();
        List<SourceStats> stats = new ArrayList<>(outcomes.size());
        for (Outcome outcome : outcomes) {
            allJobs.addAll(outcome.jobs());
            stats.add(outcome.stats());
        }
        return new RunResult(allJobs, stats);
    }

    /** Executes a single source, converting any failure into a failed {@link SourceStats}. */
    private Outcome runOne(JobSource source, Semaphore gate) {
        String name = safeName(source);
        try {
            gate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(SourceStats.failure(name, e), List.of());
        }
        try {
            List<Job> jobs = source.fetchJobs();
            List<Job> safe = (jobs == null) ? List.of() : jobs;
            return new Outcome(SourceStats.success(name, safe.size()), safe);
        } catch (Exception e) {
            return new Outcome(SourceStats.failure(name, e), List.of());
        } finally {
            gate.release();
        }
    }

    /** {@code runOne} never throws, but guard the {@link Future} contract defensively. */
    private Outcome join(Future<Outcome> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            // Should not happen because runOne catches everything; record defensively.
            return new Outcome(SourceStats.failure("unknown", e.getCause() != null ? e.getCause() : e), List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(SourceStats.failure("unknown", e), List.of());
        }
    }

    private static String safeName(JobSource source) {
        try {
            String name = source.name();
            return name != null ? name : "unknown";
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}

