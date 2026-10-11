package com.JobHunt.appJob.source;

import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.http.HttpJobFetcher;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Greenhouse job-board API payloads into normalized {@link Job} records.
 *
 * <p>Endpoint (added later):
 * {@code GET https://boards-api.greenhouse.io/v1/boards/{slug}/jobs?content=true}</p>
 *
 * <p>This stage intentionally contains <strong>no HTTP code</strong>. Parsing is kept
 * independent from networking so parser bugs and API/network bugs stay separate and
 * each is testable in isolation. Tests feed the saved {@code greenhouse-*.json}
 * fixtures straight into {@link #parse(String)}.</p>
 *
 * <p>Field mapping (Greenhouse &rarr; {@link Job}):</p>
 * <ul>
 *   <li>{@code id} &rarr; {@code sourceJobId}</li>
 *   <li>{@code title} &rarr; {@code title}</li>
 *   <li>{@code company_name} &rarr; {@code company}</li>
 *   <li>{@code location.name} &rarr; {@code location}</li>
 *   <li>{@code absolute_url} &rarr; {@code applyUrl} and {@code jobUrl}</li>
 *   <li>{@code content} (HTML-escaped) &rarr; {@code description} (stripped to text)</li>
 *   <li>{@code first_published} &rarr; {@code postedAt}</li>
 *   <li>{@code metadata[name="Workplace Type"].value} &rarr; {@code workplaceType}</li>
 * </ul>
 *
 * <p>Experience, salary and employment-type are not provided by Greenhouse and are
 * left {@code null}.</p>
 */
public class GreenhouseJobSource implements JobSource {

    /** Logical source name used for logging, dedupe, and {@link Job#source()}. */
    public static final String SOURCE = "greenhouse";

    /** Default Greenhouse API host; overridable in tests to point at a mock server. */
    static final String DEFAULT_BASE_URL = "https://boards-api.greenhouse.io";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final String companySlug;
    private final HttpJobFetcher fetcher;
    private final String baseUrl;

    /**
     * Parse-only constructor. Use this when you only need {@link #parse(String)} (e.g. the
     * fixture tests); {@link #fetchJobs()} is unavailable because there is no fetcher/slug.
     */
    public GreenhouseJobSource() {
        this(null, null, DEFAULT_BASE_URL);
    }

    /**
     * Production constructor.
     *
     * @param companySlug Greenhouse board slug (e.g. "airbnb")
     * @param fetcher     shared HTTP helper owning timeouts/retries/concurrency
     */
    public GreenhouseJobSource(String companySlug, HttpJobFetcher fetcher) {
        this(companySlug, fetcher, DEFAULT_BASE_URL);
    }

    /** Test/advanced constructor allowing a custom base URL (e.g. a mock server). */
    GreenhouseJobSource(String companySlug, HttpJobFetcher fetcher, String baseUrl) {
        this.companySlug = companySlug;
        this.fetcher = fetcher;
        this.baseUrl = baseUrl;
    }

    @Override
    public String name() {
        return SOURCE;
    }

    @Override
    public List<Job> fetchJobs() throws Exception {
        if (companySlug == null || fetcher == null) {
            throw new IllegalStateException(
                    "GreenhouseJobSource was created parse-only; provide a companySlug and HttpJobFetcher to fetch.");
        }
        // fetchJobs = fetch + parse. All network behavior lives in HttpJobFetcher.
        String body = fetcher.fetch(boardUrl());
        return parse(body);
    }

    /** Builds the Greenhouse board endpoint for this source's slug. */
    String boardUrl() {
        return baseUrl + "/v1/boards/" + companySlug + "/jobs?content=true";
    }

    /**
     * Parses a full Greenhouse board payload (the object containing a {@code jobs} array)
     * into normalized {@link Job} records.
     *
     * @param json raw JSON string from the Greenhouse board endpoint or a fixture
     * @return normalized jobs; never {@code null}, may be empty
     */
    public List<Job> parse(String json) {
        JsonNode root = MAPPER.readTree(json);
        JsonNode jobsNode = root.path("jobs");
        List<Job> jobs = new ArrayList<>();
        if (!jobsNode.isArray()) {
            return jobs;
        }
        Instant fetchedAt = Instant.now();
        for (JsonNode node : jobsNode) {
            Job job = toJob(node, fetchedAt);
            if (job != null) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    /** Maps a single Greenhouse job object to a {@link Job}, or {@code null} if unusable. */
    private Job toJob(JsonNode node, Instant fetchedAt) {
        String sourceJobId = text(node.path("id"));
        String title = text(node.path("title"));
        if (sourceJobId == null || title == null) {
            // A record without an id or title is unusable; skip it rather than fail the run.
            return null;
        }

        String company = text(node.path("company_name"));
        String location = text(node.path("location").path("name"));
        String url = text(node.path("absolute_url"));
        String description = htmlToText(text(node.path("content")));
        Instant postedAt = parseInstant(text(node.path("first_published")));
        String workplaceType = workplaceType(node.path("metadata"));
        boolean remote = isRemote(workplaceType, location);

        return Job.builder()
                .source(SOURCE)
                .sourceJobId(sourceJobId.trim())
                .title(title.trim())
                .company(company == null ? null : company.trim())
                .location(location == null ? null : location.trim())
                .remote(remote)
                .workplaceType(workplaceType)
                .applyUrl(url)
                .jobUrl(url)
                .description(description)
                .postedAt(postedAt)
                .fetchedAt(fetchedAt)
                .build();
    }

    /** Finds the "Workplace Type" entry in the metadata array, if present. */
    private static String workplaceType(JsonNode metadata) {
        if (!metadata.isArray()) {
            return null;
        }
        for (JsonNode entry : metadata) {
            if ("Workplace Type".equalsIgnoreCase(text(entry.path("name")))) {
                return text(entry.path("value"));
            }
        }
        return null;
    }

    private static boolean isRemote(String workplaceType, String location) {
        String wt = workplaceType == null ? "" : workplaceType.toLowerCase();
        String loc = location == null ? "" : location.toLowerCase();
        return wt.contains("remote") || loc.contains("remote");
    }

    /**
     * Greenhouse {@code content} is HTML that arrives HTML-entity-escaped
     * (e.g. {@code &lt;p&gt;}). Unescape the entities first, then strip tags to plain text.
     */
    private static String htmlToText(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String unescaped = Parser.unescapeEntities(content, false);
        return Jsoup.parse(unescaped).text();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            // Unparseable date: treat as unknown rather than failing the whole parse.
            return null;
        }
    }

    /** Null-safe text accessor: returns {@code null} for missing/null nodes. */
    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String s = node.asString();
        return s == null || s.isEmpty() ? null : s;
    }
}

