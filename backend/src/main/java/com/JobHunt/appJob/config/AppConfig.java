package com.JobHunt.appJob.config;

import com.JobHunt.appJob.dedupe.JobDeduplicator;
import com.JobHunt.appJob.orchestration.RunOrchestrator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuration for infrastructure and pipeline components that are plain Java classes
 * (not annotated with {@code @Component}).
 */
@Configuration
public class AppConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    public RunOrchestrator runOrchestrator() {
        return new RunOrchestrator();
    }

    @Bean
    public JobDeduplicator jobDeduplicator() {
        return new JobDeduplicator();
    }
}

