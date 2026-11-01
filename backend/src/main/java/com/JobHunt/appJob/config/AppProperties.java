package com.JobHunt.appJob.config;

import com.JobHunt.appJob.filter.FilterProfile;
import com.JobHunt.appJob.filter.Profile;
import com.JobHunt.appJob.filter.ScoringWeights;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Central typed configuration boundary for JobWatch (PRD 6.5).
 *
 * <h2>Design principle</h2>
 * <pre>
 *   Sources  = WHERE JobWatch finds jobs (Greenhouse/Lever/Ashby boards, Adzuna API)
 *   Profiles = WHAT the user wants (roles, skills, company types, location, experience)
 * </pre>
 *
 * <p><strong>JavaBean pattern used throughout</strong> — Spring Boot 4's
 * {@code @ConfigurationProperties} records binder has a known limitation where only
 * the first {@code List<String>} field in a record is populated from YAML when there
 * are multiple list fields. All configurable sub-components use mutable classes with
 * getters/setters to avoid this.</p>
 */
@ConfigurationProperties(prefix = "jobwatch")
public class AppProperties {

    private FiltersConfig filters = new FiltersConfig();
    private List<ProfileConfig> profiles = new ArrayList<>();
    private SourcesConfig sources = new SourcesConfig();
    private DigestConfig digest = new DigestConfig();
    private ScheduleConfig schedule = new ScheduleConfig();
    private DiscoveryConfig discovery = new DiscoveryConfig();

    /** Freshness window (days): only jobs this recent are emailed/shown. Default 3. */
    private int freshnessDays = 3;

    /**
     * Minimum profile-match score a job must reach to be shown/emailed (similarity threshold).
     * Jobs scoring below this are treated as not relevant to the active profile(s). Default 5
     * (one title keyword hit). Configurable via {@code jobwatch.match-min-score}.
     */
    private int matchMinScore = 5;

    public int getFreshnessDays() { return freshnessDays; }
    public void setFreshnessDays(int v) { this.freshnessDays = v; }

    public int getMatchMinScore() { return matchMinScore; }
    public void setMatchMinScore(int v) { this.matchMinScore = v; }

    public FiltersConfig getFilters() { return filters; }
    public void setFilters(FiltersConfig v) { this.filters = v; }
    public List<ProfileConfig> getProfiles() { return profiles; }
    public void setProfiles(List<ProfileConfig> v) { this.profiles = v == null ? List.of() : v; }
    public SourcesConfig getSources() { return sources; }
    public void setSources(SourcesConfig v) { this.sources = v; }
    public DigestConfig getDigest() { return digest; }
    public void setDigest(DigestConfig v) { this.digest = v; }
    public ScheduleConfig getSchedule() { return schedule; }
    public void setSchedule(ScheduleConfig v) { this.schedule = v; }
    public DiscoveryConfig getDiscovery() { return discovery; }
    public void setDiscovery(DiscoveryConfig v) { this.discovery = v; }

    /** All profiles as domain {@link Profile} objects. */
    public List<Profile> toProfiles() {
        return profiles.stream().map(ProfileConfig::toProfile).toList();
    }

    // ── global eligibility filters ────────────────────────────────────────────

    public static class FiltersConfig {
        private List<String> locations = new ArrayList<>();
        private boolean allowRemoteWorldwide = true;
        private Integer expMin;
        private Integer expMax;
        private List<String> blockedCompanies = new ArrayList<>();
        private List<String> blockedTitleWords = new ArrayList<>();

        public List<String> getLocations()               { return locations; }
        public void setLocations(List<String> v)         { this.locations = v == null ? List.of() : v; }
        public boolean isAllowRemoteWorldwide()           { return allowRemoteWorldwide; }
        public void setAllowRemoteWorldwide(boolean v)   { this.allowRemoteWorldwide = v; }
        public Integer getExpMin()                        { return expMin; }
        public void setExpMin(Integer v)                  { this.expMin = v; }
        public Integer getExpMax()                        { return expMax; }
        public void setExpMax(Integer v)                  { this.expMax = v; }
        public List<String> getBlockedCompanies()         { return blockedCompanies; }
        public void setBlockedCompanies(List<String> v)   { this.blockedCompanies = v == null ? List.of() : v; }
        public List<String> getBlockedTitleWords()        { return blockedTitleWords; }
        public void setBlockedTitleWords(List<String> v)  { this.blockedTitleWords = v == null ? List.of() : v; }

        public FilterProfile toFilterProfile() {
            return new FilterProfile(locations, allowRemoteWorldwide, expMin, expMax,
                    blockedCompanies, blockedTitleWords);
        }
    }

    // ── user preference profiles ──────────────────────────────────────────────

    /**
     * A role/preference profile.
     * Defines WHAT the user wants — roles, skills, company types, location, experience.
     */
    public static class ProfileConfig {
        private String name;
        private List<String> includeTitle    = new ArrayList<>();
        private List<String> stack           = new ArrayList<>();
        private List<String> excludeTitle    = new ArrayList<>();
        private List<String> companyTypes    = new ArrayList<>();
        private List<String> locations       = new ArrayList<>();
        private Integer expMin;
        private Integer expMax;
        private int titleWeight   = 5;
        private int descWeight    = 2;
        private int remoteWeight  = 1;

        public String getName()                           { return name; }
        public void setName(String v)                     { this.name = v; }
        public List<String> getIncludeTitle()             { return includeTitle; }
        public void setIncludeTitle(List<String> v)       { this.includeTitle = v == null ? List.of() : v; }
        public List<String> getStack()                    { return stack; }
        public void setStack(List<String> v)              { this.stack = v == null ? List.of() : v; }
        public List<String> getExcludeTitle()             { return excludeTitle; }
        public void setExcludeTitle(List<String> v)       { this.excludeTitle = v == null ? List.of() : v; }
        public List<String> getCompanyTypes()             { return companyTypes; }
        public void setCompanyTypes(List<String> v)       { this.companyTypes = v == null ? List.of() : v; }
        public List<String> getLocations()                { return locations; }
        public void setLocations(List<String> v)          { this.locations = v == null ? List.of() : v; }
        public Integer getExpMin()                        { return expMin; }
        public void setExpMin(Integer v)                  { this.expMin = v; }
        public Integer getExpMax()                        { return expMax; }
        public void setExpMax(Integer v)                  { this.expMax = v; }
        public int getTitleWeight()                       { return titleWeight; }
        public void setTitleWeight(int v)                 { this.titleWeight = v; }
        public int getDescWeight()                        { return descWeight; }
        public void setDescWeight(int v)                  { this.descWeight = v; }
        public int getRemoteWeight()                      { return remoteWeight; }
        public void setRemoteWeight(int v)                { this.remoteWeight = v; }

        public Profile toProfile() {
            return new Profile(name, includeTitle, stack, excludeTitle);
        }

        public ScoringWeights toScoringWeights() {
            return new ScoringWeights(titleWeight, descWeight, remoteWeight);
        }

        /** Effective {@link FilterProfile}: per-profile overrides global when set. */
        public FilterProfile toFilterProfile(FiltersConfig globalFilters) {
            List<String> locs = (locations == null || locations.isEmpty())
                    ? globalFilters.getLocations() : locations;
            Integer eMin = expMin != null ? expMin : globalFilters.getExpMin();
            Integer eMax = expMax != null ? expMax : globalFilters.getExpMax();
            return new FilterProfile(locs, globalFilters.isAllowRemoteWorldwide(), eMin, eMax,
                    globalFilters.getBlockedCompanies(), globalFilters.getBlockedTitleWords());
        }
    }

    // ── sources ───────────────────────────────────────────────────────────────

    public static class SourcesConfig {
        private AtsSourceConfig greenhouse = new AtsSourceConfig();
        private AtsSourceConfig lever      = new AtsSourceConfig();
        private AtsSourceConfig ashby      = new AtsSourceConfig();
        private AdzunaConfig    adzuna     = new AdzunaConfig();

        public AtsSourceConfig getGreenhouse() { return greenhouse; }
        public void setGreenhouse(AtsSourceConfig v) { this.greenhouse = v; }
        public AtsSourceConfig getLever() { return lever; }
        public void setLever(AtsSourceConfig v) { this.lever = v; }
        public AtsSourceConfig getAshby() { return ashby; }
        public void setAshby(AtsSourceConfig v) { this.ashby = v; }
        public AdzunaConfig getAdzuna() { return adzuna; }
        public void setAdzuna(AdzunaConfig v) { this.adzuna = v; }
    }

    public static class AtsSourceConfig {
        private boolean enabled = true;
        private List<String> companies = new ArrayList<>();

        public boolean isEnabled()               { return enabled; }
        public void setEnabled(boolean v)        { this.enabled = v; }
        public List<String> getCompanies()       { return companies; }
        public void setCompanies(List<String> v) { this.companies = v == null ? List.of() : v; }

        public List<CompanyEntry> companyEntries() {
            return companies.stream().map(CompanyEntry::of).toList();
        }
    }

    public static class AdzunaConfig {
        private boolean enabled = true;
        private String  country = "in";
        private List<String> queries = new ArrayList<>();
        private int resultsPerPage = 50;
        private int maxDaysOld = 2;

        public boolean isEnabled()             { return enabled; }
        public void setEnabled(boolean v)      { this.enabled = v; }
        public String getCountry()             { return country; }
        public void setCountry(String v)       { this.country = v; }
        public List<String> getQueries()       { return queries; }
        public void setQueries(List<String> v) { this.queries = v == null ? List.of() : v; }
        public int getResultsPerPage()         { return resultsPerPage; }
        public void setResultsPerPage(int v)   { this.resultsPerPage = v; }
        public int getMaxDaysOld()             { return maxDaysOld; }
        public void setMaxDaysOld(int v)       { this.maxDaysOld = v; }
    }

    /** A company board entry: slug and optional display-name override. */
    public record CompanyEntry(String slug, String company) {
        public CompanyEntry {
            if (company == null || company.isBlank()) company = slug;
        }
        public static CompanyEntry of(String slug) { return new CompanyEntry(slug, slug); }
    }

    // ── digest ────────────────────────────────────────────────────────────────

    public static class DigestConfig {
        private int maxJobs = 50;
        public int getMaxJobs()     { return maxJobs; }
        public void setMaxJobs(int v) { this.maxJobs = v; }
    }

    // ── schedule ─────────────────────────────────────────────────────────────

    public static class ScheduleConfig {
        private String cron = "0 */30 * * * *";
        public String getCron()       { return cron; }
        public void setCron(String v) { this.cron = v; }
    }

    // ── company discovery (Phase 6 / FR-1b) ───────────────────────────────────

    /**
     * Controls dynamic company-board discovery that replaces hardcoded slugs.
     * Candidate company names are harvested from Adzuna results (preference-driven) and an
     * optional bundled seed resource; live boards are persisted to the {@code companies} table.
     */
    public static class DiscoveryConfig {
        private boolean enabled = true;
        /** Also probe companies harvested from Adzuna job results. */
        private boolean harvestFromAdzuna = true;
        /** Consecutive empty/404 probes before a board is disabled. */
        private int pruneAfter404 = 3;
        /** Max candidate companies probed per run (politeness / rate-limit guard). */
        private int maxProbesPerRun = 60;

        public boolean isEnabled()                 { return enabled; }
        public void setEnabled(boolean v)          { this.enabled = v; }
        public boolean isHarvestFromAdzuna()       { return harvestFromAdzuna; }
        public void setHarvestFromAdzuna(boolean v){ this.harvestFromAdzuna = v; }
        public int getPruneAfter404()              { return pruneAfter404; }
        public void setPruneAfter404(int v)        { this.pruneAfter404 = v; }
        public int getMaxProbesPerRun()            { return maxProbesPerRun; }
        public void setMaxProbesPerRun(int v)      { this.maxProbesPerRun = v; }
    }
}
