package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.model.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link ExtractionService}.
 */
class ExtractionServiceTest {

    private final ExtractionService svc = new ExtractionService();
    private static final List<String> KEYWORDS =
            List.of("java", "spring boot", "kafka", "microservices", "kubernetes",
                    "c++", "c#", ".net", "node.js", "go", "golang", "react");

    // ── experience extraction ────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}-{2}")
    @CsvSource({
            "'2-5 years experience',           2, 5",
            "'3–8 years of experience',         3, 8",
            "'2 to 5 years',                    2, 5",
            "'5+ years',                        5, ''",
            "'7+ yrs',                          7, ''",
            "'minimum 3 years',                 3, ''",
            "'at least 4 years of experience',  4, ''",
            "'10 years of experience required', '', ''",   // no range/+ pattern → no match
            "'5 years experience',              '', ''",   // plain "N years" not in PRD patterns → unknown
    })
    void experiencePatterns(String text, String expectedMin, String expectedMax) {
        Job job = jobWithDescription(text);
        ExtractionService.ExperienceRange r = svc.extractExperience(job);

        Integer eMin = expectedMin.isBlank() ? null : Integer.parseInt(expectedMin);
        Integer eMax = expectedMax.isBlank() ? null : Integer.parseInt(expectedMax);
        assertEquals(eMin, r.min(), "expMin mismatch for: " + text);
        assertEquals(eMax, r.max(), "expMax mismatch for: " + text);
    }

    @Test
    void noExperienceMentionReturnsUnknown() {
        ExtractionService.ExperienceRange r = svc.extractExperience(jobWithDescription("Great company culture."));
        assertNull(r.min());
        assertNull(r.max());
    }

    @Test
    void nullDescriptionAndTitleReturnsUnknown() {
        Job job = Job.builder().source("test").sourceJobId("1").fetchedAt(Instant.now()).build();
        ExtractionService.ExperienceRange r = svc.extractExperience(job);
        assertNull(r.min());
        assertNull(r.max());
    }

    @Test
    void titleIsAlsoSearched() {
        Job job = jobWithTitle("Senior Engineer 5+ years");
        ExtractionService.ExperienceRange r = svc.extractExperience(job);
        assertEquals(5, r.min());
        assertNull(r.max());
    }

    @Test
    void firstPatternWins() {
        // Range pattern should match before min-only pattern when both are present.
        Job job = jobWithDescription("3-5 years, must have 7+ years");
        ExtractionService.ExperienceRange r = svc.extractExperience(job);
        assertEquals(3, r.min());
        assertEquals(5, r.max());
    }

    // ── stack extraction ─────────────────────────────────────────────────────

    @Test
    void stackKeywordMatchedInDescription() {
        Set<String> stack = svc.extractStack(jobWithDescription("We use kafka and kubernetes daily."), KEYWORDS);
        assertTrue(stack.contains("kafka"), "kafka should match");
        assertTrue(stack.contains("kubernetes"), "kubernetes should match");
    }

    @Test
    void stackMatchIsCaseInsensitive() {
        Set<String> stack = svc.extractStack(jobWithDescription("Experience with Kafka and Spring Boot."), KEYWORDS);
        assertTrue(stack.contains("kafka"));
        assertTrue(stack.contains("spring boot"));
    }

    @Test
    void javaDoesNotMatchJavascript() {
        Set<String> stack = svc.extractStack(jobWithDescription("3 years of javascript development."), KEYWORDS);
        assertFalse(stack.contains("java"), "java must not match inside javascript");
    }

    @Test
    void specialCharacterKeywordsMatch() {
        Set<String> stack = svc.extractStack(jobWithDescription("C++ and C# experience required. Also .NET and node.js."), KEYWORDS);
        assertTrue(stack.contains("c++"),    "c++ should match");
        assertTrue(stack.contains("c#"),     "c# should match");
        assertTrue(stack.contains(".net"),   ".net should match");
        assertTrue(stack.contains("node.js"),"node.js should match");
    }

    @Test
    void emptyKeywordsReturnsEmpty() {
        Set<String> stack = svc.extractStack(jobWithDescription("Java kafka microservices"), List.of());
        assertTrue(stack.isEmpty());
    }

    @Test
    void noMatchReturnsEmptySet() {
        Set<String> stack = svc.extractStack(jobWithDescription("Customer success role"), KEYWORDS);
        assertTrue(stack.isEmpty());
    }

    @Test
    void multiWordPhraseMatched() {
        Set<String> stack = svc.extractStack(jobWithDescription("We use Spring Boot microservices."), KEYWORDS);
        assertTrue(stack.contains("spring boot"));
    }

    // ── enrich ───────────────────────────────────────────────────────────────

    @Test
    void enrichFillsExperienceAndStack() {
        Job job = jobWithDescription("3-6 years. We use Java, Kafka, Spring Boot.");
        EnrichedJob e = svc.enrich(job, KEYWORDS);
        assertEquals(3, e.expMin());
        assertEquals(6, e.expMax());
        assertTrue(e.stack().contains("java"));
        assertTrue(e.stack().contains("kafka"));
        assertTrue(e.stack().contains("spring boot"));
    }

    @Test
    void enrichPreservesSourceProvidedExperience() {
        // If the source already set expMin/expMax, regex should not override them.
        Job job = Job.builder()
                .source("test").sourceJobId("1").fetchedAt(Instant.now())
                .title("Engineer")
                .description("3-6 years experience, Java Kafka")
                .minExperienceYears(2)   // source-provided wins
                .maxExperienceYears(4)
                .build();
        EnrichedJob e = svc.enrich(job, KEYWORDS);
        assertEquals(2, e.expMin(), "source-provided expMin must be preserved");
        assertEquals(4, e.expMax(), "source-provided expMax must be preserved");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Job jobWithDescription(String description) {
        return Job.builder()
                .source("test").sourceJobId("1").fetchedAt(Instant.now())
                .title("Software Engineer")
                .description(description)
                .build();
    }

    private static Job jobWithTitle(String title) {
        return Job.builder()
                .source("test").sourceJobId("1").fetchedAt(Instant.now())
                .title(title)
                .build();
    }
}

