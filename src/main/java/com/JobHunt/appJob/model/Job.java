package com.JobHunt.appJob.model;

import java.time.Instant;
import java.util.Set;

/**
 * Normalized, source-agnostic representation of a job posting.
 *
 * <p>Every {@code JobSource} (Greenhouse, Lever, Ashby, Adzuna, ...) converts its
 * provider-specific JSON into this single shape so the rest of JobWatch never has
 * to care where a job came from.</p>
 *
 * <p>Fields that a given provider does not expose should be left {@code null}
 * (or {@code false} for {@link #remote}). Do not guess or fabricate values.</p>
 *
 * @param source             logical source name, e.g. "greenhouse", "lever", "ashby", "adzuna"
 * @param sourceJobId        provider's unique id for the posting (used for exact dedupe)
 * @param title              job title, stored as-is (case preserved)
 * @param company            company / organization name
 * @param location           raw human-readable location string
 * @param remote             true if the role is remote
 * @param workplaceType      provider workplace type (e.g. "remote", "hybrid", "onsite"); nullable
 * @param applyUrl           direct apply URL; nullable
 * @param jobUrl             canonical job posting URL; nullable
 * @param description        plain-text (HTML-stripped) description; nullable
 * @param postedAt           trustworthy posted timestamp, or {@code null} if unknown
 * @param minExperienceYears extracted minimum experience in years; nullable
 * @param maxExperienceYears extracted maximum experience in years; nullable
 * @param salaryMin          minimum salary if structured/clearly stated; nullable
 * @param salaryMax          maximum salary if structured/clearly stated; nullable
 * @param salaryCurrency     ISO currency code for the salary values; nullable
 * @param salaryEstimated    true if the salary is predicted/estimated (e.g. Adzuna) rather
 *                           than employer-provided; keeps salary provenance honest
 * @param employmentType     employment type (e.g. "full-time", "contract"); nullable
 * @param fetchedAt          instant this job was fetched by the pipeline
 */
public record Job(
        String source,
        String sourceJobId,
        String title,
        String company,
        String location,
        boolean remote,
        String workplaceType,
        String applyUrl,
        String jobUrl,
        String description,
        Instant postedAt,
        Integer minExperienceYears,
        Integer maxExperienceYears,
        Double salaryMin,
        Double salaryMax,
        String salaryCurrency,
        boolean salaryEstimated,
        String employmentType,
        Instant fetchedAt,
        Set<String> stack
) {
    /** Normalizes {@code stack} to an immutable, non-null set. */
    public Job {
        stack = (stack == null) ? Set.of() : Set.copyOf(stack);
    }
    /**
     * Returns a new {@link Builder} for assembling a {@link Job}.
     *
     * <p>A builder keeps parser code readable given the large field count and lets
     * each source set only the fields its API actually provides.</p>
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link Job}. Unset object fields default to {@code null}. */
    public static final class Builder {
        private String source;
        private String sourceJobId;
        private String title;
        private String company;
        private String location;
        private boolean remote;
        private String workplaceType;
        private String applyUrl;
        private String jobUrl;
        private String description;
        private Instant postedAt;
        private Integer minExperienceYears;
        private Integer maxExperienceYears;
        private Double salaryMin;
        private Double salaryMax;
        private String salaryCurrency;
        private boolean salaryEstimated;
        private String employmentType;
        private Instant fetchedAt;
        private Set<String> stack;

        public Builder source(String source) { this.source = source; return this; }
        public Builder sourceJobId(String sourceJobId) { this.sourceJobId = sourceJobId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder company(String company) { this.company = company; return this; }
        public Builder location(String location) { this.location = location; return this; }
        public Builder remote(boolean remote) { this.remote = remote; return this; }
        public Builder workplaceType(String workplaceType) { this.workplaceType = workplaceType; return this; }
        public Builder applyUrl(String applyUrl) { this.applyUrl = applyUrl; return this; }
        public Builder jobUrl(String jobUrl) { this.jobUrl = jobUrl; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder postedAt(Instant postedAt) { this.postedAt = postedAt; return this; }
        public Builder minExperienceYears(Integer minExperienceYears) { this.minExperienceYears = minExperienceYears; return this; }
        public Builder maxExperienceYears(Integer maxExperienceYears) { this.maxExperienceYears = maxExperienceYears; return this; }
        public Builder salaryMin(Double salaryMin) { this.salaryMin = salaryMin; return this; }
        public Builder salaryMax(Double salaryMax) { this.salaryMax = salaryMax; return this; }
        public Builder salaryCurrency(String salaryCurrency) { this.salaryCurrency = salaryCurrency; return this; }
        public Builder salaryEstimated(boolean salaryEstimated) { this.salaryEstimated = salaryEstimated; return this; }
        public Builder employmentType(String employmentType) { this.employmentType = employmentType; return this; }
        public Builder fetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; return this; }
        public Builder stack(Set<String> stack) { this.stack = stack; return this; }

        public Job build() {
            return new Job(
                    source, sourceJobId, title, company, location, remote, workplaceType,
                    applyUrl, jobUrl, description, postedAt, minExperienceYears, maxExperienceYears,
                    salaryMin, salaryMax, salaryCurrency, salaryEstimated, employmentType, fetchedAt,
                    stack
            );
        }
    }
}

