package com.JobHunt.appJob.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for {@link HttpJobFetcher}: success, retry on transient failures,
 * fail-fast on non-retryable 4xx, transport-timeout retries, Retry-After honoring, and the
 * per-host concurrency limit. A recording {@link HttpJobFetcher.Sleeper} keeps retry tests
 * instant (no real backoff sleeping).
 */
class HttpJobFetcherTest {

    private HttpServer server;
    private String baseUrl;

    /** Records requested backoff delays instead of sleeping, so tests are fast and assertable. */
    private static final class RecordingSleeper implements HttpJobFetcher.Sleeper {
        final List<Long> delays = new CopyOnWriteArrayList<>();
        @Override public void sleep(long millis) {
            delays.add(millis);
        }
    }

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

    private HttpJobFetcher fetcher(HttpFetchPolicy policy, HttpJobFetcher.Sleeper sleeper) {
        return new HttpJobFetcher(policy, java.net.http.HttpClient.newHttpClient(), sleeper);
    }

    private HttpFetchPolicy policy(int maxAttempts, int perHost) {
        return new HttpFetchPolicy(
                maxAttempts,
                Duration.ofMillis(10),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                perHost);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void returnsBodyOnSuccess() throws Exception {
        server.createContext("/ok", ex -> respond(ex, 200, "{\"hello\":\"world\"}"));
        RecordingSleeper sleeper = new RecordingSleeper();

        // Generous request timeout so a cold-start first connection can't be mistaken for a
        // transient failure; this test is about the happy path, not timeout behavior.
        HttpFetchPolicy policy = new HttpFetchPolicy(
                3, Duration.ofMillis(10), Duration.ofSeconds(5), Duration.ofSeconds(5), 4);
        String body = fetcher(policy, sleeper).fetch(baseUrl + "/ok");

        assertEquals("{\"hello\":\"world\"}", body);
        assertTrue(sleeper.delays.isEmpty(), "no retries means no backoff sleeps");
    }

    @Test
    void retriesTransient500ThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/flaky", ex -> {
            if (calls.incrementAndGet() < 3) {
                respond(ex, 500, "boom");
            } else {
                respond(ex, 200, "recovered");
            }
        });
        RecordingSleeper sleeper = new RecordingSleeper();

        String body = fetcher(policy(3, 4), sleeper).fetch(baseUrl + "/flaky");

        assertEquals("recovered", body);
        assertEquals(3, calls.get(), "should have taken three attempts");
        assertEquals(2, sleeper.delays.size(), "two backoffs between three attempts");
        // Exponential: 10ms then 20ms.
        assertEquals(10L, sleeper.delays.get(0));
        assertEquals(20L, sleeper.delays.get(1));
    }

    @Test
    void exhaustsRetriesOnPersistent503() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/down", ex -> {
            calls.incrementAndGet();
            respond(ex, 503, "unavailable");
        });
        RecordingSleeper sleeper = new RecordingSleeper();

        HttpFetchException ex = assertThrows(HttpFetchException.class,
                () -> fetcher(policy(3, 4), sleeper).fetch(baseUrl + "/down"));

        assertEquals(503, ex.statusCode());
        assertTrue(ex.retryable(), "503 is a retryable status");
        assertEquals(3, calls.get(), "all attempts should be used");
        assertEquals(2, sleeper.delays.size(), "two backoffs before giving up");
    }

    @Test
    void failsFastOnNonRetryable404() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/missing", ex -> {
            calls.incrementAndGet();
            respond(ex, 404, "not found");
        });
        RecordingSleeper sleeper = new RecordingSleeper();

        HttpFetchException ex = assertThrows(HttpFetchException.class,
                () -> fetcher(policy(3, 4), sleeper).fetch(baseUrl + "/missing"));

        assertEquals(404, ex.statusCode());
        assertFalse(ex.retryable(), "404 must not be retried");
        assertEquals(1, calls.get(), "non-retryable failures make exactly one attempt");
        assertTrue(sleeper.delays.isEmpty(), "no backoff on fail-fast");
    }

    @Test
    void retriesOnRequestTimeout() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/slow", ex -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(800); // exceeds the short request timeout below
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(ex, 200, "too late");
        });
        RecordingSleeper sleeper = new RecordingSleeper();

        // Deliberately short request timeout so the 800ms handler always times out.
        HttpFetchPolicy policy = new HttpFetchPolicy(
                2, Duration.ofMillis(10), Duration.ofMillis(200), Duration.ofSeconds(2), 4);
        HttpFetchException ex = assertThrows(HttpFetchException.class,
                () -> fetcher(policy, sleeper).fetch(baseUrl + "/slow"));

        assertEquals(0, ex.statusCode(), "transport errors carry status 0");
        assertTrue(ex.retryable());
        assertEquals(2, calls.get(), "timeout should be retried up to the attempt limit");
        assertEquals(1, sleeper.delays.size(), "one backoff between the two attempts");
    }

    @Test
    void honorsRetryAfterHeader() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/throttled", ex -> {
            if (calls.incrementAndGet() == 1) {
                ex.getResponseHeaders().add("Retry-After", "2"); // seconds
                respond(ex, 429, "slow down");
            } else {
                respond(ex, 200, "ok now");
            }
        });
        RecordingSleeper sleeper = new RecordingSleeper();

        String body = fetcher(policy(3, 4), sleeper).fetch(baseUrl + "/throttled");

        assertEquals("ok now", body);
        assertEquals(1, sleeper.delays.size());
        assertEquals(2000L, sleeper.delays.get(0), "Retry-After (2s) should override exponential backoff");
    }

    @Test
    void limitsConcurrentRequestsPerHost() throws Exception {
        int limit = 2;
        int callers = 6;
        AtomicInteger current = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        CountDownLatch handlerReady = new CountDownLatch(1);

        server.createContext("/gated", new ConcurrencyProbeHandler(current, maxObserved, handlerReady));

        // Large request timeout so nothing times out; per-host limit is what we exercise.
        HttpFetchPolicy policy = new HttpFetchPolicy(
                1, Duration.ofMillis(10), Duration.ofSeconds(5), Duration.ofSeconds(2), limit);
        HttpJobFetcher fetcher = fetcher(policy, new RecordingSleeper());

        var pool = Executors.newFixedThreadPool(callers);
        var latch = new CountDownLatch(callers);
        for (int i = 0; i < callers; i++) {
            pool.submit(() -> {
                try {
                    fetcher.fetch(baseUrl + "/gated");
                } catch (Exception ignored) {
                    // not under test here
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS), "all callers should finish");
        pool.shutdownNow();

        assertTrue(maxObserved.get() <= limit,
                "observed concurrency " + maxObserved.get() + " must not exceed the per-host limit " + limit);
    }

    /** Handler that records peak concurrency and briefly holds each request open. */
    private record ConcurrencyProbeHandler(
            AtomicInteger current, AtomicInteger maxObserved, CountDownLatch ready) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int now = current.incrementAndGet();
            maxObserved.accumulateAndGet(now, Math::max);
            ready.countDown();
            try {
                Thread.sleep(150); // hold the slot so overlap is observable
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                current.decrementAndGet();
            }
            respond(exchange, 200, "done");
        }
    }
}

