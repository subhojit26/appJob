package com.JobHunt.appJob.filter;

import com.JobHunt.appJob.model.Job;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure global (eligibility) filter. Filtering decides <em>eligibility</em> only; relevance
 * ordering is handled later by {@link ProfileMatcher}. No Spring/HTTP/DB dependencies.
 *
 * <p>A job is eligible when it passes <strong>all</strong> enabled rules:</p>
 * <ul>
 *   <li><b>Location/remote</b>: remote jobs pass iff {@code allowRemote}; non-remote jobs pass
 *       when {@code allowedLocations} is empty or the location contains an allowed token.</li>
 *   <li><b>Experience</b>: rejected only when the job's stated range is clearly incompatible with
 *       the configured range. Missing experience never rejects.</li>
 *   <li><b>Company blocklist</b> / <b>Title blocklist</b>: rejected if the (present) company/title
 *       contains a blocked substring. Missing company/title never rejects.</li>
 * </ul>
 */
public class JobFilter {

    private final FilterProfile config;

    public JobFilter(FilterProfile config) {
        this.config = config;
    }

    /** Returns only the eligible jobs, preserving input order. */
    public List<Job> filter(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return List.of();
        }
        List<Job> eligible = new ArrayList<>(jobs.size());
        for (Job job : jobs) {
            if (job != null && isEligible(job)) {
                eligible.add(job);
            }
        }
        return eligible;
    }

    /** Whether a single job passes all enabled global filters. */
    public boolean isEligible(Job job) {
        return locationOk(job)
                && experienceOk(job)
                && !companyBlocked(job)
                && !titleBlocked(job);
    }

    private boolean locationOk(Job job) {
        if (job.remote()) {
            return config.allowRemote();
        }
        List<String> allowed = config.allowedLocations();
        if (allowed.isEmpty()) {
            return true; // no location restriction configured
        }
        String location = job.location();
        if (location == null) {
            return false; // restriction configured but location unknown -> cannot confirm
        }
        String loc = location.toLowerCase();
        return allowed.stream().anyMatch(token -> loc.contains(token.toLowerCase()));
    }

    private boolean experienceOk(Job job) {
        Integer cfgMin = config.minExperienceYears();
        Integer cfgMax = config.maxExperienceYears();
        Integer jobMin = job.minExperienceYears();
        Integer jobMax = job.maxExperienceYears();
        // Reject only when the job's range clearly falls outside the configured range.
        if (cfgMax != null && jobMin != null && jobMin > cfgMax) {
            return false;
        }
        if (cfgMin != null && jobMax != null && jobMax < cfgMin) {
            return false;
        }
        return true; // unknown/compatible experience passes
    }

    private boolean companyBlocked(Job job) {
        return containsAny(job.company(), config.companyBlocklist());
    }

    private boolean titleBlocked(Job job) {
        return containsAny(job.title(), config.titleBlocklist());
    }

    private static boolean containsAny(String value, List<String> blocklist) {
        if (value == null || blocklist.isEmpty()) {
            return false;
        }
        String v = value.toLowerCase();
        return blocklist.stream().anyMatch(b -> v.contains(b.toLowerCase()));
    }
}

