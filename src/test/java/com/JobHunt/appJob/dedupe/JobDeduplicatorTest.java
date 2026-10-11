package com.JobHunt.appJob.dedupe;

import com.JobHunt.appJob.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JobDeduplicator}: exact and cross-source rules, conservative
 * normalization, safety against missing fields, empty input, and deterministic ordering.
 */
class JobDeduplicatorTest {

    private final JobDeduplicator dedup = new JobDeduplicator();

    private static Job.Builder base() {
        return Job.builder().fetchedAt(Instant.now());
    }

    private static Job job(String source, String id, String company, String title, String location) {
        return base().source(source).sourceJobId(id)
                .company(company).title(title).location(location).build();
    }

    @Test
    void exactDuplicateIsRemoved() {
        Job a = job("greenhouse", "123", "Acme", "Engineer", "Kolkata");
        Job b = job("greenhouse", "123", "Acme", "Engineer", "Kolkata");

        List<Job> out = dedup.deduplicate(List.of(a, b));

        assertEquals(1, out.size());
        assertSame(a, out.get(0), "the first occurrence is kept");
    }

    @Test
    void sameIdDifferentSourceIsRetained() {
        Job gh = job("greenhouse", "123", "Acme", "Engineer", "Kolkata");
        Job lv = job("lever", "123", "Globex", "Designer", "Pune");

        List<Job> out = dedup.deduplicate(List.of(gh, lv));

        assertEquals(2, out.size(), "same id on different sources are different postings");
    }

    @Test
    void differentIdsAreRetained() {
        Job a = job("greenhouse", "1", "Acme", "Engineer", "Kolkata");
        Job b = job("greenhouse", "2", "Globex", "Designer", "Pune");

        assertEquals(2, dedup.deduplicate(List.of(a, b)).size());
    }

    @Test
    void crossSourceSameOpeningIsRemoved() {
        Job gh = job("greenhouse", "1", "Acme", "Senior Engineer", "Bengaluru, Karnataka");
        Job az = job("adzuna", "999", "Acme", "Senior Engineer", "Bengaluru, Karnataka, India");

        List<Job> out = dedup.deduplicate(List.of(gh, az));

        assertEquals(1, out.size(), "same normalized company/title/city across sources is one opening");
        assertSame(gh, out.get(0), "the first source's job is kept");
    }

    @Test
    void differentCityIsRetained() {
        Job a = job("greenhouse", "1", "Acme", "Engineer", "Bengaluru, Karnataka");
        Job b = job("adzuna", "2", "Acme", "Engineer", "Pune, Maharashtra");

        assertEquals(2, dedup.deduplicate(List.of(a, b)).size(),
                "same company/title but different city are distinct openings");
    }

    @Test
    void caseAndWhitespaceDifferencesNormalize() {
        Job a = job("greenhouse", "1", "Acme Corp", "Senior  Engineer", "Bengaluru, KA");
        Job b = job("adzuna", "2", "  acme   corp ", "senior engineer", "bengaluru, karnataka");

        List<Job> out = dedup.deduplicate(List.of(a, b));

        assertEquals(1, out.size(), "case/whitespace differences should normalize to the same key");
        assertSame(a, out.get(0));
    }

    @Test
    void missingSourceJobIdDoesNotCollapseUnrelatedJobs() {
        Job a = base().source("rss").sourceJobId(null)
                .company("Acme").title("Engineer").location("Kolkata").build();
        Job b = base().source("rss").sourceJobId("  ")
                .company("Globex").title("Designer").location("Pune").build();

        List<Job> out = dedup.deduplicate(List.of(a, b));

        assertEquals(2, out.size(), "missing ids must not merge unrelated jobs");
    }

    @Test
    void missingCompanyOrTitleDoesNotProduceUniversalKey() {
        // No ids and missing company/title -> neither exact nor cross keys -> all retained.
        Job a = base().source("rss").company(null).title(null).location(null).build();
        Job b = base().source("feed").company(null).title(null).location(null).build();
        Job c = base().source("feed").company("Acme").title(null).location("Remote").build();

        List<Job> out = dedup.deduplicate(List.of(a, b, c));

        assertEquals(3, out.size(), "missing company/title must not form a collapsing universal key");
    }

    @Test
    void remoteJobsCollapseByCompanyTitleRemote() {
        Job a = base().source("greenhouse").sourceJobId("1")
                .company("Acme").title("Engineer").remote(true).location("United States").build();
        Job b = base().source("lever").sourceJobId("2")
                .company("Acme").title("Engineer").remote(false).location("Remote - US").build();

        List<Job> out = dedup.deduplicate(List.of(a, b));

        assertEquals(1, out.size(), "remote roles for the same company/title collapse to one");
        assertSame(a, out.get(0));
    }

    @Test
    void emptyInputReturnsEmptyOutput() {
        assertTrue(dedup.deduplicate(List.of()).isEmpty());
        assertTrue(dedup.deduplicate(null).isEmpty());
    }

    @Test
    void orderingIsDeterministicAndPreserved() {
        Job a = job("greenhouse", "1", "Acme", "Engineer", "Kolkata");
        Job dupA = job("greenhouse", "1", "Acme", "Engineer", "Kolkata");
        Job b = job("lever", "2", "Globex", "Designer", "Pune");
        Job c = job("ashby", "3", "Initech", "Manager", "Hyderabad");

        List<Job> out = dedup.deduplicate(List.of(a, dupA, b, c));

        assertEquals(List.of(a, b, c), out, "kept jobs must stay in first-seen input order");
    }
}

