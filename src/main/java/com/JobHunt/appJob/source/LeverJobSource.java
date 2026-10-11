package com.JobHunt.appJob.source;

import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.http.HttpJobFetcher;
import org.jsoup.Jsoup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Lever postings API payloads into normalized {@link Job} records.
 *
 * <p>Endpoint (added later):
 * {@code GET https://api.lever.co/v0/postings/{slug}?mode=json}</p>
 *
 * <p>Like {@link GreenhouseJobSource}, this stage is <strong>parse-only</strong> (no HTTP).
 * The top-level payload is a JSON <em>array</em> of postings. Tests feed the saved
 * {@code lever-*.json} fixtures directly into {@link #parse(String)}.</p>
 *
 * <p>Lever does not include the company name in its payload (it is the board slug), so the
 * caller supplies a display name via the constructor, matching the PRD's
 * slug &rarr; display-name approach for ATS sources.</p>
 *
 * <p>Field mapping (Lever &rarr; {@link Job}):</p>
 * <ul>
 *   <li>{@code id} &rarr; {@code sourceJobId}</li>
 *   <li>{@code text} &rarr; {@code title}</li>
 *   <li>constructor {@code company} &rarr; {@code company}</li>
 *   <li>{@code categories.location} &rarr; {@code location}</li>
 *   <li>{@code workplaceType} &rarr; {@code workplaceType} (drives {@code remote})</li>
 *   <li>{@code applyUrl} &rarr; {@code applyUrl}; {@code hostedUrl} &rarr; {@code jobUrl}</li>
 *   <li>{@code descriptionPlain} &rarr; {@code description} (falls back to stripped {@code description})</li>
 *   <li>{@code createdAt} (epoch ms) &rarr; {@code postedAt}</li>
 *   <li>{@code categories.commitment} &rarr; {@code employmentType}</li>
 *   <li>{@code salaryRange.min/max/currency} &rarr; {@code salaryMin/salaryMax/salaryCurrency} (nullable)</li>
 * </ul>
 *
 * <p>Experience is not provided by Lever and is left {@code null}.</p>
 */
public class LeverJobSource implements JobSource {

    /** Logical source name used for logging, dedupe, and {@link Job#source()}. */
    public static final String SOURCE = "lever";

    /** Default Lever API host; overridable in tests to point at a mock server. */
    static final String DEFAULT_BASE_URL = "https://api.lever.co";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final String companySlug;
    private final String company;
    private final HttpJobFetcher fetcher;
    private final String baseUrl;

    /**
     * Parse-only constructor. Use this when you only need {@link #parse(String)} (e.g. the
     * fixture tests). {@link #fetchJobs()} is unavailable because there is no fetcher/slug.
     *
     * @param company display name for this Lever board (e.g. "Entrata"); Lever's API does
     *                not return it, so it is supplied here. May be {@code null}.
     */
    public LeverJobSource(String company) {
        this(null, company, null, DEFAULT_BASE_URL);
    }

    /**
     * Production constructor.
     *
     * @param companySlug Lever board slug (e.g. "leverdemo-8")
     * @param company     display name for this board (Lever's payload omits it)
     * @param fetcher     shared HTTP helper owning timeouts/retries/concurrency
     */
    public LeverJobSource(String companySlug, String company, HttpJobFetcher fetcher) {
        this(companySlug, company, fetcher, DEFAULT_BASE_URL);
    }

    /** Test/advanced constructor allowing a custom base URL (e.g. a mock server). */
    LeverJobSource(String companySlug, String company, HttpJobFetcher fetcher, String baseUrl) {
        this.companySlug = companySlug;
        this.company = company;
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
                    "LeverJobSource was created parse-only; provide a companySlug and HttpJobFetcher to fetch.");
        }
        // fetchJobs = fetch + parse. All network behavior lives in HttpJobFetcher.
        String body = fetcher.fetch(boardUrl());
        return parse(body);
    }

    /** Builds the Lever postings endpoint for this source's slug. */
    String boardUrl() {
        return baseUrl + "/v0/postings/" + companySlug + "?mode=json";
    }

    /**
     * Parses a Lever postings payload (a JSON array of postings) into normalized jobs.
     *
     * @param json raw JSON string from the Lever postings endpoint or a fixture
     * @return normalized jobs; never {@code null}, may be empty
     */
    public List<Job> parse(String json) {
        JsonNode root = MAPPER.readTree(json);
        List<Job> jobs = new ArrayList<>();
        if (!root.isArray()) {
            return jobs;
        }
        Instant fetchedAt = Instant.now();
        for (JsonNode node : root) {
            Job job = toJob(node, fetchedAt);
            if (job != null) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    /** Maps a single Lever posting to a {@link Job}, or {@code null} if unusable. */
    private Job toJob(JsonNode node, Instant fetchedAt) {
        String sourceJobId = text(node.path("id"));
        String title = text(node.path("text"));
        if (sourceJobId == null || title == null) {
            // Skip records missing an id or title rather than failing the whole parse.
            return null;
        }

        JsonNode categories = node.path("categories");
        String location = text(categories.path("location"));
        String employmentType = text(categories.path("commitment"));
        String workplaceType = text(node.path("workplaceType"));
        String applyUrl = text(node.path("applyUrl"));
        String hostedUrl = text(node.path("hostedUrl"));
        String description = description(node);
        Instant postedAt = epochMillis(node.path("createdAt"));
        boolean remote = isRemote(workplaceType, location, description);

        JsonNode salary = node.path("salaryRange");
        Double salaryMin = decimal(salary.path("min"));
        Double salaryMax = decimal(salary.path("max"));
        String salaryCurrency = text(salary.path("currency"));

        return Job.builder()
                .source(SOURCE)
                .sourceJobId(sourceJobId.trim())
                .title(title.trim())
                .company(company == null ? null : company.trim())
                .location(location == null ? null : location.trim())
                .remote(remote)
                .workplaceType(workplaceType)
                .applyUrl(applyUrl)
                .jobUrl(hostedUrl)
                .description(description)
                .postedAt(postedAt)
                .employmentType(employmentType)
                .salaryMin(salaryMin)
                .salaryMax(salaryMax)
                .salaryCurrency(salaryCurrency)
                .fetchedAt(fetchedAt)
                .build();
    }

    /**
     * Prefers Lever's ready-made {@code descriptionPlain}; if absent, strips tags from
     * the HTML {@code description} with Jsoup.
     */
    private static String description(JsonNode node) {
        String plain = text(node.path("descriptionPlain"));
        if (plain != null) {
            return plain;
        }
        String html = text(node.path("description"));
        return html == null ? null : Jsoup.parse(html).text();
    }

    private static boolean isRemote(String workplaceType, String location, String description) {
        String wt = workplaceType == null ? "" : workplaceType.toLowerCase();
        if (wt.contains("remote")) {
            return true;
        }
        String loc = location == null ? "" : location.toLowerCase();
        return loc.contains("remote");
    }

    private static Instant epochMillis(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return Instant.ofEpochMilli(node.asLong());
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

