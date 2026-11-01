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
 * Fixture-based tests for {@link AdzunaJobSource} (no HTTP).
 *
 * <p>Uses {@code adzuna-india-java.json} (50 results). Adzuna differs from the ATS sources:
 * jobs are wrapped in {@code results[]}, company/location are nested under
 * {@code display_name}, and salary can be predicted (flagged via {@code salaryEstimated}).</p>
 */
class AdzunaJobSourceTest {

    private static List<Job> jobs;

    @BeforeAll
    static void parseFixture() throws IOException {
        jobs = new AdzunaJobSource().parse(readFixture("fixtures/adzuna-india-java.json"));
    }

    @Test
    void returnsJobs() {
        assertNotNull(jobs);
        assertEquals(50, jobs.size(), "the India/Java fixture has 50 results");
    }

    @Test
    void mapsCoreFields() {
        // First result: Backend Developer at Tata Consultancy Services, Kolkata.
        Job job = jobs.get(0);
        assertEquals("adzuna", job.source());
        assertEquals("5880951401", job.sourceJobId());
        assertNotNull(job.title());
        assertEquals("Tata Consultancy Services", job.company(), "company.display_name maps to company");
        assertEquals("Kolkata, West Bengal", job.location(), "location.display_name maps to location");
    }

    @Test
    void mapsRedirectUrlToBothUrls() {
        Job job = jobs.get(0);
        assertNotNull(job.applyUrl());
        assertEquals(job.applyUrl(), job.jobUrl(), "redirect_url maps to both applyUrl and jobUrl");
        assertTrue(job.applyUrl().startsWith("http"));
    }

    @Test
    void mapsCreatedToPostedAt() {
        assertNotNull(jobs.get(0).postedAt(), "created must map to postedAt");
    }

    @Test
    void capturesPlainTextDescription() {
        Job job = jobs.get(0);
        assertNotNull(job.description());
        assertFalse(job.description().contains("<"), "description should be plain text");
    }

    @Test
    void employerProvidedSalaryIsNotEstimated() {
        // Java Spring Boot Lead: salary_min=1,000,000 salary_max=3,000,000 salary_is_predicted=0.
        Job job = find("5067552082");
        assertEquals(1_000_000.0, job.salaryMin());
        assertEquals(3_000_000.0, job.salaryMax());
        assertFalse(job.salaryEstimated(), "salary_is_predicted=0 means employer-provided");
        assertNull(job.salaryCurrency(), "Adzuna payload has no currency field");
    }

    @Test
    void mapsEmploymentTypeFromContractTime() {
        Job job = find("5067552082");
        assertEquals("full_time", job.employmentType(),
                "contract_time should populate employmentType when contract_type is absent");
    }

    @Test
    void noPredictedSalaryInThisFixture() {
        // Sanity: none of the results in this fixture are predicted, so none should be flagged.
        assertTrue(jobs.stream().noneMatch(Job::salaryEstimated),
                "this fixture contains only employer-provided salaries");
    }

    @Test
    void jobsWithoutSalaryHaveNullSalary() {
        long withSalary = jobs.stream().filter(j -> j.salaryMin() != null).count();
        assertEquals(16, withSalary, "only some results carry a salary");
        jobs.stream()
                .filter(j -> j.salaryMin() == null)
                .forEach(j -> assertNull(j.salaryMax(), "missing salary should leave both bounds null"));
    }

    @Test
    void leavesExperienceNull() {
        Job job = jobs.get(0);
        assertNull(job.minExperienceYears());
        assertNull(job.maxExperienceYears());
    }

    private static Job find(String sourceJobId) {
        return jobs.stream()
                .filter(j -> sourceJobId.equals(j.sourceJobId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("job not found: " + sourceJobId));
    }

    private static String readFixture(String classpathLocation) throws IOException {
        try (InputStream in = AdzunaJobSourceTest.class.getClassLoader()
                .getResourceAsStream(classpathLocation)) {
            assertNotNull(in, "fixture not found on classpath: " + classpathLocation);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

