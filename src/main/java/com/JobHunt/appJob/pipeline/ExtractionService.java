package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.model.Job;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured signals from the free-text fields of a {@link Job}.
 *
 * <p>Rules (PRD 7.2):</p>
 * <ul>
 *   <li><strong>Experience</strong> — regex patterns applied to {@code title + " " + description},
 *       first match wins, result is an integer range. Null when nothing matches; do not guess.</li>
 *   <li><strong>Stack tags</strong> — whole-word, case-insensitive keyword match against the
 *       description (and title) from a caller-supplied keyword list. Capped at 6 display tags
 *       (all matched tags are returned; callers cap for display).</li>
 * </ul>
 *
 * <p>This class is a pure stateless utility — no Spring, no DB, no HTTP.</p>
 */
public class ExtractionService {

    // PRD 7.2 patterns, applied in order; first match wins.
    // Group 1 = min years, Group 2 = max years (optional).
    private static final List<Pattern> EXP_PATTERNS = List.of(
            // "2-5 years" / "2–5 yrs" / "2 to 5 years"
            Pattern.compile(
                    "(\\d{1,2})\\s*(?:-|–|to)\\s*(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)",
                    Pattern.CASE_INSENSITIVE),
            // "5+ years" / "5 + yrs"
            Pattern.compile(
                    "(\\d{1,2})\\s*\\+\\s*(?:years?|yrs?)",
                    Pattern.CASE_INSENSITIVE),
            // "minimum 3 years" / "at least 4 years"
            Pattern.compile(
                    "(?:minimum|at\\s+least)\\s+(\\d{1,2})\\s+(?:years?|yrs?)",
                    Pattern.CASE_INSENSITIVE)
    );

    /**
     * Holds extracted experience range; both fields may be {@code null} if extraction failed.
     *
     * @param min minimum years of experience, or {@code null}
     * @param max maximum years of experience, or {@code null}
     */
    public record ExperienceRange(Integer min, Integer max) {
        public static ExperienceRange unknown() {
            return new ExperienceRange(null, null);
        }
    }

    /**
     * Extracts an experience range by running the PRD regex patterns against
     * {@code title + " " + description}, first match wins.
     *
     * @param job the job to inspect
     * @return an {@link ExperienceRange}; both fields are null when nothing matched
     */
    public ExperienceRange extractExperience(Job job) {
        String text = buildText(job);
        if (text.isBlank()) {
            return ExperienceRange.unknown();
        }

        // Pattern 1: range — "2-5 years"
        Matcher m1 = EXP_PATTERNS.get(0).matcher(text);
        if (m1.find()) {
            return new ExperienceRange(intOf(m1.group(1)), intOf(m1.group(2)));
        }

        // Pattern 2: min-only with "+" — "5+ years"
        Matcher m2 = EXP_PATTERNS.get(1).matcher(text);
        if (m2.find()) {
            return new ExperienceRange(intOf(m2.group(1)), null);
        }

        // Pattern 3: "minimum N years" / "at least N years"
        Matcher m3 = EXP_PATTERNS.get(2).matcher(text);
        if (m3.find()) {
            return new ExperienceRange(intOf(m3.group(1)), null);
        }

        return ExperienceRange.unknown();
    }

    /**
     * Matches the job's text against the supplied keyword list using whole-word,
     * case-insensitive matching.
     *
     * <p>Matching order preserves the order of {@code keywords} so callers can prioritise
     * more-important tags by putting them first.</p>
     *
     * @param job      the job whose title and description to search
     * @param keywords ordered list of tech-stack keywords to look for
     * @return matched keywords in the order they appeared in {@code keywords}, de-duplicated
     */
    public Set<String> extractStack(Job job, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Set.of();
        }
        String text = buildText(job).toLowerCase();
        Set<String> matched = new LinkedHashSet<>();
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            if (containsWord(text, kw.toLowerCase())) {
                matched.add(kw.toLowerCase());
            }
        }
        return matched;
    }

    /**
     * Returns a copy of the job with experience and stack populated from extraction.
     *
     * <p>Existing non-null experience fields on the job are preserved (source-provided values
     * take priority over regex extraction).</p>
     *
     * @param job      the job to enrich
     * @param keywords stack keyword list from configuration
     * @return a new {@link EnrichedJob} with extracted fields
     */
    public EnrichedJob enrich(Job job, List<String> keywords) {
        ExperienceRange exp = extractExperience(job);
        Integer expMin = job.minExperienceYears() != null ? job.minExperienceYears() : exp.min();
        Integer expMax = job.maxExperienceYears() != null ? job.maxExperienceYears() : exp.max();
        Set<String> stack = extractStack(job, keywords);
        return new EnrichedJob(job, expMin, expMax, stack);
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private static String buildText(Job job) {
        String title = job.title() != null ? job.title() : "";
        String desc  = job.description() != null ? job.description() : "";
        return (title + " " + desc).trim();
    }

    /**
     * Case-insensitive whole-word match. Handles multi-word phrases and special characters
     * like {@code c++}, {@code c#}, {@code .net}, {@code node.js}.
     */
    static boolean containsWord(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return false;
        }
        // Escape regex metacharacters in the keyword for safe embedding.
        String escaped = Pattern.quote(keyword.trim());
        // Use word boundaries when the keyword starts/ends with a word char;
        // otherwise fall back to (?<!\w) / (?!\w) lookarounds (handles "c++", ".net").
        String lBound = Character.isLetterOrDigit(keyword.charAt(0))                   ? "\\b" : "(?<!\\w)";
        String rBound = Character.isLetterOrDigit(keyword.charAt(keyword.length() - 1)) ? "\\b" : "(?!\\w)";
        Pattern p = Pattern.compile(lBound + escaped + rBound, Pattern.CASE_INSENSITIVE);
        return p.matcher(text).find();
    }

    private static Integer intOf(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

