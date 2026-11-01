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
 * Integration test for the complete Greenhouse fetch path:
 * <pre>
 *   mock HTTP server -> GreenhouseJobSource.fetchJobs() -> HttpJobFetcher.fetch()
 *                    -> GreenhouseJobSource.parse() -> List&lt;Job&gt;
 * </pre>
 *
 * <p>The pure {@link GreenhouseJobSourceTest} fixture tests are intentionally left untouched;
 * they validate parsing independently of networking.</p>
 */
class GreenhouseJobSourceIntegrationTest {

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

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String fixture() throws IOException {
        try (InputStream in = GreenhouseJobSourceIntegrationTest.class.getClassLoader()
                .getResourceAsStream("fixtures/greenhouse-airbnb.json")) {
            assertNotNull(in, "greenhouse fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fetchJobsReturnsParsedJobsFromMockServer() throws Exception {
        String json = fixture();
        AtomicReference<String> requestedUri = new AtomicReference<>();
        server.createContext("/v1/boards/airbnb/jobs", ex -> {
            requestedUri.set(ex.getRequestURI().toString());
            respond(ex, 200, json);
        });

        GreenhouseJobSource source = new GreenhouseJobSource("airbnb", fetcher(), baseUrl);
        List<Job> jobs = source.fetchJobs();

        assertFalse(jobs.isEmpty(), "jobs should be parsed from the mocked response");
        assertEquals("greenhouse", jobs.get(0).source());
        assertEquals("Airbnb", jobs.get(0).company());
    }

    @Test
    void fetchJobsUsesConfiguredBoardUrl() throws Exception {
        String json = fixture();
        AtomicReference<String> requestedUri = new AtomicReference<>();
        server.createContext("/v1/boards/airbnb/jobs", ex -> {
            requestedUri.set(ex.getRequestURI().toString());
            respond(ex, 200, json);
        });

        GreenhouseJobSource source = new GreenhouseJobSource("airbnb", fetcher(), baseUrl);
        // boardUrl() is the single source of truth for the endpoint.
        assertEquals(baseUrl + "/v1/boards/airbnb/jobs?content=true", source.boardUrl());

        source.fetchJobs();

        assertNotNull(requestedUri.get(), "the board endpoint should have been requested");
        assertTrue(requestedUri.get().startsWith("/v1/boards/airbnb/jobs"),
                "unexpected path: " + requestedUri.get());
        assertTrue(requestedUri.get().contains("content=true"),
                "content=true query param should be present: " + requestedUri.get());
    }

    @Test
    void httpFailurePropagatesAsHttpFetchException() {
        server.createContext("/v1/boards/airbnb/jobs", ex -> respond(ex, 404, "no such board"));

        GreenhouseJobSource source = new GreenhouseJobSource("airbnb", fetcher(), baseUrl);

        HttpFetchException ex = assertThrows(HttpFetchException.class, source::fetchJobs);
        assertEquals(404, ex.statusCode());
        assertFalse(ex.retryable(), "404 should surface as a non-retryable failure");
    }

    @Test
    void parseOnlyInstanceCannotFetch() {
        // The no-arg (parse-only) instance must refuse to fetch rather than hit the network.
        assertThrows(IllegalStateException.class, () -> new GreenhouseJobSource().fetchJobs());
    }
}

