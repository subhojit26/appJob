package com.JobHunt.appJob.config;

import com.JobHunt.appJob.http.HttpFetchPolicy;
import com.JobHunt.appJob.http.HttpJobFetcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure beans for the source layer.
 *
 * <p>The actual {@code JobSource} list is assembled per-run by
 * {@link com.JobHunt.appJob.pipeline.JobSourceProvider} from the DB-backed company roster
 * (Phase 6) plus config-driven Adzuna queries — not hardcoded here.</p>
 */
@Configuration
public class SourceBeansConfig {

    /** Shared HTTP helper used by every source and by company discovery. */
    @Bean
    public HttpJobFetcher httpJobFetcher() {
        return new HttpJobFetcher(HttpFetchPolicy.defaults());
    }
}

