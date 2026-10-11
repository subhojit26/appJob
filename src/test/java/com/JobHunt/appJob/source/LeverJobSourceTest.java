package com.JobHunt.appJob.source;

import com.JobHunt.appJob.model.Job;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture-based tests for {@link LeverJobSource} (no HTTP).
 *
 * <ul>
 *   <li>{@code lever-demo.json} &rarr; parser-shape / edge cases (no salaryRange, remote flag)</li>
 *   <li>{@code lever-entrata.json} &rarr; production-style coverage (salaryRange present)</li>
 * </ul>
 */
class LeverJobSourceTest {

    private static List<Job> demoJobs;
    private static List<Job> entrataJobs;

    @BeforeAll
    static void parseFixtures() throws IOException {
        demoJobs = new LeverJobSource("Lever Demo")
                .parse(readFixture("fixtures/lever-demo.json"));
        entrataJobs = new LeverJobSource("Entrata")
                .parse(readFixture("fixtures/lever-entrata.json"));
    }

    // --- demo fixture -----------------------------------------------------

    @Test
    void demoReturnsJobs() {
        assertNotNull(demoJobs);
        assertFalse(demoJobs.isEmpty(), "expected postings in lever-demo fixture");
    }

    @Test
    void demoMapsCoreFields() {
        Job job = demoJobs.get(0);
        assertEquals("lever", job.source());
        assertNotNull(job.sourceJobId(), "id must map to sourceJobId");
        assertFalse(job.sourceJobId().isBlank());
        assertNotNull(job.title(), "text must map to title");
        assertEquals("Lever Demo", job.company(), "company comes from the constructor");
        assertNotNull(job.location(), "categories.location must map to location");
    }

    @Test
    void demoMapsUrlsSeparately() {
        Job job = demoJobs.get(0);
        assertNotNull(job.applyUrl(), "applyUrl must be mapped");
        assertNotNull(job.jobUrl(), "hostedUrl must map to jobUrl");
        assertTrue(job.applyUrl().endsWith("/apply"), "Lever applyUrl ends with /apply");
        assertFalse(job.jobUrl().endsWith("/apply"), "hostedUrl is the posting URL, not the apply URL");
    }

    @Test
    void demoMapsWorkplaceTypeAndRemote() {
        Job job = demoJobs.get(0);
        assertEquals("remote", job.workplaceType());
        assertTrue(job.remote(), "workplaceType=remote should set remote=true");
    }

    @Test
    void demoMapsPostedAtFromEpochMillis() {
        Job job = demoJobs.get(0);
        assertNotNull(job.postedAt(), "createdAt (epoch ms) must map to postedAt");
    }

    @Test
    void demoMapsEmploymentType() {
        Job job = demoJobs.get(0);
        assertEquals("Part-time", job.employmentType(), "categories.commitment maps to employmentType");
    }

    @Test
    void demoHasPlainTextDescription() {
        Job job = demoJobs.get(0);
        assertNotNull(job.description(), "descriptionPlain should populate description");
        assertFalse(job.description().contains("<p>"), "description must be plain text");
    }

    @Test
    void demoLeavesSalaryAndExperienceNull() {
        // The demo postings have no salaryRange.
        Job job = demoJobs.get(0);
        assertNull(job.salaryMin(), "no salaryRange in demo");
        assertNull(job.salaryMax(), "no salaryRange in demo");
        assertNull(job.salaryCurrency(), "no salaryRange in demo");
        assertNull(job.minExperienceYears(), "Lever has no experience data");
        assertNull(job.maxExperienceYears(), "Lever has no experience data");
    }

    // --- entrata fixture --------------------------------------------------

    @Test
    void entrataReturnsJobs() {
        assertNotNull(entrataJobs);
        assertFalse(entrataJobs.isEmpty(), "expected postings in lever-entrata fixture");
    }

    @Test
    void entrataMapsSalaryRange() {
        Job job = entrataJobs.get(0);
        assertEquals("Entrata", job.company());
        assertNotNull(job.salaryMin(), "salaryRange.min must map to salaryMin");
        assertNotNull(job.salaryMax(), "salaryRange.max must map to salaryMax");
        assertEquals("USD", job.salaryCurrency(), "salaryRange.currency must map to salaryCurrency");
        assertTrue(job.salaryMax() >= job.salaryMin(), "max should be >= min");
    }

    @Test
    void entrataOnsiteIsNotRemote() {
        Job job = entrataJobs.get(0);
        assertEquals("onsite", job.workplaceType());
        assertFalse(job.remote(), "onsite workplaceType should not be remote");
    }

    private static String readFixture(String classpathLocation) throws IOException {
        try (InputStream in = LeverJobSourceTest.class.getClassLoader()
                .getResourceAsStream(classpathLocation)) {
            assertNotNull(in, "fixture not found on classpath: " + classpathLocation);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

