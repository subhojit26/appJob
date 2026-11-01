package com.JobHunt.appJob.source;

import com.JobHunt.appJob.http.HttpFetchException;
import com.JobHunt.appJob.http.HttpFetchPolicy;
import com.JobHunt.appJob.http.HttpJobFetcher;
import com.JobHunt.appJob.model.Job;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the complete Adzuna fetch path:
 * <pre>
 *   mock HTTP server -> AdzunaJobSource.fetchJobs() -> HttpJobFetcher.fetch()
 *                    -> AdzunaJobSource.parse() -> List&lt;Job&gt;
 * </pre>
 *
 * <p>The pure {@link AdzunaJobSourceTest} fixture tests are intentionally left untouched.
 * Sentinel credentials are used so the test can assert the secret never leaks into error
 * output.</p>
 */
class AdzunaJobSourceIntegrationTest {

    private static final String TEST_APP_ID = "test-app-id";
    private static final String TEST_APP_KEY = "SUPER-SECRET-KEY-should-not-leak";

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpJobFetcher fetcher() {
        HttpFetchPolicy policy = new HttpFetchPolicy(
                2, Duration.ofMillis(10), Duration.ofSeconds(5), Duration.ofSeconds(2), 4);
        return new HttpJobFetcher(policy, HttpClient.newHttpClient(), millis -> { /* no sleep */ });
    }

    private AdzunaJobSource source() {
        return new AdzunaJobSource("in", "java developer", TEST_APP_ID, TEST_APP_KEY,
                fetcher(), baseUrl, 50, 2);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String fixture() throws IOException {
        try (InputStream in = AdzunaJobSourceIntegrationTest.class.getClassLoader()
                .getResourceAsStream("fixtures/adzuna-india-java.json")) {
            assertNotNull(in, "adzuna fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fetchJobsReturnsParsedJobsFromMockServer() throws Exception {
        String json = fixture();
        server.createContext("/v1/api/jobs/in/search/1", ex -> respond(ex, 200, json));

        List<Job> jobs = source().fetchJobs();

        assertFalse(jobs.isEmpty(), "jobs should be parsed from the mocked response");
        assertEquals("adzuna", jobs.get(0).source());
    }

    @Test
    void fetchJobsBuildsExactQuery() throws Exception {
        String json = fixture();
        AtomicReference<String> requestedUri = new AtomicReference<>();
        server.createContext("/v1/api/jobs/in/search/1", ex -> {
            requestedUri.set(ex.getRequestURI().toString());
            respond(ex, 200, json);
        });

        AdzunaJobSource source = source();
        source.fetchJobs();

        String uri = requestedUri.get();
        assertNotNull(uri, "the search endpoint should have been requested");
        assertTrue(uri.startsWith("/v1/api/jobs/in/search/1"), "unexpected path: " + uri);
        assertTrue(uri.contains("app_id=" + TEST_APP_ID), "app_id must be present");
        assertTrue(uri.contains("app_key=" + TEST_APP_KEY), "app_key must be present in the real request");
        assertTrue(uri.contains("results_per_page=50"), "results_per_page must be present");
        assertTrue(uri.contains("what=java+developer"), "what must be URL-encoded: " + uri);
        assertTrue(uri.contains("sort_by=date"), "sort_by=date must be present");
        assertTrue(uri.contains("max_days_old=2"), "max_days_old must be present");
    }

    @Test
    void secretIsNotExposedInErrorSurface() {
        // The fetcher embeds the URL (with app_key) in its message; Adzuna must redact it.
        server.createContext("/v1/api/jobs/in/search/1", ex -> respond(ex, 500, "server error"));

        // maxAttempts=1 so the 500 surfaces immediately as a retryable failure.
        HttpFetchPolicy policy = new HttpFetchPolicy(
                1, Duration.ofMillis(10), Duration.ofSeconds(5), Duration.ofSeconds(2), 4);
        HttpJobFetcher oneShot = new HttpJobFetcher(policy, HttpClient.newHttpClient(), m -> { });
        AdzunaJobSource source = new AdzunaJobSource("in", "java developer",
                TEST_APP_ID, TEST_APP_KEY, oneShot, baseUrl, 50, 2);

        HttpFetchException ex = assertThrows(HttpFetchException.class, source::fetchJobs);

        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String errorSurface = String.valueOf(ex.getMessage()) + "\n" + sw;

        assertFalse(errorSurface.contains(TEST_APP_KEY),
                "the app_key must never appear in exception message/stack trace");
        assertTrue(errorSurface.contains("***") || !errorSurface.contains("app_key="),
                "the secret should be redacted at the error boundary");
    }

    @Test
    void httpFailurePropagatesAsHttpFetchException() {
        server.createContext("/v1/api/jobs/in/search/1", ex -> respond(ex, 404, "not found"));

        HttpFetchException ex = assertThrows(HttpFetchException.class, () -> source().fetchJobs());
        assertEquals(404, ex.statusCode());
        assertFalse(ex.retryable(), "404 should surface as a non-retryable failure");
        assertFalse(String.valueOf(ex.getMessage()).contains(TEST_APP_KEY),
                "even on 404 the key must not leak");
    }

    @Test
    void parseOnlyInstanceCannotFetch() {
        // The parse-only instance must refuse to fetch rather than hit the network.
        assertThrows(IllegalStateException.class, () -> new AdzunaJobSource().fetchJobs());
    }
}

