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
 * Fixture-based tests for {@link AshbyJobSource} (no HTTP).
 *
 * <ul>
 *   <li>{@code ashby-llamaindex.json} (9 jobs) &rarr; edge cases: isRemote=true + Hybrid,
 *       structured compensation, secondary "Remote" location</li>
 *   <li>{@code ashby-openai.json} (832 jobs) &rarr; production-style scale, blank remote flags</li>
 * </ul>
 */
class AshbyJobSourceTest {

    private static List<Job> llamaJobs;
    private static List<Job> openaiJobs;

    @BeforeAll
    static void parseFixtures() throws IOException {
        llamaJobs = new AshbyJobSource("LlamaIndex")
                .parse(readFixture("fixtures/ashby-llamaindex.json"));
        openaiJobs = new AshbyJobSource("OpenAI")
                .parse(readFixture("fixtures/ashby-openai.json"));
    }

    // --- llamaindex fixture ----------------------------------------------

    @Test
    void llamaReturnsJobs() {
        assertNotNull(llamaJobs);
        assertFalse(llamaJobs.isEmpty(), "expected jobs in the LlamaIndex fixture");
    }

    @Test
    void llamaMapsCoreFields() {
        Job job = llamaJobs.get(0);
        assertEquals("ashby", job.source());
        assertNotNull(job.sourceJobId());
        assertFalse(job.sourceJobId().isBlank());
        assertNotNull(job.title());
        assertEquals("LlamaIndex", job.company(), "company comes from the constructor");
        assertNotNull(job.location());
    }

    @Test
    void llamaMapsUrls() {
        Job job = llamaJobs.get(0);
        assertNotNull(job.jobUrl(), "jobUrl must be mapped");
        assertNotNull(job.applyUrl(), "applyUrl must be mapped");
        assertTrue(job.applyUrl().contains("/application"), "Ashby applyUrl points at the application form");
    }

    @Test
    void llamaMapsWorkplaceEmploymentAndRemote() {
        Job job = llamaJobs.get(0);
        assertEquals("Hybrid", job.workplaceType());
        assertEquals("FullTime", job.employmentType());
        // isRemote is true on this posting even though workplaceType is Hybrid.
        assertTrue(job.remote(), "isRemote=true should set remote=true");
    }

    @Test
    void llamaMapsPublishedAt() {
        assertNotNull(llamaJobs.get(0).postedAt(), "publishedAt must map to postedAt");
    }

    @Test
    void llamaExtractsStructuredSalary() {
        Job job = llamaJobs.get(0);
        assertEquals(200000.0, job.salaryMin(), "salary min from summaryComponents[type=Salary]");
        assertEquals(275000.0, job.salaryMax(), "salary max from summaryComponents[type=Salary]");
        assertEquals("USD", job.salaryCurrency());
    }

    @Test
    void llamaHasPlainTextDescription() {
        Job job = llamaJobs.get(0);
        assertNotNull(job.description());
        assertFalse(job.description().contains("<"), "description must be plain text");
    }

    @Test
    void llamaLeavesExperienceNull() {
        Job job = llamaJobs.get(0);
        assertNull(job.minExperienceYears());
        assertNull(job.maxExperienceYears());
    }

    // --- openai fixture ---------------------------------------------------

    @Test
    void openaiReturnsManyJobs() {
        assertNotNull(openaiJobs);
        assertTrue(openaiJobs.size() > 100, "OpenAI fixture is large; expected many jobs");
    }

    @Test
    void openaiFirstJobCompanyAndSalary() {
        Job job = openaiJobs.get(0);
        assertEquals("OpenAI", job.company());
        assertEquals("FullTime", job.employmentType());
        assertEquals(257000.0, job.salaryMin());
        assertEquals(335000.0, job.salaryMax());
        assertEquals("USD", job.salaryCurrency());
    }

    @Test
    void openaiBlankRemoteFlagIsNotRemote() {
        // The first OpenAI posting has blank isRemote/workplaceType and a city location.
        Job job = openaiJobs.get(0);
        assertNull(job.workplaceType(), "blank workplaceType should be null");
        assertFalse(job.remote(), "no remote signals should yield remote=false");
    }

    @Test
    void openaiNoJobIsMissingIdOrTitle() {
        for (Job job : openaiJobs) {
            assertNotNull(job.sourceJobId());
            assertNotNull(job.title());
        }
    }

    private static String readFixture(String classpathLocation) throws IOException {
        try (InputStream in = AshbyJobSourceTest.class.getClassLoader()
                .getResourceAsStream(classpathLocation)) {
            assertNotNull(in, "fixture not found on classpath: " + classpathLocation);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

