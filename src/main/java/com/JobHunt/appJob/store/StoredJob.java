package com.JobHunt.appJob.store;

import com.JobHunt.appJob.model.Job;

import java.time.Instant;
import java.util.Set;

/**
 * A row in the {@code jobs} table: the normalized {@link Job} fields plus notification/audit
 * state ({@code notifiedAt}, {@code createdAt}, {@code updatedAt}).
 *
 * <p>Match metadata (profile/score/matched_on) is intentionally not stored here — matching runs
 * before persistence, so only matched jobs are persisted and the table stays a clean normalized
 * representation.</p>
 */
public record StoredJob(
        Long id,
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
        Instant notifiedAt,
        Instant createdAt,
        Instant updatedAt,
        Set<String> stack,
        String profile,
        Integer score,
        Instant appliedAt
) {
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a copy of this row with {@code notifiedAt} replaced. */
    public StoredJob withNotifiedAt(Instant value) {
        return new StoredJob(id, source, sourceJobId, title, company, location, remote, workplaceType,
                applyUrl, jobUrl, description, postedAt, minExperienceYears, maxExperienceYears,
                salaryMin, salaryMax, salaryCurrency, salaryEstimated, employmentType, fetchedAt,
                value, createdAt, updatedAt, stack, profile, score, appliedAt);
    }

    /** Returns a copy with match metadata (winning profile + score) attached. */
    public StoredJob withMatch(String profileName, Integer matchScore) {
        return new StoredJob(id, source, sourceJobId, title, company, location, remote, workplaceType,
                applyUrl, jobUrl, description, postedAt, minExperienceYears, maxExperienceYears,
                salaryMin, salaryMax, salaryCurrency, salaryEstimated, employmentType, fetchedAt,
                notifiedAt, createdAt, updatedAt, stack, profileName, matchScore, appliedAt);
    }

    /** Converts this DB row back to a normalized {@link Job} for pipeline use. */
    public Job toJob() {        return Job.builder()
                .source(source)
                .sourceJobId(sourceJobId)
                .title(title)
                .company(company)
                .location(location)
                .remote(remote)
                .workplaceType(workplaceType)
                .applyUrl(applyUrl)
                .jobUrl(jobUrl)
                .description(description)
                .postedAt(postedAt)
                .minExperienceYears(minExperienceYears)
                .maxExperienceYears(maxExperienceYears)
                .salaryMin(salaryMin)
                .salaryMax(salaryMax)
                .salaryCurrency(salaryCurrency)
                .salaryEstimated(salaryEstimated)
                .employmentType(employmentType)
                .fetchedAt(fetchedAt)
                .stack(stack)
                .build();
    }

    /** Builds a persistable row from a normalized {@link Job} (not yet notified). */
    public static StoredJob fromJob(Job job) {
        return builder()
                .source(job.source())
                .sourceJobId(job.sourceJobId())
                .title(job.title())
                .company(job.company())
                .location(job.location())
                .remote(job.remote())
                .workplaceType(job.workplaceType())
                .applyUrl(job.applyUrl())
                .jobUrl(job.jobUrl())
                .description(job.description())
                .postedAt(job.postedAt())
                .minExperienceYears(job.minExperienceYears())
                .maxExperienceYears(job.maxExperienceYears())
                .salaryMin(job.salaryMin())
                .salaryMax(job.salaryMax())
                .salaryCurrency(job.salaryCurrency())
                .salaryEstimated(job.salaryEstimated())
                .employmentType(job.employmentType())
                .fetchedAt(job.fetchedAt())
                .stack(job.stack())
                .build();
    }

    /** Fluent builder given the wide column set. */
    public static final class Builder {
        private Long id;
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
        private Instant notifiedAt;
        private Instant createdAt;
        private Instant updatedAt;
        private Set<String> stack;
        private String profile;
        private Integer score;
        private Instant appliedAt;

        public Builder id(Long v) { this.id = v; return this; }
        public Builder source(String v) { this.source = v; return this; }
        public Builder sourceJobId(String v) { this.sourceJobId = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder company(String v) { this.company = v; return this; }
        public Builder location(String v) { this.location = v; return this; }
        public Builder remote(boolean v) { this.remote = v; return this; }
        public Builder workplaceType(String v) { this.workplaceType = v; return this; }
        public Builder applyUrl(String v) { this.applyUrl = v; return this; }
        public Builder jobUrl(String v) { this.jobUrl = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder postedAt(Instant v) { this.postedAt = v; return this; }
        public Builder minExperienceYears(Integer v) { this.minExperienceYears = v; return this; }
        public Builder maxExperienceYears(Integer v) { this.maxExperienceYears = v; return this; }
        public Builder salaryMin(Double v) { this.salaryMin = v; return this; }
        public Builder salaryMax(Double v) { this.salaryMax = v; return this; }
        public Builder salaryCurrency(String v) { this.salaryCurrency = v; return this; }
        public Builder salaryEstimated(boolean v) { this.salaryEstimated = v; return this; }
        public Builder employmentType(String v) { this.employmentType = v; return this; }
        public Builder fetchedAt(Instant v) { this.fetchedAt = v; return this; }
        public Builder notifiedAt(Instant v) { this.notifiedAt = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }
        public Builder stack(Set<String> v) { this.stack = v; return this; }
        public Builder profile(String v) { this.profile = v; return this; }
        public Builder score(Integer v) { this.score = v; return this; }
        public Builder appliedAt(Instant v) { this.appliedAt = v; return this; }

        public StoredJob build() {
            return new StoredJob(id, source, sourceJobId, title, company, location, remote, workplaceType,
                    applyUrl, jobUrl, description, postedAt, minExperienceYears, maxExperienceYears,
                    salaryMin, salaryMax, salaryCurrency, salaryEstimated, employmentType, fetchedAt,
                    notifiedAt, createdAt, updatedAt, stack, profile, score, appliedAt);
        }
    }
}

