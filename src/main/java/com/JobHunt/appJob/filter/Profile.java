package com.JobHunt.appJob.filter;

import java.util.List;

/**
 * A role profile for {@link ProfileMatcher}.
 *
 * @param name                       profile name (e.g. "backend-java")
 * @param includeTitleKeywords       keywords matched against the job title
 * @param includeDescriptionKeywords keywords matched against the job description (stack/skills)
 * @param excludeKeywords            if any is found in title or description, the job does not match
 */
public record Profile(
        String name,
        List<String> includeTitleKeywords,
        List<String> includeDescriptionKeywords,
        List<String> excludeKeywords
) {
    public Profile {
        includeTitleKeywords = includeTitleKeywords == null ? List.of() : List.copyOf(includeTitleKeywords);
        includeDescriptionKeywords = includeDescriptionKeywords == null ? List.of() : List.copyOf(includeDescriptionKeywords);
        excludeKeywords = excludeKeywords == null ? List.of() : List.copyOf(excludeKeywords);
    }
}

