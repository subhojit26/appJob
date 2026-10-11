package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.store.JobRepository;
import com.JobHunt.appJob.store.SourceRunRepository;
import com.JobHunt.appJob.store.StoredJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static com.JobHunt.appJob.store.SqliteTestSupport.dataSource;
import static com.JobHunt.appJob.store.SqliteTestSupport.initSchema;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FirstRunSeeder} first-run seeding logic (PRD 7.6).
 */
class FirstRunSeederTest {

    @TempDir Path tempDir;

    private JobRepository jobRepo;
    private SourceRunRepository sourceRunRepo;
    private FirstRunSeeder seeder;

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        DataSource ds = dataSource(tempDir.resolve("seed.db"));
        initSchema(ds);
        jobRepo = new JobRepository(ds);
        sourceRunRepo = new SourceRunRepository(ds);
        seeder = new FirstRunSeeder(sourceRunRepo, jobRepo);
    }

    @Test
    void isFirstRunTrueWhenNoRunsRecorded() {
        assertTrue(seeder.isFirstRun("greenhouse"));
    }

    @Test
    void recentJobIsInsertedPendingNotification() {
        Job recentJob = job("1", NOW.minusSeconds(3600)); // 1 hour ago — within 48h
        int newCount = seeder.seed("greenhouse", List.of(recentJob), NOW);

        assertEquals(1, newCount, "recent job should be counted as new");

        StoredJob stored = jobRepo.findBySourceAndId("greenhouse", "1").orElseThrow();
        assertNull(stored.notifiedAt(), "recent job must have notified_at = null");
    }

    @Test
    void oldJobIsInsertedSuppressed() {
        Job oldJob = job("2", NOW.minusSeconds(72 * 3600)); // 72 hours ago — beyond 48h
        int newCount = seeder.seed("greenhouse", List.of(oldJob), NOW);

        assertEquals(0, newCount, "old job should not be counted as new");

        StoredJob stored = jobRepo.findBySourceAndId("greenhouse", "2").orElseThrow();
        assertNotNull(stored.notifiedAt(), "old job must have notified_at set (suppressed)");
    }

    @Test
    void jobWithNullPostedAtIsSuppressed() {
        Job noDate = Job.builder()
                .source("greenhouse").sourceJobId("3")
                .title("Unknown date").company("Acme")
                .applyUrl("https://example.com/3")
                .fetchedAt(NOW)
                .build(); // postedAt = null

        int newCount = seeder.seed("greenhouse", List.of(noDate), NOW);

        assertEquals(0, newCount, "job with unknown date should be suppressed on first run");
        StoredJob stored = jobRepo.findBySourceAndId("greenhouse", "3").orElseThrow();
        assertNotNull(stored.notifiedAt());
    }

    @Test
    void mixedBatchCountsOnlyRecentOnes() {
        Job recent1 = job("10", NOW.minusSeconds(1800));  // 30 min ago
        Job recent2 = job("11", NOW.minusSeconds(7200));  // 2h ago
        Job old1    = job("12", NOW.minusSeconds(50 * 3600)); // 50h ago

        int newCount = seeder.seed("greenhouse", List.of(recent1, recent2, old1), NOW);

        assertEquals(2, newCount);
    }

    @Test
    void emptyListReturnsZero() {
        assertEquals(0, seeder.seed("greenhouse", List.of(), NOW));
        assertEquals(0, seeder.seed("greenhouse", null, NOW));
    }

    @Test
    void exactly48hBoundaryIsConsideredRecent() {
        // Exactly 48h old — should pass as recent (<=)
        Job boundary = job("99", NOW.minusSeconds(48 * 3600));
        int newCount = seeder.seed("greenhouse", List.of(boundary), NOW);
        assertEquals(1, newCount);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Job job(String id, Instant postedAt) {
        return Job.builder()
                .source("greenhouse").sourceJobId(id)
                .title("Software Engineer").company("Acme Corp")
                .applyUrl("https://example.com/" + id)
                .postedAt(postedAt)
                .fetchedAt(NOW)
                .build();
    }
}

