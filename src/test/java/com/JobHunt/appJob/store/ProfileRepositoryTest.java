package com.JobHunt.appJob.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ProfileRepository}: CRUD and enabled filtering of DB-backed role profiles.
 */
class ProfileRepositoryTest {

    @TempDir
    Path tempDir;

    private ProfileRepository repo;

    @BeforeEach
    void setup() {
        DataSource ds = SqliteTestSupport.dataSource(tempDir.resolve("profiles.db"));
        SqliteTestSupport.initSchema(ds);
        repo = new ProfileRepository(ds);
    }

    private static ProfileRow row(String name, boolean enabled) {
        return new ProfileRow(null, name,
                List.of("java", "spring"), List.of("kafka", "aws"), List.of("javascript"),
                List.of("stripe"), 0, 5, enabled);
    }

    @Test
    void insertAndReadBackRoundTrips() {
        long id = repo.insert(row("backend-java", true));
        ProfileRow r = repo.findById(id).orElseThrow();
        assertEquals("backend-java", r.name());
        assertTrue(r.includeTitle().contains("java"));
        assertTrue(r.stack().contains("kafka"));
        assertTrue(r.excludeTitle().contains("javascript"));
        assertTrue(r.companies().contains("stripe"));
        assertEquals(0, r.expMin());
        assertEquals(5, r.expMax());
        assertTrue(r.enabled());
    }

    @Test
    void emptyUntilInserted() {
        assertTrue(repo.isEmpty());
        repo.insert(row("p1", true));
        assertFalse(repo.isEmpty());
    }

    @Test
    void findAllEnabledExcludesDisabled() {
        repo.insert(row("on", true));
        repo.insert(row("off", false));
        List<ProfileRow> enabled = repo.findAllEnabled();
        assertEquals(1, enabled.size());
        assertEquals("on", enabled.get(0).name());
        assertEquals(2, repo.findAll().size());
    }

    @Test
    void updateChangesFields() {
        long id = repo.insert(row("p", true));
        repo.update(id, new ProfileRow(id, "p", List.of("go"), List.of("grpc"),
                List.of(), List.of(), 2, 8, false));
        ProfileRow r = repo.findById(id).orElseThrow();
        assertTrue(r.includeTitle().contains("go"));
        assertFalse(r.enabled());
        assertEquals(2, r.expMin());
    }

    @Test
    void deleteRemoves() {
        long id = repo.insert(row("p", true));
        repo.delete(id);
        assertTrue(repo.findById(id).isEmpty());
    }
}

