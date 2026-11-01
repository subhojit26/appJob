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
 * Integration test for the complete Ashby fetch path:
 * <pre>
 *   mock HTTP server -> AshbyJobSource.fetchJobs() -> HttpJobFetcher.fetch()
 *                    -> AshbyJobSource.parse() -> List&lt;Job&gt;
 * </pre>
 *
 * <p>The pure {@link AshbyJobSourceTest} fixture tests are intentionally left untouched.</p>
 */
class AshbyJobSourceIntegrationTest {

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
        try (InputStream in = AshbyJobSourceIntegrationTest.class.getClassLoader()
                .getResourceAsStream("fixtures/ashby-llamaindex.json")) {
            assertNotNull(in, "ashby fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fetchJobsReturnsParsedJobsFromMockServer() throws Exception {
        String json = fixture();
        server.createContext("/posting-api/job-board/llamaindex", ex -> respond(ex, 200, json));

        AshbyJobSource source = new AshbyJobSource("llamaindex", "LlamaIndex", fetcher(), baseUrl);
        List<Job> jobs = source.fetchJobs();

        assertFalse(jobs.isEmpty(), "jobs should be parsed from the mocked response");
        assertEquals("ashby", jobs.get(0).source());
        assertEquals("LlamaIndex", jobs.get(0).company(), "constructor company name must be used");
    }

    @Test
    void fetchJobsUsesConfiguredBoardUrl() throws Exception {
        String json = fixture();
        AtomicReference<String> requestedUri = new AtomicReference<>();
        server.createContext("/posting-api/job-board/llamaindex", ex -> {
            requestedUri.set(ex.getRequestURI().toString());
            respond(ex, 200, json);
        });

        AshbyJobSource source = new AshbyJobSource("llamaindex", "LlamaIndex", fetcher(), baseUrl);
        assertEquals(baseUrl + "/posting-api/job-board/llamaindex?includeCompensation=true", source.boardUrl());

        source.fetchJobs();

        assertNotNull(requestedUri.get(), "the job-board endpoint should have been requested");
        assertTrue(requestedUri.get().startsWith("/posting-api/job-board/llamaindex"),
                "unexpected path: " + requestedUri.get());
        assertTrue(requestedUri.get().contains("includeCompensation=true"),
                "includeCompensation=true query param should be present: " + requestedUri.get());
    }

    @Test
    void httpFailurePropagatesAsHttpFetchException() {
        server.createContext("/posting-api/job-board/llamaindex", ex -> respond(ex, 404, "no such board"));

        AshbyJobSource source = new AshbyJobSource("llamaindex", "LlamaIndex", fetcher(), baseUrl);

        HttpFetchException ex = assertThrows(HttpFetchException.class, source::fetchJobs);
        assertEquals(404, ex.statusCode());
        assertFalse(ex.retryable(), "404 should surface as a non-retryable failure");
    }

    @Test
    void parseOnlyInstanceCannotFetch() {
        // The parse-only instance must refuse to fetch rather than hit the network.
        assertThrows(IllegalStateException.class, () -> new AshbyJobSource("LlamaIndex").fetchJobs());
    }
}

