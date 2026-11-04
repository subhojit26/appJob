package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.config.AppProperties;
import com.JobHunt.appJob.http.HttpJobFetcher;
import com.JobHunt.appJob.source.AshbyJobSource;
import com.JobHunt.appJob.source.GreenhouseJobSource;
import com.JobHunt.appJob.source.JobSource;
import com.JobHunt.appJob.source.LeverJobSource;
import com.JobHunt.appJob.store.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;

/**
 * Dynamic company-board discovery (Phase 6 / FR-1b, §7.10).
 *
 * <p>Candidate company slugs come from (a) a bundled seed resource ({@code company-seed.txt}) and
 * (b) optional Adzuna-harvested company names (preference-driven). Each candidate is probed against
 * the Greenhouse, Lever, and Ashby board endpoints by reusing the real source parsers; any board
 * that returns ≥1 posting is persisted to the {@code companies} table. The fetch pipeline then reads
 * the roster from the DB instead of from hardcoded config.</p>
 */
@Service
public class CompanyDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(CompanyDiscoveryService.class);
    private static final String SEED_RESOURCE = "company-seed.txt";

    /**
     * Max slugs probed concurrently. Each slug probes 3 ATS endpoints, each buffering a JSON
     * response; an unbounded fan-out over the seed list spikes heap and OOM-kills the JVM on
     * small containers (observed as 502s mid-reload). Bounding keeps peak memory flat.
     * Override via {@code JOBWATCH_FETCH_CONCURRENCY}.
     */
    private static final int MAX_CONCURRENT_PROBES = readConcurrencyLimit();

    private static int readConcurrencyLimit() {
        String raw = System.getenv("JOBWATCH_FETCH_CONCURRENCY");
        if (raw != null && !raw.isBlank()) {
            try {
                int v = Integer.parseInt(raw.trim());
                if (v > 0) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return 6;
    }

    private final CompanyRepository companyRepository;
    private final HttpJobFetcher fetcher;
    private final AppProperties.DiscoveryConfig config;

    public CompanyDiscoveryService(CompanyRepository companyRepository,
                                   HttpJobFetcher fetcher,
                                   AppProperties appProperties) {
        this.companyRepository = companyRepository;
        this.fetcher = fetcher;
        this.config = appProperties.getDiscovery();
    }

    /**
     * Runs discovery only when the roster is empty (bootstrap). Keeps normal 30-minute runs fast;
     * the roster can be re-seeded by clearing the {@code companies} table.
     *
     * @param harvestedCompanyNames company display names harvested from this run's Adzuna results
     *                              (may be empty); used as additional candidates when enabled
     */
    public void discoverIfNeeded(List<String> harvestedCompanyNames) {
        if (!config.isEnabled()) {
            return;
        }
        if (!companyRepository.isEmpty()) {
            return; // already bootstrapped
        }
        discover(harvestedCompanyNames);
    }

    /** Probes all candidate slugs and persists live boards. */
    public void discover(List<String> harvestedCompanyNames) {
        Set<String> candidates = new LinkedHashSet<>(loadSeed());
        if (config.isHarvestFromAdzuna() && harvestedCompanyNames != null) {
            for (String name : harvestedCompanyNames) {
                candidates.addAll(slugCandidates(name));
            }
        }

        List<String> capped = candidates.stream().limit(config.getMaxProbesPerRun()).toList();
        log.info("Company discovery: probing {} candidate slug(s) against Greenhouse/Lever/Ashby",
                capped.size());

        Instant now = Instant.now();
        int discovered = 0;
        // Bound how many slugs probe concurrently to keep peak memory flat on small containers.
        Semaphore gate = new Semaphore(Math.max(1, MAX_CONCURRENT_PROBES));
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<int[]>> futures = new ArrayList<>();
            for (String slug : capped) {
                futures.add(pool.submit(probeAll(slug, now, gate)));
            }
            for (Future<int[]> f : futures) {
                try {
                    discovered += f.get()[0];
                } catch (Exception e) {
                    log.debug("probe task failed: {}", e.toString());
                }
            }
        }
        log.info("Company discovery complete: {} live board(s) persisted", discovered);
    }

    /** Returns a task that probes one slug across all three ATS; result[0] = boards found. */
    private Callable<int[]> probeAll(String slug, Instant now, Semaphore gate) {
        return () -> {
            gate.acquire();
            try {
                int found = 0;
                found += probe(GreenhouseJobSource.SOURCE, slug, now,
                        (s, f) -> new GreenhouseJobSource(s, f));
                found += probe(LeverJobSource.SOURCE, slug, now,
                        (s, f) -> new LeverJobSource(s, s, f));
                found += probe(AshbyJobSource.SOURCE, slug, now,
                        (s, f) -> new AshbyJobSource(s, s, f));
                return new int[]{found};
            } finally {
                gate.release();
            }
        };
    }

    /** Probes a single ATS for one slug; persists on success. Returns 1 if live, else 0. */
    private int probe(String source, String slug, Instant now,
                      BiFunction<String, HttpJobFetcher, JobSource> factory) {
        try {
            JobSource probeSource = factory.apply(slug, fetcher);
            List<?> jobs = probeSource.fetchJobs();
            if (jobs != null && !jobs.isEmpty()) {
                companyRepository.upsertDiscovered(source, slug, slug, now);
                log.debug("discovered {} board: {}", source, slug);
                return 1;
            }
        } catch (Exception e) {
            // 404 / network / parse failure → not a live board for this ATS. Expected for most.
            log.trace("no {} board for '{}': {}", source, slug, e.toString());
        }
        return 0;
    }

    /** Loads candidate slugs from the bundled seed resource. */
    private List<String> loadSeed() {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource(SEED_RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String s = line.trim();
                if (!s.isEmpty() && !s.startsWith("#")) {
                    out.add(s.toLowerCase());
                }
            }
        } catch (Exception e) {
            log.warn("Could not read company seed resource '{}': {}", SEED_RESOURCE, e.toString());
        }
        return out;
    }

    /**
     * Derives candidate slugs from a company display name. ATS slugs are usually the short brand,
     * so we try both the first word and the full alphanumeric concatenation.
     */
    static Set<String> slugCandidates(String companyName) {
        Set<String> out = new LinkedHashSet<>();
        if (companyName == null || companyName.isBlank()) {
            return out;
        }
        String lower = companyName.toLowerCase().strip();
        String firstWord = lower.split("[^a-z0-9]+")[0];
        if (firstWord.length() >= 2) {
            out.add(firstWord);
        }
        String concat = lower.replaceAll("[^a-z0-9]", "");
        if (concat.length() >= 2 && concat.length() <= 30) {
            out.add(concat);
        }
        return out;
    }
}

