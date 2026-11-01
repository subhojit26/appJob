package com.JobHunt.appJob.http;

import java.time.Duration;

/**
 * Explicit HTTP fetch policy shared by all job sources.
 *
 * <p>Centralizing these knobs keeps retry/timeout/concurrency behavior consistent and
 * out of the individual {@code JobSource} implementations.</p>
 *
 * @param maxAttempts                 total attempts including the first (must be &ge; 1)
 * @param initialBackoff              backoff before the first retry; doubles each retry
 * @param requestTimeout              per-request timeout (response must arrive within this)
 * @param connectTimeout             TCP connect timeout for the underlying client
 * @param maxConcurrentRequestsPerHost upper bound on in-flight requests to a single host
 */
public record HttpFetchPolicy(
        int maxAttempts,
        Duration initialBackoff,
        Duration requestTimeout,
        Duration connectTimeout,
        int maxConcurrentRequestsPerHost
) {
    public HttpFetchPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (maxConcurrentRequestsPerHost < 1) {
            throw new IllegalArgumentException("maxConcurrentRequestsPerHost must be >= 1");
        }
    }

    /**
     * Sensible defaults per the PRD: 2 retries (3 attempts), 500&nbsp;ms initial backoff,
     * 10&nbsp;s request timeout, 5&nbsp;s connect timeout, and at most 4 requests per host.
     */
    public static HttpFetchPolicy defaults() {
        return new HttpFetchPolicy(
                3,
                Duration.ofMillis(500),
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                4
        );
    }
}

