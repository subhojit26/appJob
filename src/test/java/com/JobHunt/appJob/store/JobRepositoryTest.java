package com.JobHunt.appJob.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JobRepository} against a temp-file SQLite database:
 * idempotent insert on the natural key, field round-trip (incl. salaryEstimated), and
 * limit/ordered pending selection with {@code markNotified} touching only the given ids.
 */
class JobRepositoryTest {

    @TempDir
    Path tempDir;

    private JobRepository repo;

    @BeforeEach
    void setup() {
        DataSource ds = SqliteTestSupport.dataSource(tempDir.resolve("jobs.db"));
        SqliteTestSupport.initSchema(ds);
        repo = new JobRepository(ds);
    }

    private static StoredJob.Builder job(String source, String id) {
        return StoredJob.builder()
                .source(source).sourceJobId(id)
                .company("Acme").title("Engineer").applyUrl("https://x/apply")
                .createdAt(Instant.now())
                .fetchedAt(Instant.now());  // fresh by default so freshness window includes it
    }

    @Test
    void insertIfNewIsIdempotentOnNaturalKey() {
        StoredJob j = job("greenhouse", "123").build();

        assertTrue(repo.insertIfNew(j), "first insert creates the row");
        assertFalse(repo.insertIfNew(j), "second insert of same (source, id) is ignored");

        assertTrue(repo.findBySourceAndId("greenhouse", "123").isPresent());
    }

    @Test
    void stackTagsRoundTrip() {
        StoredJob j = job("greenhouse", "stk-1")
                .stack(new java.util.LinkedHashSet<>(List.of("java", "spring boot", "kafka")))
                .build();
        assertTrue(repo.insertIfNew(j));

        StoredJob read = repo.findBySourceAndId("greenhouse", "stk-1").orElseThrow();
        assertTrue(read.stack().contains("java"));
        assertTrue(read.stack().contains("spring boot"));
        assertTrue(read.stack().contains("kafka"));
        assertEquals(3, read.stack().size());
    }

    @Test
    void emptyStackReadsBackAsEmptySet() {
        assertTrue(repo.insertIfNew(job("lever", "no-stack").build()));
        StoredJob read = repo.findBySourceAndId("lever", "no-stack").orElseThrow();
        assertNotNull(read.stack());
        assertTrue(read.stack().isEmpty());
    }

    @Test
    void sameIdDifferentSourceCreatesTwoRows() {
        assertTrue(repo.insertIfNew(job("greenhouse", "123").build()));
        assertTrue(repo.insertIfNew(job("lever", "123").build()));

        assertTrue(repo.findBySourceAndId("greenhouse", "123").isPresent());
        assertTrue(repo.findBySourceAndId("lever", "123").isPresent());
    }

    @Test
    void roundTripsFieldsIncludingSalaryEstimatedAndNulls() {
        StoredJob j = job("ashby", "a1")
                .remote(true).workplaceType("Remote").location("Remote")
                .jobUrl("https://x/job").description("Build things")
                .postedAt(Instant.parse("2026-09-30T12:00:00Z"))
                .minExperienceYears(2).maxExperienceYears(6)
                .salaryMin(200000.0).salaryMax(275000.0).salaryCurrency("USD")
                .salaryEstimated(true).employmentType("FullTime")
                .build();
        assertTrue(repo.insertIfNew(j));

        StoredJob got = repo.findBySourceAndId("ashby", "a1").orElseThrow();
        assertTrue(got.remote());
        assertEquals("Remote", got.workplaceType());
        assertEquals(200000.0, got.salaryMin());
        assertEquals(275000.0, got.salaryMax());
        assertEquals("USD", got.salaryCurrency());
        assertTrue(got.salaryEstimated(), "salary_estimated must round-trip");
        assertEquals("FullTime", got.employmentType());
        assertEquals(2, got.minExperienceYears());
        assertEquals(6, got.maxExperienceYears());
        assertEquals(Instant.parse("2026-09-30T12:00:00Z"), got.postedAt());
        assertNotNull(got.createdAt());
        assertNull(got.notifiedAt(), "not notified yet");
        assertNotNull(got.id());
    }

    @Test
    void nullSalaryAndExperienceRoundTripAsNull() {
        StoredJob j = job("s", "n1").build(); // no salary/experience set
        assertTrue(repo.insertIfNew(j));

        StoredJob got = repo.findBySourceAndId("s", "n1").orElseThrow();
        assertNull(got.salaryMin());
        assertNull(got.salaryMax());
        assertNull(got.salaryCurrency());
        assertNull(got.minExperienceYears());
        assertNull(got.maxExperienceYears());
        assertFalse(got.salaryEstimated());
    }

    @Test
    void pendingReturnsOnlyUnnotifiedNewestFirstRespectingLimit() {
        Instant now = Instant.now();
        repo.insertIfNew(job("s", "old").postedAt(now.minusSeconds(3 * 3600)).build());
        repo.insertIfNew(job("s", "new").postedAt(now.minusSeconds(1 * 3600)).build());
        repo.insertIfNew(job("s", "mid").postedAt(now.minusSeconds(2 * 3600)).build());
        repo.insertIfNew(job("s", "already").postedAt(now.minusSeconds(1800))
                .notifiedAt(now.minusSeconds(600)).build());

        List<StoredJob> pending = repo.findPendingNotifications(2);

        assertEquals(2, pending.size(), "limit is respected");
        assertEquals("new", pending.get(0).sourceJobId(), "newest first");
        assertEquals("mid", pending.get(1).sourceJobId());
    }

    @Test
    void stalePostingsAreExcludedFromPending() {
        Instant now = Instant.now();
        // Fresh (within the 3-day window) vs stale (older than 3 days).
        repo.insertIfNew(job("s", "fresh").postedAt(now.minusSeconds(3600)).build());
        repo.insertIfNew(job("s", "stale")
                .postedAt(now.minus(java.time.Duration.ofDays(JobRepository.FRESH_DAYS + 1)))
                .fetchedAt(now.minus(java.time.Duration.ofDays(JobRepository.FRESH_DAYS + 1)))
                .build());

        List<StoredJob> pending = repo.findPendingNotifications(10);
        assertEquals(1, pending.size(), "only the fresh job is pending");
        assertEquals("fresh", pending.get(0).sourceJobId());
    }

    @Test
    void alreadyNotifiedJobsAreNotReturned() {
        repo.insertIfNew(job("s", "a").build());
        repo.insertIfNew(job("s", "b").build());

        long idA = repo.findBySourceAndId("s", "a").orElseThrow().id();
        repo.markNotified(List.of(idA), Instant.parse("2026-10-01T02:00:00Z"));

        List<StoredJob> pending = repo.findPendingNotifications(10);
        assertEquals(1, pending.size());
        assertEquals("b", pending.get(0).sourceJobId());
    }

    @Test
    void markNotifiedOnlyTouchesGivenIds() {
        repo.insertIfNew(job("s", "a").build());
        repo.insertIfNew(job("s", "b").build());
        repo.insertIfNew(job("s", "c").build());

        long idA = repo.findBySourceAndId("s", "a").orElseThrow().id();
        long idC = repo.findBySourceAndId("s", "c").orElseThrow().id();

        int updated = repo.markNotified(List.of(idA, idC), Instant.parse("2026-10-01T02:00:00Z"));
        assertEquals(2, updated);

        List<StoredJob> pending = repo.findPendingNotifications(10);
        assertEquals(1, pending.size());
        assertEquals("b", pending.get(0).sourceJobId());

        Optional<StoredJob> a = repo.findBySourceAndId("s", "a");
        assertTrue(a.isPresent());
        assertEquals(Instant.parse("2026-10-01T02:00:00Z"), a.get().notifiedAt());
        assertEquals(Instant.parse("2026-10-01T02:00:00Z"), a.get().updatedAt(), "updated_at is stamped");
    }

    @Test
    void markNotifiedWithEmptyIdsIsNoOp() {
        repo.insertIfNew(job("s", "a").build());
        assertEquals(0, repo.markNotified(List.of(), Instant.now()));
        assertEquals(1, repo.findPendingNotifications(10).size());
    }
}
