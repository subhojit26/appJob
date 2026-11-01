package com.JobHunt.appJob.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for email sending (from/to addresses, healthcheck URL, digest settings).
 */
@ConfigurationProperties(prefix = "jobwatch.email")
public record EmailProperties(
        String from,
        String to
) {
    public EmailProperties {
        // Defensive copying and normalization can happen here if needed
    }
}

