package com.JobHunt.appJob.orchestration;

import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.source.JobSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for {@link RunOrchestrator}: success aggregation, per-source failure
 * isolation, empty results, duplicate source names, and genuine concurrency (proven with a
 * {@link CyclicBarrier} rather than timing/sleep).
 */
class RunOrchestratorTest {

    private final RunOrchestrator orchestrator = new RunOrchestrator();

    // --- test doubles -----------------------------------------------------

    /** A source returning a fixed number of jobs. */
    private static final class StubSource implements JobSource {
        private final String name;
        private final int count;
        StubSource(String name, int count) { this.name = name; this.count = count; }
        @Override public String name() { return name; }
        @Override public List<Job> fetchJobs() {
            return job(name, count);
        }
    }

    /** A source that always throws. */
    private static final class FailingSource implements JobSource {
        private final String name;
        private final RuntimeException error;
        FailingSource(String name, RuntimeException error) { this.name = name; this.error = error; }
        @Override public String name() { return name; }
        @Override public List<Job> fetchJobs() { throw error; }
    }

    /** A source that waits on a shared barrier before returning, proving concurrent execution. */
    private static final class BarrierSource implements JobSource {
        private final String name;
        private final CyclicBarrier barrier;
        BarrierSource(String name, CyclicBarrier barrier) { this.name = name; this.barrier = barrier; }
        @Override public String name() { return name; }
        @Override public List<Job> fetchJobs() throws Exception {
            try {
                // If sources ran sequentially, the barrier would never reach its party count
                // and this await would time out, failing that source.
                barrier.await(5, TimeUnit.SECONDS);
            } catch (TimeoutException | BrokenBarrierException e) {
                throw new IllegalStateException("sources did not run concurrently", e);
            }
            return job(name, 1);
        }
    }

    private static List<Job> job(String source, int count) {
        Instant now = Instant.now();
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Job.builder()
                        .source(source)
                        .sourceJobId(source + "-" + i)
                        .title("Job " + i)
                        .company("Co")
                        .fetchedAt(now)
                        .build())
                .collect(Collectors.toList());
    }

    private static Map<String, SourceStats> byName(RunResult result) {
        return result.sourceStats().stream()
                .collect(Collectors.toMap(SourceStats::sourceName, Function.identity(), (a, b) -> a));
    }

    // --- tests ------------------------------------------------------------

    @Test
    void allSourcesSucceed() {
        RunResult result = orchestrator.run(List.of(
                new StubSource("greenhouse", 2),
                new StubSource("lever", 3),
                new StubSource("ashby", 1)));

        assertEquals(6, result.totalJobs(), "all jobs should be combined");
        assertEquals(3, result.successfulSources());
        assertEquals(0, result.failedSources());

        Map<String, SourceStats> stats = byName(result);
        assertEquals(2, stats.get("greenhouse").jobsFetched());
        assertEquals(3, stats.get("lever").jobsFetched());
        assertEquals(1, stats.get("ashby").jobsFetched());
        stats.values().forEach(s -> assertTrue(s.success()));
    }

    @Test
    void oneSourceFailsOthersStillRun() {
        RuntimeException boom = new RuntimeException("lever exploded");
        RunResult result = orchestrator.run(List.of(
                new StubSource("greenhouse", 2),
                new FailingSource("lever", boom),
                new StubSource("ashby", 4)));

        // Successful jobs are still returned.
        assertEquals(6, result.totalJobs());
        assertEquals(2, result.successfulSources());
        assertEquals(1, result.failedSources());

        Map<String, SourceStats> stats = byName(result);
        assertTrue(stats.get("greenhouse").success());
        assertTrue(stats.get("ashby").success());

        SourceStats failed = stats.get("lever");
        assertFalse(failed.success(), "failed source must be recorded as failed");
        assertEquals(0, failed.jobsFetched());
        assertSame(boom, failed.error(), "the original error should be preserved");
    }

    @Test
    void multipleSourcesFailRemainIsolated() {
        RunResult result = orchestrator.run(List.of(
                new FailingSource("greenhouse", new RuntimeException("gh down")),
                new StubSource("lever", 5),
                new FailingSource("ashby", new RuntimeException("ashby down")),
                new StubSource("adzuna", 2)));

        assertEquals(7, result.totalJobs(), "only successful sources contribute jobs");
        assertEquals(2, result.successfulSources());
        assertEquals(2, result.failedSources());

        Map<String, SourceStats> stats = byName(result);
        assertFalse(stats.get("greenhouse").success());
        assertFalse(stats.get("ashby").success());
        assertTrue(stats.get("lever").success());
        assertTrue(stats.get("adzuna").success());
    }

    @Test
    void sourcesExecuteConcurrently() {
        int n = 4;
        CyclicBarrier barrier = new CyclicBarrier(n);
        RunResult result = orchestrator.run(List.of(
                new BarrierSource("s1", barrier),
                new BarrierSource("s2", barrier),
                new BarrierSource("s3", barrier),
                new BarrierSource("s4", barrier)));

        // Every source only returns after all reached the barrier — impossible unless concurrent.
        assertEquals(n, result.successfulSources(), "all barrier sources should succeed");
        assertEquals(n, result.totalJobs());
    }

    @Test
    void emptySourceResultCountsAsZero() {
        RunResult result = orchestrator.run(List.of(new StubSource("greenhouse", 0)));

        assertEquals(0, result.totalJobs());
        SourceStats stats = result.sourceStats().get(0);
        assertTrue(stats.success(), "zero jobs is still a success");
        assertEquals(0, stats.jobsFetched());
    }

    @Test
    void duplicateSourceNamesEachKeepTheirOwnStats() {
        // Two sources reporting the same name must not overwrite each other's stats.
        RunResult result = orchestrator.run(List.of(
                new StubSource("greenhouse", 1),
                new StubSource("greenhouse", 2)));

        assertEquals(2, result.sourceStats().size(), "each invocation keeps its own stats entry");
        assertEquals(3, result.totalJobs());
        List<Integer> counts = result.sourceStats().stream()
                .filter(s -> s.sourceName().equals("greenhouse"))
                .map(SourceStats::jobsFetched)
                .sorted()
                .toList();
        assertEquals(List.of(1, 2), counts, "both distinct job counts should be preserved");
    }

    @Test
    void emptySourceListReturnsEmptyResult() {
        RunResult result = orchestrator.run(List.of());
        assertNotNull(result);
        assertEquals(0, result.totalJobs());
        assertTrue(result.sourceStats().isEmpty());
    }
}

