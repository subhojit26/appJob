package com.JobHunt.appJob.filter;

import com.JobHunt.appJob.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ProfileMatcher} behaviors 11-18: title/description matches, score
 * accumulation, exclusion, matchedOn contents, case-insensitivity, explicit whole-word
 * semantics, and the no-match case.
 */
class ProfileMatcherTest {

    private final ProfileMatcher matcher = new ProfileMatcher(); // defaults: title +5, desc +2, remote +1

    private static Job job(String title, String description) {
        return Job.builder().source("greenhouse").sourceJobId("1")
                .company("Acme").title(title).description(description)
                .fetchedAt(Instant.now()).build();
    }

    private static Profile backendJava() {
        return new Profile(
                "backend-java",
                List.of("java", "spring boot", "backend"),
                List.of("kafka", "microservices"),
                List.of("intern"));
    }

    @Test
    void titleKeywordProducesAMatch() {
        Optional<JobMatch> m = matcher.match(job("Senior Java Developer", "Build services."), backendJava());
        assertTrue(m.isPresent());
        assertEquals("backend-java", m.get().profileName());
        assertTrue(m.get().matchedOn().contains("java"));
    }

    @Test
    void descriptionKeywordProducesAMatch() {
        Optional<JobMatch> m = matcher.match(job("Software Engineer", "We use Kafka heavily."), backendJava());
        assertTrue(m.isPresent(), "a description keyword alone should qualify");
        assertTrue(m.get().matchedOn().contains("kafka"));
    }

    @Test
    void multipleKeywordsAccumulateScore() {
        Job j = job("Java Backend Engineer", "Kafka and microservices at scale.");
        Optional<JobMatch> m = matcher.match(j, backendJava());
        assertTrue(m.isPresent());
        // title: java (+5), backend (+5) = 10; description: kafka (+2), microservices (+2) = 4 -> 14
        assertEquals(14, m.get().score());
        assertEquals(List.of("java", "backend", "kafka", "microservices"), m.get().matchedOn());
    }

    @Test
    void excludedKeywordPreventsAMatch() {
        Optional<JobMatch> m = matcher.match(job("Java Intern", "Kafka basics."), backendJava());
        assertFalse(m.isPresent(), "an exclude keyword must disqualify the job");
    }

    @Test
    void matchedOnContainsActualMatchingRules() {
        Job j = job("Java Developer", "Experience with Kafka.");
        JobMatch m = matcher.match(j, backendJava()).orElseThrow();
        assertEquals(List.of("java", "kafka"), m.matchedOn());
    }

    @Test
    void matchingIsCaseInsensitive() {
        Optional<JobMatch> m = matcher.match(job("JAVA developer", "KAFKA streams"), backendJava());
        assertTrue(m.isPresent());
        assertTrue(m.get().matchedOn().containsAll(List.of("java", "kafka")));
    }

    @Test
    void wholeWordMatchingIsExplicit() {
        // "java" must NOT match "javascript" (word-boundary semantics, not raw substring).
        Profile javaOnly = new Profile("java", List.of("java"), List.of(), List.of());
        assertTrue(matcher.match(job("JavaScript Engineer", "frontend"), javaOnly).isEmpty(),
                "java should not match javascript");
        assertTrue(matcher.match(job("Java Engineer", "backend"), javaOnly).isPresent(),
                "java should match the standalone word Java");
    }

    @Test
    void noMatchingKeywordsMeansNoMatch() {
        Optional<JobMatch> m = matcher.match(job("Accountant", "Payroll and ledgers."), backendJava());
        assertTrue(m.isEmpty(), "no include keywords matched -> no match / score 0");
    }

    @Test
    void remoteBonusAppliesOnlyToMatches() {
        Job remoteJava = Job.builder().source("lever").sourceJobId("2")
                .company("Acme").title("Java Engineer").description("services")
                .remote(true).fetchedAt(Instant.now()).build();
        JobMatch m = matcher.match(remoteJava, backendJava()).orElseThrow();
        assertEquals(6, m.score(), "java (+5) + remote bonus (+1)");
    }

    @Test
    void bestMatchPicksHighestScoringProfile() {
        Profile golang = new Profile("golang", List.of("golang", "go"), List.of("grpc"), List.of());
        Job j = job("Java Backend Engineer", "Kafka, microservices");
        JobMatch best = matcher.bestMatch(j, List.of(golang, backendJava())).orElseThrow();
        assertEquals("backend-java", best.profileName(), "the higher-scoring profile wins");
    }
}

