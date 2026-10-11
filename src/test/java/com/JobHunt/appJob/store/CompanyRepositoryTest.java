package com.JobHunt.appJob.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CompanyRepository}: the discovered roster (Phase 6).
 */
class CompanyRepositoryTest {

    @TempDir
    Path tempDir;

    private CompanyRepository repo;

    @BeforeEach
    void setup() {
        DataSource ds = SqliteTestSupport.dataSource(tempDir.resolve("companies.db"));
        SqliteTestSupport.initSchema(ds);
        repo = new CompanyRepository(ds);
    }

    @Test
    void emptyUntilSomethingDiscovered() {
        assertTrue(repo.isEmpty());
        repo.upsertDiscovered("greenhouse", "stripe", "stripe", Instant.now());
        assertFalse(repo.isEmpty());
    }

    @Test
    void upsertIsIdempotentAndListsEnabledSlugs() {
        Instant now = Instant.now();
        repo.upsertDiscovered("greenhouse", "stripe", "Stripe", now);
        repo.upsertDiscovered("greenhouse", "stripe", "Stripe", now); // duplicate
        repo.upsertDiscovered("greenhouse", "airbnb", "Airbnb", now);
        repo.upsertDiscovered("lever", "entrata", "Entrata", now);

        List<String> gh = repo.findEnabledSlugs("greenhouse");
        assertEquals(List.of("airbnb", "stripe"), gh);
        assertEquals(List.of("entrata"), repo.findEnabledSlugs("lever"));
    }

    @Test
    void recordMissDisablesAfterThreshold() {
        Instant now = Instant.now();
        repo.upsertDiscovered("ashby", "openai", "OpenAI", now);

        repo.recordMiss("ashby", "openai", 3); // 1
        assertEquals(List.of("openai"), repo.findEnabledSlugs("ashby"));
        repo.recordMiss("ashby", "openai", 3); // 2
        assertEquals(List.of("openai"), repo.findEnabledSlugs("ashby"));
        repo.recordMiss("ashby", "openai", 3); // 3 → disabled
        assertTrue(repo.findEnabledSlugs("ashby").isEmpty());
    }

    @Test
    void rediscoveryReenablesAndResetsCounter() {
        Instant now = Instant.now();
        repo.upsertDiscovered("lever", "cred", "Cred", now);
        repo.recordMiss("lever", "cred", 1); // disables
        assertTrue(repo.findEnabledSlugs("lever").isEmpty());

        repo.upsertDiscovered("lever", "cred", "Cred", now); // live again
        assertEquals(List.of("cred"), repo.findEnabledSlugs("lever"));
    }
}

