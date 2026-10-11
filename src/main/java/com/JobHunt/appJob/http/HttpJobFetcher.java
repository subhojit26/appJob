package com.JobHunt.appJob.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Shared HTTP helper that owns the cross-cutting fetch concerns for every job source:
 * JDK {@link HttpClient}, connect/request timeouts, retry with exponential backoff,
 * and a per-host concurrency limit.
 *
 * <p>Sources call {@link #fetch(String)} to get a response body as a {@code String}, then
 * hand it to their own {@code parse(String)} method. This keeps parsing pure and the
 * existing fixture tests network-free.</p>
 *
 * <p>Retry semantics:</p>
 * <ul>
 *   <li>{@code 2xx} &rarr; success, body returned.</li>
 *   <li>{@code 429} and {@code 5xx} &rarr; retryable; honored {@code Retry-After} overrides backoff.</li>
 *   <li>other {@code 4xx} &rarr; non-retryable, fails fast.</li>
 *   <li>transport errors (timeouts, connection failures) &rarr; retryable.</li>
 * </ul>
 */
public class HttpJobFetcher {

    /** Injectable sleep so retry/backoff can be tested without real delays. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final String USER_AGENT =
            "JobWatch/1.0 (+https://github.com/; personal job digest)";

    private final HttpFetchPolicy policy;
    private final HttpClient client;
    private final Sleeper sleeper;
    private final ConcurrentHashMap<String, Semaphore> hostSemaphores = new ConcurrentHashMap<>();

    /** Production constructor: builds a virtual-thread-friendly client from the policy. */
    public HttpJobFetcher(HttpFetchPolicy policy) {
        this(policy,
                HttpClient.newBuilder()
                        .connectTimeout(policy.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                Thread::sleep);
    }

    /** Test/advanced constructor allowing a stub {@link HttpClient} and {@link Sleeper}. */
    public HttpJobFetcher(HttpFetchPolicy policy, HttpClient client, Sleeper sleeper) {
        this.policy = policy;
        this.client = client;
        this.sleeper = sleeper;
    }

    /**
     * Fetches the given URL, applying timeouts, retries, and the per-host concurrency limit.
     *
     * @param url absolute HTTP(S) URL
     * @return the response body
     * @throws HttpFetchException   if the request ultimately fails
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public String fetch(String url) throws HttpFetchException, InterruptedException {
        URI uri = URI.create(url);
        Semaphore semaphore = hostSemaphores.computeIfAbsent(
                hostKey(uri), h -> new Semaphore(policy.maxConcurrentRequestsPerHost()));
        semaphore.acquire();
        try {
            return attempt(uri);
        } finally {
            semaphore.release();
        }
    }

    private String attempt(URI uri) throws HttpFetchException, InterruptedException {
        HttpFetchException lastError = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                HttpResponse<String> response = send(uri);
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response.body();
                }
                if (isRetryableStatus(status)) {
                    lastError = new HttpFetchException(
                            "HTTP " + status + " from " + uri, status, true, null);
                    if (attempt < policy.maxAttempts()) {
                        backoff(attempt, retryAfterMillis(response));
                        continue;
                    }
                    throw lastError;
                }
                // Non-retryable (most 4xx): fail fast.
                throw new HttpFetchException("HTTP " + status + " from " + uri, status, false, null);
            } catch (IOException e) {
                // Transport-level failure (timeout, connection reset): retryable.
                lastError = new HttpFetchException(
                        "Transport error fetching " + uri + ": " + e.getMessage(), 0, true, e);
                if (attempt < policy.maxAttempts()) {
                    backoff(attempt, -1);
                    continue;
                }
                throw lastError;
            }
        }
        // Unreachable in practice, but keeps the compiler and the contract happy.
        throw (lastError != null)
                ? lastError
                : new HttpFetchException("Failed to fetch " + uri, 0, true, null);
    }

    private HttpResponse<String> send(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(policy.requestTimeout())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static boolean isRetryableStatus(int status) {
        return status == 429 || (status >= 500 && status < 600);
    }

    /** Exponential backoff for {@code attempt} (1-based); {@code retryAfterMillis} overrides if &ge; 0. */
    private void backoff(int attempt, long retryAfterMillis) throws InterruptedException {
        long base = policy.initialBackoff().toMillis() * (1L << (attempt - 1));
        long delay = retryAfterMillis >= 0 ? retryAfterMillis : base;
        if (delay > 0) {
            sleeper.sleep(delay);
        }
    }

    /** Parses a numeric {@code Retry-After} header (seconds) into millis, or {@code -1} if absent. */
    private static long retryAfterMillis(HttpResponse<String> response) {
        Optional<String> header = response.headers().firstValue("Retry-After");
        if (header.isEmpty()) {
            return -1;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.get().trim())).toMillis();
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String hostKey(URI uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase();
    }
}

