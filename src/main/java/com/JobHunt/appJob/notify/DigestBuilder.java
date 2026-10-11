package com.JobHunt.appJob.notify;

import com.JobHunt.appJob.config.AppProperties;
import com.JobHunt.appJob.filter.JobMatch;
import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.util.TimeAgo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds HTML and plain-text digest emails from a list of matched jobs (PRD 7.7).
 *
 * <p>Sort order: profile → score descending → recency descending.
 * Cap enforced from {@code jobwatch.digest.max-jobs} (default 50).</p>
 */
@Service
public class DigestBuilder {

    private final ITemplateEngine templateEngine;
    private final int maxJobs;

    /** Production constructor: cap driven by {@link AppProperties}. */
    @Autowired
    public DigestBuilder(ITemplateEngine templateEngine, AppProperties appProperties) {
        this.templateEngine = templateEngine;
        this.maxJobs = appProperties.getDigest().getMaxJobs();
    }

    /** Test/stub constructor: explicit cap, no Spring config needed. */
    public DigestBuilder(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        this.maxJobs = 50;
    }

    /**
     * Builds a digest email. Returns {@code null} when the list is empty
     * (caller should suppress the send — PRD FR-27).
     */
    public DigestEmail build(List<JobMatch> jobMatches, String sourceHealthLine) {
        if (jobMatches == null || jobMatches.isEmpty()) {
            return null;
        }

        List<JobMatch> sorted = jobMatches.stream()
                .sorted(Comparator
                        .comparing((JobMatch jm) -> jm.profileName() != null ? jm.profileName() : "")
                        .thenComparingInt((JobMatch jm) -> -(jm.score()))
                        .thenComparing((JobMatch jm) -> jm.job().postedAt() != null
                                ? jm.job().postedAt()
                                : jm.job().fetchedAt(), Comparator.reverseOrder()))
                .collect(Collectors.toList());

        Map<String, Integer> jobsByProfile = sorted.stream()
                .collect(Collectors.groupingBy(
                        jm -> jm.profileName() != null ? jm.profileName() : "unmatched",
                        Collectors.summingInt(jm -> 1)));

        List<JobMatch> capped = sorted.stream().limit(maxJobs).collect(Collectors.toList());
        boolean isCapped = sorted.size() > maxJobs;
        List<Job> cappedJobs = capped.stream().map(JobMatch::job).collect(Collectors.toList());

        String subject  = buildSubject(jobsByProfile, cappedJobs);
        String htmlBody = renderTemplate("digest.html", cappedJobs, isCapped, sorted.size(), sourceHealthLine);
        String textBody = renderTemplate("digest.txt",  cappedJobs, isCapped, sorted.size(), sourceHealthLine);

        return DigestEmail.of(subject, htmlBody, textBody, cappedJobs, jobsByProfile, sourceHealthLine);
    }

    private String buildSubject(Map<String, Integer> jobsByProfile, List<Job> jobs) {
        int total = jobsByProfile.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder sb = new StringBuilder(total + " new job" + (total == 1 ? "" : "s"));
        if (!jobsByProfile.isEmpty()) {
            sb.append(": ");
            sb.append(jobsByProfile.entrySet().stream()
                    .map(e -> e.getValue() + " " + e.getKey())
                    .collect(Collectors.joining(", ")));
        }
        jobs.stream()
                .map(j -> j.postedAt() != null ? j.postedAt() : j.fetchedAt())
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .ifPresent(t -> sb.append(" (freshest ").append(TimeAgo.ago(t)).append(")"));
        return sb.toString();
    }

    private String renderTemplate(String name, List<Job> jobs, boolean isCapped,
                                  int total, String healthLine) {
        Context ctx = new Context();
        ctx.setVariable("jobs", jobs);
        ctx.setVariable("isCapped", isCapped);
        ctx.setVariable("totalJobs", total);
        ctx.setVariable("healthLine", healthLine != null ? healthLine : "");
        return templateEngine.process(name, ctx);
    }
}


