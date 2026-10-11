package com.JobHunt.appJob.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CompanyDiscoveryService#slugCandidates(String)} — the pure name→slug
 * derivation used to turn Adzuna company display names into ATS board candidates.
 */
class CompanyDiscoveryServiceTest {

    @Test
    void firstWordAndConcatAreCandidates() {
        Set<String> c = CompanyDiscoveryService.slugCandidates("Razorpay Software Pvt Ltd");
        assertTrue(c.contains("razorpay"), "first word should be a candidate");
        assertTrue(c.contains("razorpaysoftwarepvtltd"), "alphanumeric concat should be a candidate");
    }

    @Test
    void singleWordNameYieldsThatSlug() {
        Set<String> c = CompanyDiscoveryService.slugCandidates("Stripe");
        assertTrue(c.contains("stripe"));
    }

    @Test
    void punctuationAndCaseAreStripped() {
        Set<String> c = CompanyDiscoveryService.slugCandidates("GitLab, Inc.");
        assertTrue(c.contains("gitlab"));
    }

    @Test
    void blankNameYieldsNoCandidates() {
        assertTrue(CompanyDiscoveryService.slugCandidates("").isEmpty());
        assertTrue(CompanyDiscoveryService.slugCandidates(null).isEmpty());
    }

    @Test
    void overlongConcatIsSkipped() {
        // A very long multi-word name should not produce a 30+ char concat slug.
        Set<String> c = CompanyDiscoveryService.slugCandidates(
                "Some Extremely Long Company Name That Exceeds Limits");
        assertFalse(c.contains("someextremelylongcompanynamethatexceedslimits"));
    }
}

