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
 * Fixture-based tests for {@link GreenhouseJobSource}.
 *
 * <p>These prove the parser works purely from saved JSON, with no HTTP calls:</p>
 * <pre>
 *   greenhouse-airbnb.json -> Jackson -> parser -> List&lt;Job&gt; -> assertions
 * </pre>
 */
class GreenhouseJobSourceTest {

    private static String fixture;
    private static List<Job> jobs;

    @BeforeAll
    static void parseFixture() throws IOException {
        fixture = readFixture("fixtures/greenhouse-airbnb.json");
        jobs = new GreenhouseJobSource().parse(fixture);
    }

    @Test
    void returnsJobs() {
        assertNotNull(jobs, "parse() must never return null");
        assertFalse(jobs.isEmpty(), "expected at least one job from the Airbnb fixture");
    }

    @Test
    void mapsCoreFields() {
        Job job = jobs.get(0);
        assertEquals("greenhouse", job.source());
        assertNotNull(job.sourceJobId(), "sourceJobId must be populated");
        assertFalse(job.sourceJobId().isBlank());
        assertNotNull(job.title(), "title must be mapped");
        assertEquals("Airbnb", job.company());
        assertNotNull(job.location(), "location must be mapped");
        assertNotNull(job.applyUrl(), "apply URL must be mapped");
        assertTrue(job.applyUrl().startsWith("http"), "apply URL should be absolute");
    }

    @Test
    void capturesHtmlDescriptionAsPlainText() {
        Job job = jobs.get(0);
        assertNotNull(job.description(), "description must be captured");
        assertFalse(job.description().contains("<p>"), "HTML tags must be stripped");
        assertFalse(job.description().contains("&lt;"), "HTML entities must be unescaped");
    }

    @Test
    void mapsWorkplaceTypeAndRemoteFlag() {
        // The first Airbnb posting has metadata Workplace Type = "Remote".
        Job job = jobs.get(0);
        assertEquals("Remote", job.workplaceType());
        assertTrue(job.remote(), "a Remote workplace type should set remote=true");
    }

    @Test
    void mapsPostedAt() {
        Job job = jobs.get(0);
        assertNotNull(job.postedAt(), "first_published should map to postedAt");
    }

    @Test
    void setsFetchedAt() {
        assertNotNull(jobs.get(0).fetchedAt(), "fetchedAt must be set by the parser");
    }

    @Test
    void leavesUnavailableFieldsNull() {
        Job job = jobs.get(0);
        assertNull(job.minExperienceYears(), "Greenhouse has no experience data");
        assertNull(job.maxExperienceYears(), "Greenhouse has no experience data");
        assertNull(job.salaryMin(), "Greenhouse salary is not structured");
        assertNull(job.salaryMax(), "Greenhouse salary is not structured");
        assertNull(job.salaryCurrency(), "Greenhouse salary is not structured");
        assertNull(job.employmentType(), "Greenhouse employment type is not provided here");
    }

    private static String readFixture(String classpathLocation) throws IOException {
        try (InputStream in = GreenhouseJobSourceTest.class.getClassLoader()
                .getResourceAsStream(classpathLocation)) {
            assertNotNull(in, "fixture not found on classpath: " + classpathLocation);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

