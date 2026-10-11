package com.JobHunt.appJob.filter;

import com.JobHunt.appJob.model.Job;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, deterministic rule-based profile matcher. Produces explainable {@link JobMatch}es with a
 * score and {@code matchedOn} list. No Spring/HTTP/DB dependencies, and not an "AI matcher" — a
 * semantic/LLM matcher could later implement the same conceptual contract without touching the
 * source, orchestration, dedupe, or persistence layers.
 *
 * <h2>Matching semantics (explicit)</h2>
 * Keywords match as <strong>case-insensitive whole words</strong> (word-boundary), not arbitrary
 * substrings. So {@code "java"} matches "Senior Java Developer" but <em>not</em> "javascript",
 * and multi-word keywords like {@code "spring boot"} match as a phrase. Title keywords are checked
 * against the title; description keywords against the description.
 *
 * <h2>Match rule</h2>
 * A job matches a profile iff at least one include keyword matches and no exclude keyword appears
 * in the title or description. The remote bonus only applies to an already-matching job.
 */
public class ProfileMatcher {

    private final ScoringWeights weights;
    private final int minScore;

    public ProfileMatcher() {
        this(ScoringWeights.defaults(), 0);
    }

    public ProfileMatcher(ScoringWeights weights) {
        this(weights, 0);
    }

    /** Uses default scoring weights with a minimum-score (similarity) threshold. */
    public ProfileMatcher(int minScore) {
        this(ScoringWeights.defaults(), minScore);
    }

    public ProfileMatcher(ScoringWeights weights, int minScore) {
        this.weights = weights;
        this.minScore = Math.max(0, minScore);
    }

    /**
     * Matches a job against a single profile.
     *
     * @return a {@link JobMatch} if the job qualifies, otherwise {@link Optional#empty()}
     */
    public Optional<JobMatch> match(Job job, Profile profile) {
        if (job == null || profile == null) {
            return Optional.empty();
        }
        String title = job.title();
        String description = job.description();

        // Exclusion disqualifies regardless of score.
        for (String ex : profile.excludeKeywords()) {
            if (containsWord(title, ex) || containsWord(description, ex)) {
                return Optional.empty();
            }
        }

        LinkedHashSet<String> matchedOn = new LinkedHashSet<>();
        int score = 0;

        for (String kw : profile.includeTitleKeywords()) {
            if (containsWord(title, kw)) {
                score += weights.titleInclude();
                matchedOn.add(kw.trim().toLowerCase());
            }
        }
        for (String kw : profile.includeDescriptionKeywords()) {
            if (containsWord(description, kw)) {
                score += weights.descriptionInclude();
                matchedOn.add(kw.trim().toLowerCase());
            }
        }

        if (matchedOn.isEmpty()) {
            return Optional.empty(); // no include keywords matched -> not a match
        }

        if (job.remote()) {
            score += weights.remoteBonus();
        }
        if (score < minScore) {
            return Optional.empty(); // below similarity threshold -> not relevant
        }
        return Optional.of(new JobMatch(job, score, List.copyOf(matchedOn), profile.name()));
    }

    /**
     * Matches a job against several profiles and returns the highest-scoring match, if any.
     * Ties keep the first profile in the list (deterministic).
     */
    public Optional<JobMatch> bestMatch(Job job, List<Profile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return Optional.empty();
        }
        JobMatch best = null;
        for (Profile profile : profiles) {
            Optional<JobMatch> match = match(job, profile);
            if (match.isPresent() && (best == null || match.get().score() > best.score())) {
                best = match.get();
            }
        }
        return Optional.ofNullable(best);
    }

    /** Case-insensitive whole-word (word-boundary) match; handles multi-word phrases. */
    private static boolean containsWord(String text, String keyword) {
        if (text == null || keyword == null) {
            return false;
        }
        String kw = keyword.trim();
        if (kw.isEmpty()) {
            return false;
        }
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(kw.toLowerCase()) + "\\b");
        Matcher matcher = pattern.matcher(text.toLowerCase());
        return matcher.find();
    }
}

