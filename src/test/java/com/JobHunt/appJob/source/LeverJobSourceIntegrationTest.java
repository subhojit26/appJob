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
 * Integration test for the complete Lever fetch path:
 * <pre>
 *   mock HTTP server -> LeverJobSource.fetchJobs() -> HttpJobFetcher.fetch()
 *                    -> LeverJobSource.parse() -> List&lt;Job&gt;
 * </pre>
 *
 * <p>The pure {@link LeverJobSourceTest} fixture tests are intentionally left untouched.</p>
 */
class LeverJobSourceIntegrationTest {

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
        try (InputStream in = LeverJobSourceIntegrationTest.class.getClassLoader()
                .getResourceAsStream("fixtures/lever-entrata.json")) {
            assertNotNull(in, "lever fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fetchJobsReturnsParsedJobsFromMockServer() throws Exception {
        String json = fixture();
        server.createContext("/v0/postings/entrata", ex -> respond(ex, 200, json));

        LeverJobSource source = new LeverJobSource("entrata", "Entrata", fetcher(), baseUrl);
        List<Job> jobs = source.fetchJobs();

        assertFalse(jobs.isEmpty(), "jobs should be parsed from the mocked response");
        assertEquals("lever", jobs.get(0).source());
        assertEquals("Entrata", jobs.get(0).company(), "constructor company name must be used");
    }

    @Test
    void fetchJobsUsesConfiguredBoardUrl() throws Exception {
        String json = fixture();
        AtomicReference<String> requestedUri = new AtomicReference<>();
        server.createContext("/v0/postings/entrata", ex -> {
            requestedUri.set(ex.getRequestURI().toString());
            respond(ex, 200, json);
        });

        LeverJobSource source = new LeverJobSource("entrata", "Entrata", fetcher(), baseUrl);
        assertEquals(baseUrl + "/v0/postings/entrata?mode=json", source.boardUrl());

        source.fetchJobs();

        assertNotNull(requestedUri.get(), "the postings endpoint should have been requested");
        assertTrue(requestedUri.get().startsWith("/v0/postings/entrata"),
                "unexpected path: " + requestedUri.get());
        assertTrue(requestedUri.get().contains("mode=json"),
                "mode=json query param should be present: " + requestedUri.get());
    }

    @Test
    void httpFailurePropagatesAsHttpFetchException() {
        server.createContext("/v0/postings/entrata", ex -> respond(ex, 404, "no such board"));

        LeverJobSource source = new LeverJobSource("entrata", "Entrata", fetcher(), baseUrl);

        HttpFetchException ex = assertThrows(HttpFetchException.class, source::fetchJobs);
        assertEquals(404, ex.statusCode());
        assertFalse(ex.retryable(), "404 should surface as a non-retryable failure");
    }

    @Test
    void parseOnlyInstanceCannotFetch() {
        // The parse-only instance must refuse to fetch rather than hit the network.
        assertThrows(IllegalStateException.class, () -> new LeverJobSource("Entrata").fetchJobs());
    }
}

