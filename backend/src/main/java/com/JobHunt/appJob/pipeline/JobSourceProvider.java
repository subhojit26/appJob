package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.config.AppProperties;
import com.JobHunt.appJob.http.HttpJobFetcher;
import com.JobHunt.appJob.source.AdzunaJobSource;
import com.JobHunt.appJob.source.AshbyJobSource;
import com.JobHunt.appJob.source.GreenhouseJobSource;
import com.JobHunt.appJob.source.JobSource;
import com.JobHunt.appJob.source.LeverJobSource;
import com.JobHunt.appJob.store.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the live {@link JobSource} list for each run (Phase 6).
 *
 * <p>ATS sources (Greenhouse/Lever/Ashby) are created one-per-slug from the DB-backed
 * {@code companies} roster — <strong>not</strong> from hardcoded config. Adzuna is created
 * one-per-query from config (it is query/preference-driven and needs no company roster).</p>
 */
@Component
public class JobSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(JobSourceProvider.class);

    private final CompanyRepository companyRepository;
    private final HttpJobFetcher fetcher;
    private final AppProperties props;
    private final String adzunaAppId;
    private final String adzunaAppKey;

    public JobSourceProvider(CompanyRepository companyRepository,
                             HttpJobFetcher fetcher,
                             AppProperties props,
                             @Value("${adzuna.app-id:}") String adzunaAppId,
                             @Value("${adzuna.app-key:}") String adzunaAppKey) {
        this.companyRepository = companyRepository;
        this.fetcher = fetcher;
        this.props = props;
        this.adzunaAppId = adzunaAppId;
        this.adzunaAppKey = adzunaAppKey;
    }

    /** Assembles the current source list from the discovered roster + config queries. */
    public List<JobSource> currentSources() {
        List<JobSource> sources = new ArrayList<>();
        AppProperties.SourcesConfig cfg = props.getSources();

        if (cfg.getGreenhouse().isEnabled()) {
            for (String slug : companyRepository.findEnabledSlugs(GreenhouseJobSource.SOURCE)) {
                sources.add(new GreenhouseJobSource(slug, fetcher));
            }
        }
        if (cfg.getLever().isEnabled()) {
            for (String slug : companyRepository.findEnabledSlugs(LeverJobSource.SOURCE)) {
                sources.add(new LeverJobSource(slug, slug, fetcher));
            }
        }
        if (cfg.getAshby().isEnabled()) {
            for (String slug : companyRepository.findEnabledSlugs(AshbyJobSource.SOURCE)) {
                sources.add(new AshbyJobSource(slug, slug, fetcher));
            }
        }

        AppProperties.AdzunaConfig adzuna = cfg.getAdzuna();
        if (adzuna.isEnabled()) {
            if (adzunaAppId == null || adzunaAppId.isBlank()
                    || adzunaAppKey == null || adzunaAppKey.isBlank()) {
                log.warn("Adzuna enabled but ADZUNA_APP_ID/ADZUNA_APP_KEY not set — skipping Adzuna");
            } else {
                for (String query : adzuna.getQueries()) {
                    sources.add(new AdzunaJobSource(
                            adzuna.getCountry(), query, adzunaAppId, adzunaAppKey, fetcher));
                }
            }
        }

        log.info("Resolved {} source(s) from roster+config: {}", sources.size(),
                sources.stream().map(JobSource::name).distinct().toList());
        return sources;
    }
}

