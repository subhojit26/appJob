package com.JobHunt.appJob.http;

/**
 * Thrown when an HTTP fetch ultimately fails.
 *
 * <p>Carries the HTTP status code (or {@code 0} for transport-level failures such as
 * timeouts/connection errors) and whether the failure was considered retryable. The
 * fetcher retries {@link #retryable()} failures up to the policy's attempt limit; a
 * non-retryable failure (most {@code 4xx}) fails fast.</p>
 */
public class HttpFetchException extends Exception {

    private final int statusCode;
    private final boolean retryable;

    public HttpFetchException(String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    /** HTTP status code, or {@code 0} if the failure occurred before a response (e.g. timeout). */
    public int statusCode() {
        return statusCode;
    }

    /** Whether this failure was retryable (transient 5xx/429 or transport error). */
    public boolean retryable() {
        return retryable;
    }
}

