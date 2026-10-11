package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.config.AppProperties;
import com.JobHunt.appJob.dedupe.JobDeduplicator;
import com.JobHunt.appJob.filter.JobFilter;
import com.JobHunt.appJob.filter.JobMatch;
import com.JobHunt.appJob.filter.Profile;
import com.JobHunt.appJob.filter.ProfileMatcher;
import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.orchestration.RunOrchestrator;
import com.JobHunt.appJob.orchestration.RunResult;
import com.JobHunt.appJob.orchestration.SourceStats;
import com.JobHunt.appJob.source.JobSource;
import com.JobHunt.appJob.store.JobRepository;
import com.JobHunt.appJob.store.SourceRun;
import com.JobHunt.appJob.store.SourceRunRepository;
import com.JobHunt.appJob.store.StoredJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Wires the full fetch→dedupe→extract→filter→match→persist pipeline for one run.
 *
 * <pre>
 *   sources
 *      ↓  RunOrchestrator (virtual threads, per-source isolation)
 *   List&lt;Job&gt;
 *      ↓  JobDeduplicator (exact + cross-source)
 *      ↓  ExtractionService (experience, stack tags)
 *      ↓  JobFilter (global eligibility)
 *      ↓  ProfileMatcher (best-match profile + score)
 *      ↓  insertIfNew / first-run seed
 *      ↓  SourceRunRepository.save (stats)
 *   RunSummary
 * </pre>
 *
 * <p>Does NOT send email; that is the responsibility of
 * {@link com.JobHunt.appJob.notify.DigestNotificationService}, invoked separately by the
 * scheduler after this method completes.</p>
 */
@Service
public class JobWatchPipeline {

    private static final Logger log = LoggerFactory.getLogger(JobWatchPipeline.class);

    private final RunOrchestrator orchestrator;
    private final JobDeduplicator deduplicator;
    private final ExtractionService extraction;
    private final JobFilter filter;
    private final ProfileMatcher profileMatcher;
    private final List<Profile> profiles;
    private final List<String> stackKeywords;
    private final JobRepository jobRepository;
    private final SourceRunRepository sourceRunRepository;
    private final FirstRunSeeder firstRunSeeder;
    private final List<JobSource> sources;                 // static list (test mode); null in prod
    private final JobSourceProvider sourceProvider;        // dynamic roster (prod); null in tests
    private final CompanyDiscoveryService discoveryService; // nullable (tests)
    private final ProfileProvider profileProvider;          // DB-backed profiles (prod); null in tests

    /** Production constructor: sources come from the discovered roster, with company discovery. */
    @org.springframework.beans.factory.annotation.Autowired
    public JobWatchPipeline(
            RunOrchestrator orchestrator,
            JobDeduplicator deduplicator,
            JobRepository jobRepository,
            SourceRunRepository sourceRunRepository,
            FirstRunSeeder firstRunSeeder,
            JobSourceProvider sourceProvider,
            CompanyDiscoveryService discoveryService,
            ProfileProvider profileProvider,
            AppProperties appProperties) {
        this(orchestrator, deduplicator, jobRepository, sourceRunRepository, firstRunSeeder,
                null, sourceProvider, discoveryService, profileProvider, appProperties);
    }

    /** Test constructor: a fixed list of stub sources, no discovery. */
    public JobWatchPipeline(
            RunOrchestrator orchestrator,
            JobDeduplicator deduplicator,
            JobRepository jobRepository,
            SourceRunRepository sourceRunRepository,
            FirstRunSeeder firstRunSeeder,
            List<JobSource> sources,
            AppProperties appProperties) {
        this(orchestrator, deduplicator, jobRepository, sourceRunRepository, firstRunSeeder,
                sources, null, null, null, appProperties);
    }

    private JobWatchPipeline(
            RunOrchestrator orchestrator,
            JobDeduplicator deduplicator,
            JobRepository jobRepository,
            SourceRunRepository sourceRunRepository,
            FirstRunSeeder firstRunSeeder,
            List<JobSource> sources,
            JobSourceProvider sourceProvider,
            CompanyDiscoveryService discoveryService,
            ProfileProvider profileProvider,
            AppProperties appProperties) {

        this.orchestrator = orchestrator;
        this.deduplicator = deduplicator;
        this.jobRepository = jobRepository;
        this.sourceRunRepository = sourceRunRepository;
        this.firstRunSeeder = firstRunSeeder;
        this.sources = sources;
        this.sourceProvider = sourceProvider;
        this.discoveryService = discoveryService;
        this.profileProvider = profileProvider;
        this.extraction = new ExtractionService();
        this.profileMatcher = new ProfileMatcher(appProperties.getMatchMinScore());

        // Build filter and profile list from typed config
        this.filter = new JobFilter(appProperties.getFilters().toFilterProfile());

        this.profiles = appProperties.toProfiles();

        // Collect all stack keywords across all profiles for extraction
        this.stackKeywords = profiles.stream()
                .flatMap(p -> p.includeDescriptionKeywords().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Executes one full run and returns a summary.
     *
     * @return a {@link RunSummary} describing what happened
     */
    public RunSummary run() {
        Instant runStart = Instant.now();

        // Resolve sources for this run: dynamic DB-backed roster (prod) or static list (tests).
        List<JobSource> runSources = (sourceProvider != null)
                ? sourceProvider.currentSources()
                : sources;
        log.info("Pipeline run started with {} configured source(s)", runSources.size());

        // 1. Fetch concurrently
        RunResult result = orchestrator.run(runSources);

        // 1b. Company discovery (Phase 6): bootstrap the roster from seed + harvested Adzuna names.
        //     Runs only when the roster is empty, so normal runs stay fast.
        if (discoveryService != null) {
            List<String> harvested = result.jobs().stream()
                    .filter(j -> "adzuna".equals(j.source()))
                    .map(Job::company)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            try {
                discoveryService.discoverIfNeeded(harvested);
            } catch (Exception e) {
                log.warn("Company discovery failed (non-fatal): {}", e.toString());
            }
        }

        // 2. Record source run stats immediately
        Instant runEnd = Instant.now();
        for (SourceStats stats : result.sourceStats()) {
            sourceRunRepository.save(SourceRun.fromStats(stats, runStart, runEnd));
            if (!stats.success()) {
                log.warn("[{}] fetch failed: {}", stats.sourceName(),
                        stats.error() != null ? stats.error().getMessage() : "unknown error");
            } else {
                log.info("[{}] fetched {} job(s)", stats.sourceName(), stats.jobsFetched());
            }
        }

        List<Job> allJobs = result.jobs();
        log.info("Total raw jobs fetched: {}", allJobs.size());

        // 3. Deduplicate in-memory (across sources)
        List<Job> deduped = deduplicator.deduplicate(allJobs);
        log.info("After dedupe: {} job(s)", deduped.size());

        // Resolve active profiles for this run: DB-backed (prod) or config (tests).
        List<com.JobHunt.appJob.store.ProfileRow> activeRows =
                (profileProvider != null) ? profileProvider.activeProfiles() : List.of();
        List<Profile> runProfiles;
        List<String> runStackKeywords;
        java.util.Map<String, com.JobHunt.appJob.store.ProfileRow> rowsByName = new java.util.HashMap<>();
        if (!activeRows.isEmpty()) {
            runProfiles = activeRows.stream().map(com.JobHunt.appJob.store.ProfileRow::toProfile).toList();
            runStackKeywords = activeRows.stream()
                    .flatMap(r -> r.stack().stream()).distinct().collect(Collectors.toList());
            activeRows.forEach(r -> rowsByName.put(r.name(), r));
        } else {
            runProfiles = this.profiles;
            runStackKeywords = this.stackKeywords;
        }

        // 4. Enrich with extraction (experience, stack)
        List<Job> enriched = deduped.stream()
                .map(job -> {
                    EnrichedJob e = extraction.enrich(job, runStackKeywords);
                    // Re-build job with extracted experience/stack merged in
                    return Job.builder()
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
                            .minExperienceYears(e.expMin())
                            .maxExperienceYears(e.expMax())
                            .salaryMin(job.salaryMin())
                            .salaryMax(job.salaryMax())
                            .salaryCurrency(job.salaryCurrency())
                            .salaryEstimated(job.salaryEstimated())
                            .employmentType(job.employmentType())
                            .fetchedAt(job.fetchedAt())
                            .stack(e.stack())
                            .build();
                })
                .collect(Collectors.toList());

        // 5. Global filter
        List<Job> eligible = filter.filter(enriched);
        log.info("After global filter: {} eligible job(s)", eligible.size());

        // 6. Profile match — only keep jobs that match at least one profile
        List<JobMatch> matched = eligible.stream()
                .map(job -> profileMatcher.bestMatch(job, runProfiles))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(m -> passesProfileConstraints(m, rowsByName))
                .collect(Collectors.toList());
        log.info("Profile-matched: {} job(s)", matched.size());

        // 7. Persist matched jobs, applying first-run seeding per source
        int newJobs = 0;
        Instant now = Instant.now();
        for (JobMatch m : matched) {
            Job job = m.job();
            String source = job.source();
            StoredJob base = StoredJob.fromJob(job).withMatch(m.profileName(), m.score());

            if (firstRunSeeder.isFirstRun(source)) {
                // First run: seed this job (suppresses old ones, keeps recent ones)
                newJobs += seedSingleJob(base, now);
            } else {
                boolean inserted = jobRepository.insertIfNew(base);
                if (inserted) {
                    newJobs++;
                }
            }
        }

        // Also insert non-matching eligible jobs with notified_at=now (so we don't reprocess)
        // This is required by PRD: "Store non-matching jobs too (matched = 0)"
        for (Job job : eligible) {
            boolean alreadyMatched = matched.stream()
                    .anyMatch(m -> m.job().source().equals(job.source())
                            && m.job().sourceJobId().equals(job.sourceJobId()));
            if (!alreadyMatched) {
                // Insert suppressed: never emailed but prevents re-processing
                StoredJob suppressed = StoredJob.fromJob(job).withNotifiedAt(now);
                jobRepository.insertIfNew(suppressed);
            }
        }

        log.info("Pipeline run complete: {} new job(s) pending notification", newJobs);

        // Build health line for any failing sources
        String healthLine = buildHealthLine(result.sourceStats());

        return new RunSummary(
                result.sourceStats(),
                allJobs.size(),
                deduped.size(),
                eligible.size(),
                matched.size(),
                newJobs,
                healthLine
        );
    }

    /**
     * Applies per-profile constraints (Phase 10 increment): desired experience range and
     * preferred-companies list, taken from the DB-backed profile the job matched.
     *
     * <p>Experience: if the profile sets a range and the job's extracted experience is known,
     * the job must overlap that range. Jobs with unknown experience pass (don't guess).</p>
     * <p>Companies: if the profile lists preferred companies, the job's company must contain one
     * of them (case-insensitive). Empty list = any company.</p>
     */
    private static boolean passesProfileConstraints(
            JobMatch m, java.util.Map<String, com.JobHunt.appJob.store.ProfileRow> rowsByName) {
        com.JobHunt.appJob.store.ProfileRow row = rowsByName.get(m.profileName());
        if (row == null) {
            return true; // config-mode or unknown profile: no extra constraints
        }
        Job job = m.job();

        // Experience overlap (only when the job's experience is known)
        Integer jMin = job.minExperienceYears();
        Integer jMax = job.maxExperienceYears();
        Integer pMin = row.expMin();
        Integer pMax = row.expMax();
        if (pMax != null && jMin != null && jMin > pMax) {
            return false;
        }
        if (pMin != null && jMax != null && jMax < pMin) {
            return false;
        }

        // Preferred companies
        if (!row.companies().isEmpty() && job.company() != null) {
            String company = job.company().toLowerCase();
            boolean hit = row.companies().stream()
                    .anyMatch(c -> company.contains(c.toLowerCase()));
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    /**
     * Seeds a single job, applying the 48h threshold.
     * Returns 1 if the job was inserted as pending (recent), 0 otherwise.
     */
    private int seedSingleJob(StoredJob base, Instant now) {
        boolean recent = base.postedAt() != null
                && java.time.Duration.between(base.postedAt(), now).toHours() <= 48;
        StoredJob stored = base.withNotifiedAt(recent ? null : now);
        boolean inserted = jobRepository.insertIfNew(stored);
        return (inserted && recent) ? 1 : 0;
    }

    private static String buildHealthLine(List<SourceStats> stats) {
        List<String> failures = stats.stream()
                .filter(s -> !s.success())
                .map(s -> s.sourceName())
                .collect(Collectors.toList());
        if (failures.isEmpty()) {
            return null;
        }
        return "⚠️ Failed sources this run: " + String.join(", ", failures);
    }
}

