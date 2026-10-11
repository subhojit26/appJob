package com.JobHunt.appJob.config;

import com.JobHunt.appJob.filter.FilterProfile;
import com.JobHunt.appJob.filter.Profile;
import com.JobHunt.appJob.filter.ScoringWeights;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link AppProperties} binds correctly from {@code application.yml}.
 *
 * <p>Each test group covers a section of the configuration so a YAML typo surfaces here,
 * not silently at runtime.</p>
 */
@SpringBootTest
class AppPropertiesBindingTest {

    @Autowired
    AppProperties props;

    // ── filters ──────────────────────────────────────────────────────────────

    @Test
    void filtersContainExpectedLocations() {
        List<String> locs = props.getFilters().getLocations();
        assertTrue(locs.contains("india"),     "india must be in locations");
        assertTrue(locs.contains("bengaluru"), "bengaluru must be in locations");
        assertTrue(locs.contains("remote"),    "remote must be in locations");
    }

    @Test
    void filtersAllowRemoteWorldwide() {
        assertTrue(props.getFilters().isAllowRemoteWorldwide());
    }

    @Test
    void filtersExperienceRange() {
        assertEquals(0, props.getFilters().getExpMin());
        assertEquals(6, props.getFilters().getExpMax());
    }

    @Test
    void filtersTitleBlocklistContainsIntern() {
        assertTrue(props.getFilters().getBlockedTitleWords().contains("intern"));
    }

    @Test
    void toFilterProfileConvertsCorrectly() {
        FilterProfile fp = props.getFilters().toFilterProfile();
        assertNotNull(fp);
        assertFalse(fp.allowedLocations().isEmpty());
        assertEquals(0, fp.minExperienceYears());
        assertEquals(6, fp.maxExperienceYears());
    }

    // ── profiles ─────────────────────────────────────────────────────────────

    @Test
    void atLeastFourProfilesConfigured() {
        assertTrue(props.getProfiles().size() >= 4);
    }

    @Test
    void backendJavaProfileStructure() {
        AppProperties.ProfileConfig java = props.getProfiles().stream()
                .filter(p -> "backend-java".equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("backend-java profile missing from YAML"));

        assertTrue(java.getIncludeTitle().contains("java"),        "include-title must contain java");
        assertTrue(java.getIncludeTitle().contains("spring"),      "include-title must contain spring");
        assertTrue(java.getExcludeTitle().contains("javascript"),  "exclude-title must contain javascript");
        assertTrue(java.getStack().contains("kafka"),              "stack must contain kafka");
        assertTrue(java.getStack().contains("spring boot"),        "stack must contain spring boot");
        assertTrue(java.getCompanyTypes().contains("product"),     "company-types must contain product");
        assertTrue(java.getCompanyTypes().contains("fintech"),     "company-types must contain fintech");
    }

    @Test
    void profilePerProfileLocationsDefaultToEmpty() {
        // profiles without explicit locations should have an empty list (global filters apply)
        props.getProfiles().forEach(p ->
                assertNotNull(p.getLocations(), "locations must never be null, just empty"));
    }

    @Test
    void profileToFilterProfileUsesGlobal() {
        AppProperties.ProfileConfig p = props.getProfiles().get(0);
        FilterProfile fp = p.toFilterProfile(props.getFilters());
        // backend-java has no per-profile locations, so global locations should be used
        assertFalse(fp.allowedLocations().isEmpty(),
                "effective locations should come from global filters when profile doesn't override");
    }

    @Test
    void toProfileConvertsCorrectly() {
        Profile p = props.getProfiles().get(0).toProfile();
        assertNotNull(p.name());
        assertFalse(p.includeTitleKeywords().isEmpty());
    }

    @Test
    void defaultScoringWeightsApplied() {
        ScoringWeights w = props.getProfiles().get(0).toScoringWeights();
        assertEquals(5, w.titleInclude());
        assertEquals(2, w.descriptionInclude());
        assertEquals(1, w.remoteBonus());
    }

    @Test
    void toProfilesHelperProducesAllProfiles() {
        List<Profile> profiles = props.toProfiles();
        assertEquals(props.getProfiles().size(), profiles.size());
    }

    // ── sources ───────────────────────────────────────────────────────────────

    @Test
    void greenhouseEnabledWithNoHardcodedCompanies() {
        // Phase 6 / FR-1a: the ATS roster is DB-discovered, not listed in config.
        assertTrue(props.getSources().getGreenhouse().isEnabled());
        assertTrue(props.getSources().getGreenhouse().getCompanies().isEmpty());
    }

    @Test
    void leverEnabledWithNoHardcodedCompanies() {
        assertTrue(props.getSources().getLever().isEnabled());
        assertTrue(props.getSources().getLever().getCompanies().isEmpty());
    }

    @Test
    void ashbyEnabledWithNoHardcodedCompanies() {
        assertTrue(props.getSources().getAshby().isEnabled());
        assertTrue(props.getSources().getAshby().getCompanies().isEmpty());
    }

    @Test
    void discoveryEnabledByDefault() {
        assertTrue(props.getDiscovery().isEnabled());
        assertTrue(props.getDiscovery().getPruneAfter404() >= 1);
    }

    @Test
    void adzunaCountryIsIndia() {
        assertEquals("in", props.getSources().getAdzuna().getCountry());
    }

    @Test
    void adzunaQueriesPresent() {
        assertFalse(props.getSources().getAdzuna().getQueries().isEmpty());
        assertTrue(props.getSources().getAdzuna().getQueries().contains("java developer"));
    }

    @Test
    void adzunaResultsPerPage() {
        assertEquals(50, props.getSources().getAdzuna().getResultsPerPage());
    }

    @Test
    void adzunaMaxDaysOld() {
        assertEquals(2, props.getSources().getAdzuna().getMaxDaysOld());
    }

    // ── digest ────────────────────────────────────────────────────────────────

    @Test
    void digestMaxJobsIs50() {
        assertEquals(50, props.getDigest().getMaxJobs());
    }

    // ── schedule ─────────────────────────────────────────────────────────────

    @Test
    void scheduleCronIsNotBlank() {
        assertNotNull(props.getSchedule().getCron());
        assertFalse(props.getSchedule().getCron().isBlank());
    }
}


