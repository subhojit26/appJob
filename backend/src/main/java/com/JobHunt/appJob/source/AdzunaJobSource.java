package com.JobHunt.appJob.source;

import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.http.HttpFetchException;
import com.JobHunt.appJob.http.HttpJobFetcher;
import org.jsoup.Jsoup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Adzuna search API payloads into normalized {@link Job} records.
 *
 * <p>Endpoint (added later):
 * {@code GET https://api.adzuna.com/v1/api/jobs/{country}/search/{page}?app_id=..&app_key=..}</p>
 *
 * <p>Parse-only stage (no HTTP), matching the other sources. Jobs are wrapped in a
 * {@code results} array, with company and location nested under {@code display_name}.
 * Unlike the ATS sources, Adzuna <em>does</em> include a company name, so no constructor
 * argument is required.</p>
 *
 * <p>Field mapping (Adzuna &rarr; {@link Job}):</p>
 * <ul>
 *   <li>{@code id} &rarr; {@code sourceJobId}</li>
 *   <li>{@code title} &rarr; {@code title}</li>
 *   <li>{@code company.display_name} &rarr; {@code company}</li>
 *   <li>{@code location.display_name} &rarr; {@code location}</li>
 *   <li>{@code description} &rarr; {@code description}</li>
 *   <li>{@code redirect_url} &rarr; {@code applyUrl} and {@code jobUrl}</li>
 *   <li>{@code created} &rarr; {@code postedAt}</li>
 *   <li>{@code contract_type} (falls back to {@code contract_time}) &rarr; {@code employmentType}</li>
 *   <li>{@code salary_min} / {@code salary_max} &rarr; {@code salaryMin} / {@code salaryMax}</li>
 *   <li>{@code salary_is_predicted == "1"} &rarr; {@code salaryEstimated}</li>
 * </ul>
 *
 * <p><strong>Salary provenance:</strong> Adzuna often returns <em>predicted</em> salaries.
 * When {@code salary_is_predicted} is {@code "1"} the job is flagged via
 * {@link Job#salaryEstimated()} so estimates are never shown as employer-provided figures.
 * Adzuna does not include a currency in its payload, so {@code salaryCurrency} stays
 * {@code null}. Experience is not provided and is left {@code null}.</p>
 */
public class AdzunaJobSource implements JobSource {

    /** Logical source name used for logging, dedupe, and {@link Job#source()}. */
    public static final String SOURCE = "adzuna";

    /** Default Adzuna API host; overridable in tests to point at a mock server. */
    static final String DEFAULT_BASE_URL = "https://api.adzuna.com";

    private static final int DEFAULT_RESULTS_PER_PAGE = 50;
    private static final int DEFAULT_MAX_DAYS_OLD = 2;
    private static final int PAGE = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final String country;
    private final String what;
    private final String appId;
    private final String appKey;
    private final HttpJobFetcher fetcher;
    private final String baseUrl;
    private final int resultsPerPage;
    private final int maxDaysOld;

    /**
     * Parse-only constructor. Use this when you only need {@link #parse(String)} (e.g. the
     * fixture tests). {@link #fetchJobs()} is unavailable because there is no fetcher/config.
     */
    public AdzunaJobSource() {
        this(null, null, null, null, null, DEFAULT_BASE_URL,
                DEFAULT_RESULTS_PER_PAGE, DEFAULT_MAX_DAYS_OLD);
    }

    /**
     * Production constructor.
     *
     * @param country two-letter Adzuna country code (e.g. "in", "gb"); part of the endpoint path
     * @param what    search term (e.g. "java developer"); URL-encoded when building the query
     * @param appId   Adzuna app id (from {@code adzuna.app-id} / {@code ADZUNA_APP_ID})
     * @param appKey  Adzuna app key (from {@code adzuna.app-key} / {@code ADZUNA_APP_KEY}) — secret
     * @param fetcher shared HTTP helper owning timeouts/retries/concurrency
     */
    public AdzunaJobSource(String country, String what, String appId, String appKey, HttpJobFetcher fetcher) {
        this(country, what, appId, appKey, fetcher, DEFAULT_BASE_URL,
                DEFAULT_RESULTS_PER_PAGE, DEFAULT_MAX_DAYS_OLD);
    }

    /** Test/advanced constructor allowing a custom base URL and paging knobs. */
    AdzunaJobSource(String country, String what, String appId, String appKey,
                    HttpJobFetcher fetcher, String baseUrl, int resultsPerPage, int maxDaysOld) {
        this.country = country;
        this.what = what;
        this.appId = appId;
        this.appKey = appKey;
        this.fetcher = fetcher;
        this.baseUrl = baseUrl;
        this.resultsPerPage = resultsPerPage;
        this.maxDaysOld = maxDaysOld;
    }

    @Override
    public String name() {
        return SOURCE;
    }

    @Override
    public List<Job> fetchJobs() throws Exception {
        if (country == null || what == null || appId == null || appKey == null || fetcher == null) {
            throw new IllegalStateException(
                    "AdzunaJobSource was created parse-only; provide country, search term, credentials and an HttpJobFetcher to fetch.");
        }
        try {
            // fetchJobs = fetch + parse. All network behavior lives in HttpJobFetcher.
            String body = fetcher.fetch(boardUrl());
            return parse(body);
        } catch (HttpFetchException e) {
            // Redact the secret at the error boundary; the fetcher embeds the full URL (with
            // app_key) in its message. Preserve status/retryable so callers behave identically.
            throw new HttpFetchException(redact(e.getMessage()), e.statusCode(), e.retryable(), null);
        }
    }

    /**
     * Builds the real Adzuna search URL (including credentials) that the fetcher must call.
     *
     * <p>This intentionally returns the unredacted URL because the HTTP client needs it;
     * redaction happens only at logging/error boundaries (see {@link #fetchJobs()}).</p>
     */
    String boardUrl() {
        return baseUrl + "/v1/api/jobs/" + country + "/search/" + PAGE
                + "?app_id=" + enc(appId)
                + "&app_key=" + enc(appKey)
                + "&results_per_page=" + resultsPerPage
                + "&what=" + enc(what)
                + "&sort_by=date"
                + "&max_days_old=" + maxDaysOld;
    }

    /** Masks the {@code app_key} value so secrets never reach logs or exception messages. */
    private String redact(String message) {
        if (message == null) {
            return null;
        }
        if (appKey != null && !appKey.isEmpty()) {
            message = message.replace(appKey, "***");
        }
        // Also mask any app_key query value generically, in case of encoding differences.
        return message.replaceAll("(?i)(app_key=)[^&\\s]+", "$1***");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Parses an Adzuna search payload (object with a {@code results} array) into normalized jobs.
     *
     * @param json raw JSON string from the Adzuna search endpoint or a fixture
     * @return normalized jobs; never {@code null}, may be empty
     */
    public List<Job> parse(String json) {
        JsonNode root = MAPPER.readTree(json);
        JsonNode results = root.path("results");
        List<Job> jobs = new ArrayList<>();
        if (!results.isArray()) {
            return jobs;
        }
        Instant fetchedAt = Instant.now();
        for (JsonNode node : results) {
            Job job = toJob(node, fetchedAt);
            if (job != null) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    /** Maps a single Adzuna result to a {@link Job}, or {@code null} if unusable. */
    private Job toJob(JsonNode node, Instant fetchedAt) {
        String sourceJobId = text(node.path("id"));
        String title = text(node.path("title"));
        if (sourceJobId == null || title == null) {
            // Skip records missing an id or title rather than failing the whole parse.
            return null;
        }

        String company = text(node.path("company").path("display_name"));
        String location = text(node.path("location").path("display_name"));
        String url = text(node.path("redirect_url"));
        String description = description(node);
        Instant postedAt = parseInstant(text(node.path("created")));
        String employmentType = employmentType(node);
        boolean remote = mentionsRemote(location) || mentionsRemote(title);

        Double salaryMin = decimal(node.path("salary_min"));
        Double salaryMax = decimal(node.path("salary_max"));
        boolean salaryEstimated = "1".equals(text(node.path("salary_is_predicted")));

        return Job.builder()
                .source(SOURCE)
                .sourceJobId(sourceJobId.trim())
                .title(title.trim())
                .company(company == null ? null : company.trim())
                .location(location == null ? null : location.trim())
                .remote(remote)
                .applyUrl(url)
                .jobUrl(url)
                .description(description)
                .postedAt(postedAt)
                .employmentType(employmentType)
                .salaryMin(salaryMin)
                .salaryMax(salaryMax)
                // Adzuna payloads carry no currency; leave salaryCurrency null (honest).
                .salaryEstimated(salaryEstimated)
                .fetchedAt(fetchedAt)
                .build();
    }

    /** Adzuna descriptions are usually plain text; strip tags defensively just in case. */
    private static String description(JsonNode node) {
        String desc = text(node.path("description"));
        return desc == null ? null : Jsoup.parse(desc).text();
    }

    /** Prefers {@code contract_type}; falls back to {@code contract_time} (e.g. "full_time"). */
    private static String employmentType(JsonNode node) {
        String type = text(node.path("contract_type"));
        return type != null ? type : text(node.path("contract_time"));
    }

    private static boolean mentionsRemote(String value) {
        return value != null && value.toLowerCase().contains("remote");
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Double decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asDouble();
    }

    /** Null-safe text accessor: returns {@code null} for missing/null/empty nodes. */
    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String s = node.asString();
        return s == null || s.isEmpty() ? null : s;
    }
}

