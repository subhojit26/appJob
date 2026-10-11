package com.JobHunt.appJob.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for healthchecks.io pinging.
 */
@ConfigurationProperties(prefix = "jobwatch.healthcheck")
public record HealthcheckProperties(
        String url
) {
    public HealthcheckProperties {
        // Validation/normalization can happen here
    }
}

