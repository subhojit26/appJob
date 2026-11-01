package com.JobHunt.appJob.filter;

import java.util.List;

/**
 * Configuration for {@link JobFilter} global (eligibility) filtering.
 *
 * <p>All rules are opt-in: an empty/blank setting means "no restriction" so an empty profile
 * never accidentally rejects everything. Missing job data is treated conservatively as
 * <em>not disqualifying</em> (e.g. a job with unknown experience passes the experience filter).</p>
 *
 * @param allowedLocations   location tokens a non-remote job must contain (case-insensitive);
 *                           empty means any location is allowed
 * @param allowRemote        whether remote jobs are eligible
 * @param minExperienceYears lower bound of your target experience range; {@code null} = unbounded
 * @param maxExperienceYears upper bound of your target experience range; {@code null} = unbounded
 * @param companyBlocklist   company substrings to reject (case-insensitive)
 * @param titleBlocklist     title substrings to reject (case-insensitive)
 */
public record FilterProfile(
        List<String> allowedLocations,
        boolean allowRemote,
        Integer minExperienceYears,
        Integer maxExperienceYears,
        List<String> companyBlocklist,
        List<String> titleBlocklist
) {
    public FilterProfile {
        allowedLocations = allowedLocations == null ? List.of() : List.copyOf(allowedLocations);
        companyBlocklist = companyBlocklist == null ? List.of() : List.copyOf(companyBlocklist);
        titleBlocklist = titleBlocklist == null ? List.of() : List.copyOf(titleBlocklist);
    }

    /** A profile that rejects nothing: no location/experience/blocklist limits, remote allowed. */
    public static FilterProfile permissive() {
        return new FilterProfile(List.of(), true, null, null, List.of(), List.of());
    }
}

