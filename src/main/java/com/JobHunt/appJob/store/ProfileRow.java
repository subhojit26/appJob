package com.JobHunt.appJob.store;

import com.JobHunt.appJob.filter.Profile;

import java.util.List;

/**
 * A user-editable role profile (DB-backed; Phase 10 increment).
 *
 * @param id           surrogate key (null before insert)
 * @param name         unique profile name
 * @param includeTitle role/title keywords
 * @param stack        skill/stack keywords (matched against the description)
 * @param excludeTitle disqualifying keywords
 * @param companies    preferred companies (empty = any)
 * @param expMin       desired minimum years (nullable)
 * @param expMax       desired maximum years (nullable)
 * @param enabled      whether the pipeline uses this profile
 */
public record ProfileRow(
        Long id,
        String name,
        List<String> includeTitle,
        List<String> stack,
        List<String> excludeTitle,
        List<String> companies,
        Integer expMin,
        Integer expMax,
        boolean enabled
) {
    public ProfileRow {
        includeTitle = includeTitle == null ? List.of() : List.copyOf(includeTitle);
        stack = stack == null ? List.of() : List.copyOf(stack);
        excludeTitle = excludeTitle == null ? List.of() : List.copyOf(excludeTitle);
        companies = companies == null ? List.of() : List.copyOf(companies);
    }

    /** Domain {@link Profile} used by the matcher (title/stack/exclude only). */
    public Profile toProfile() {
        return new Profile(name, includeTitle, stack, excludeTitle);
    }
}

