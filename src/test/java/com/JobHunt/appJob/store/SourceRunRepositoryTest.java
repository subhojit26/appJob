package com.JobHunt.appJob.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SourceRunRepository}: persisting successful/failed runs and the
 * "has this source ever run?" seeding gate.
 */
class SourceRunRepositoryTest {

    @TempDir
    Path tempDir;

    private SourceRunRepository repo;

    @BeforeEach
    void setup() {
        DataSource ds = SqliteTestSupport.dataSource(tempDir.resolve("runs.db"));
        SqliteTestSupport.initSchema(ds);
        repo = new SourceRunRepository(ds);
    }

    @Test
    void sourceHasRunsIsFalseBeforeAnyRun() {
        assertFalse(repo.sourceHasRuns("greenhouse"));
    }

    @Test
    void successfulRunPersistedAndSeedsSource() {
        repo.save(SourceRun.ok("greenhouse",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:01Z"), 50));
        assertTrue(repo.sourceHasRuns("greenhouse"));
        assertFalse(repo.sourceHasRuns("lever"), "other sources are unaffected");
    }

    @Test
    void failedRunPersistedWithErrorAlsoSeedsSource() {
        repo.save(SourceRun.failed("ashby",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:01Z"), "boom"));
        // "Has this source ever run?" is true even for a failed run.
        assertTrue(repo.sourceHasRuns("ashby"));
    }

    @Test
    void multipleRunsAreRecorded() {
        repo.save(SourceRun.failed("adzuna",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:01Z"), "timeout"));
        repo.save(SourceRun.ok("adzuna",
                Instant.parse("2026-10-01T01:00:00Z"), Instant.parse("2026-10-01T01:00:02Z"), 50));
        assertTrue(repo.sourceHasRuns("adzuna"));
    }
}
