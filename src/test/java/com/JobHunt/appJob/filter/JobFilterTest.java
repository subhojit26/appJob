package com.JobHunt.appJob.filter;

import com.JobHunt.appJob.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JobFilter} global (eligibility) filtering behaviors 1-10.
 */
class JobFilterTest {

    private static Job.Builder base() {
        return Job.builder().source("greenhouse").sourceJobId("1").company("Acme").title("Engineer")
                .fetchedAt(Instant.now());
    }

    private static FilterProfile config() {
        return new FilterProfile(
                List.of("bengaluru", "pune"),
                true,
                2, 6,
                List.of("staffingco"),
                List.of("intern", "manager"));
    }

    @Test
    void allowedLocationPasses() {
        Job job = base().location("Bengaluru, Karnataka").build();
        assertTrue(new JobFilter(config()).isEligible(job));
    }

    @Test
    void disallowedLocationRejected() {
        Job job = base().location("New Delhi").build();
        assertFalse(new JobFilter(config()).isEligible(job));
    }

    @Test
    void remoteJobPassesWhenRemoteEnabled() {
        Job job = base().remote(true).location("Anywhere").build();
        assertTrue(new JobFilter(config()).isEligible(job));
    }

    @Test
    void remoteJobRejectedWhenRemoteDisabled() {
        FilterProfile noRemote = new FilterProfile(
                List.of("bengaluru"), false, null, null, List.of(), List.of());
        Job job = base().remote(true).location("Remote").build();
        assertFalse(new JobFilter(noRemote).isEligible(job));
    }

    @Test
    void experienceRangeMatches() {
        Job job = base().location("Pune").minExperienceYears(3).maxExperienceYears(5).build();
        assertTrue(new JobFilter(config()).isEligible(job));
    }

    @Test
    void clearlyIncompatibleExperienceRejected() {
        // Job requires 10+ years; configured max is 6.
        Job tooSenior = base().location("Pune").minExperienceYears(10).maxExperienceYears(12).build();
        assertFalse(new JobFilter(config()).isEligible(tooSenior));

        // Job caps at 1 year; configured min is 2.
        Job tooJunior = base().location("Pune").minExperienceYears(0).maxExperienceYears(1).build();
        assertFalse(new JobFilter(config()).isEligible(tooJunior));
    }

    @Test
    void companyBlocklistRejects() {
        Job job = base().company("StaffingCo Pvt Ltd").location("Pune").build();
        assertFalse(new JobFilter(config()).isEligible(job));
    }

    @Test
    void titleBlocklistRejects() {
        Job job = base().title("Data Science Intern").location("Pune").build();
        assertFalse(new JobFilter(config()).isEligible(job));
    }

    @Test
    void missingOptionalFieldsBehaveSafely() {
        // Unknown experience, and a location that is allowed; company/title present and clean.
        Job job = base().location("Bengaluru")
                .minExperienceYears(null).maxExperienceYears(null).build();
        assertTrue(new JobFilter(config()).isEligible(job),
                "missing experience must not reject an otherwise-eligible job");
    }

    @Test
    void emptyConfigDoesNotRejectEverything() {
        JobFilter permissive = new JobFilter(FilterProfile.permissive());
        assertTrue(permissive.isEligible(base().location("Mars Colony").build()));
        assertTrue(permissive.isEligible(base().remote(true).build()));
        assertTrue(permissive.isEligible(base().company(null).title(null).location(null).build()));
    }
}

