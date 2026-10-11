package com.JobHunt.appJob.source;

import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.http.HttpJobFetcher;
import org.jsoup.Jsoup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Ashby job-board API payloads into normalized {@link Job} records.
 *
 * <p>Endpoint (added later):
 * {@code GET https://api.ashbyhq.com/posting-api/job-board/{slug}?includeCompensation=true}</p>
 *
 * <p>Parse-only stage (no HTTP), matching {@link GreenhouseJobSource} and
 * {@link LeverJobSource}. The payload is an object with a {@code jobs} array. Tests feed the
 * saved {@code ashby-*.json} fixtures directly into {@link #parse(String)}.</p>
 *
 * <p>Ashby does not include the company name in the payload, so the caller supplies a
 * display name via the constructor (slug &rarr; display-name approach).</p>
 *
 * <p>Field mapping (Ashby &rarr; {@link Job}):</p>
 * <ul>
 *   <li>{@code id} &rarr; {@code sourceJobId}</li>
 *   <li>{@code title} &rarr; {@code title}</li>
 *   <li>constructor {@code company} &rarr; {@code company}</li>
 *   <li>{@code location} &rarr; {@code location}</li>
 *   <li>{@code isRemote} / {@code workplaceType} / {@code secondaryLocations[].location} &rarr; {@code remote}</li>
 *   <li>{@code workplaceType} &rarr; {@code workplaceType}</li>
 *   <li>{@code employmentType} &rarr; {@code employmentType}</li>
 *   <li>{@code publishedAt} &rarr; {@code postedAt}</li>
 *   <li>{@code jobUrl} &rarr; {@code jobUrl}; {@code applyUrl} &rarr; {@code applyUrl}</li>
 *   <li>{@code descriptionPlain} &rarr; {@code description} (falls back to stripped {@code descriptionHtml})</li>
 *   <li>{@code compensation.summaryComponents[type=Salary]} &rarr; {@code salaryMin/salaryMax/salaryCurrency}</li>
 * </ul>
 *
 * <p>Experience is not provided by Ashby and is left {@code null}.</p>
 */
public class AshbyJobSource implements JobSource {

    /** Logical source name used for logging, dedupe, and {@link Job#source()}. */
    public static final String SOURCE = "ashby";

    /** Default Ashby API host; overridable in tests to point at a mock server. */
    static final String DEFAULT_BASE_URL = "https://api.ashbyhq.com";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final String companySlug;
    private final String company;
    private final HttpJobFetcher fetcher;
    private final String baseUrl;

    /**
     * Parse-only constructor. Use this when you only need {@link #parse(String)} (e.g. the
     * fixture tests). {@link #fetchJobs()} is unavailable because there is no fetcher/slug.
     *
     * @param company display name for this Ashby board (e.g. "OpenAI"); Ashby's API does
     *                not return it, so it is supplied here. May be {@code null}.
     */
    public AshbyJobSource(String company) {
        this(null, company, null, DEFAULT_BASE_URL);
    }

    /**
     * Production constructor.
     *
     * @param companySlug Ashby board slug (e.g. "openai")
     * @param company     display name for this board (Ashby's payload omits it)
     * @param fetcher     shared HTTP helper owning timeouts/retries/concurrency
     */
    public AshbyJobSource(String companySlug, String company, HttpJobFetcher fetcher) {
        this(companySlug, company, fetcher, DEFAULT_BASE_URL);
    }

    /** Test/advanced constructor allowing a custom base URL (e.g. a mock server). */
    AshbyJobSource(String companySlug, String company, HttpJobFetcher fetcher, String baseUrl) {
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
                    "AshbyJobSource was created parse-only; provide a companySlug and HttpJobFetcher to fetch.");
        }
        // fetchJobs = fetch + parse. All network behavior lives in HttpJobFetcher.
        String body = fetcher.fetch(boardUrl());
        return parse(body);
    }

    /** Builds the Ashby job-board endpoint for this source's slug. */
    String boardUrl() {
        return baseUrl + "/posting-api/job-board/" + companySlug + "?includeCompensation=true";
    }

    /**
     * Parses an Ashby job-board payload (object with a {@code jobs} array) into normalized jobs.
     *
     * @param json raw JSON string from the Ashby job-board endpoint or a fixture
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

    /** Maps a single Ashby posting to a {@link Job}, or {@code null} if unusable. */
    private Job toJob(JsonNode node, Instant fetchedAt) {
        String sourceJobId = text(node.path("id"));
        String title = text(node.path("title"));
        if (sourceJobId == null || title == null) {
            // Skip records missing an id or title rather than failing the whole parse.
            return null;
        }

        String location = text(node.path("location"));
        String workplaceType = text(node.path("workplaceType"));
        String employmentType = text(node.path("employmentType"));
        String applyUrl = text(node.path("applyUrl"));
        String jobUrl = text(node.path("jobUrl"));
        String description = description(node);
        Instant postedAt = parseInstant(text(node.path("publishedAt")));
        boolean remote = isRemote(node, workplaceType, location);

        double[] salary = salary(node.path("compensation"));
        Double salaryMin = salary == null ? null : salary[0];
        Double salaryMax = salary == null ? null : salary[1];
        String salaryCurrency = salaryCurrency(node.path("compensation"));

        return Job.builder()
                .source(SOURCE)
                .sourceJobId(sourceJobId.trim())
                .title(title.trim())
                .company(company == null ? null : company.trim())
                .location(location == null ? null : location.trim())
                .remote(remote)
                .workplaceType(workplaceType)
                .applyUrl(applyUrl)
                .jobUrl(jobUrl)
                .description(description)
                .postedAt(postedAt)
                .employmentType(employmentType)
                .salaryMin(salaryMin)
                .salaryMax(salaryMax)
                .salaryCurrency(salaryCurrency)
                .fetchedAt(fetchedAt)
                .build();
    }

    /** Prefers {@code descriptionPlain}; falls back to stripping {@code descriptionHtml}. */
    private static String description(JsonNode node) {
        String plain = text(node.path("descriptionPlain"));
        if (plain != null) {
            return plain;
        }
        String html = text(node.path("descriptionHtml"));
        return html == null ? null : Jsoup.parse(html).text();
    }

    /**
     * Remote if the boolean {@code isRemote} flag is true, the workplace type says remote,
     * the primary location mentions remote, or any {@code secondaryLocations} entry is remote.
     */
    private static boolean isRemote(JsonNode node, String workplaceType, String location) {
        JsonNode isRemote = node.path("isRemote");
        if (isRemote.isBoolean() && isRemote.asBoolean()) {
            return true;
        }
        if (workplaceType != null && workplaceType.toLowerCase().contains("remote")) {
            return true;
        }
        if (location != null && location.toLowerCase().contains("remote")) {
            return true;
        }
        return secondaryLocationsHaveRemote(node.path("secondaryLocations"));
    }

    /** Handles {@code secondaryLocations} being either an array or a single object. */
    private static boolean secondaryLocationsHaveRemote(JsonNode secondary) {
        if (secondary.isArray()) {
            for (JsonNode entry : secondary) {
                if (mentionsRemote(text(entry.path("location")))) {
                    return true;
                }
            }
            return false;
        }
        if (secondary.isObject()) {
            return mentionsRemote(text(secondary.path("location")));
        }
        return false;
    }

    private static boolean mentionsRemote(String location) {
        return location != null && location.toLowerCase().contains("remote");
    }

    /**
     * Extracts structured salary from {@code compensation.summaryComponents}, picking the
     * component whose {@code compensationType} is {@code Salary}.
     *
     * @return {@code [min, max]} or {@code null} if no salary component with values exists
     */
    private static double[] salary(JsonNode compensation) {
        JsonNode salaryComponent = salaryComponent(compensation);
        if (salaryComponent == null) {
            return null;
        }
        Double min = decimal(salaryComponent.path("minValue"));
        Double max = decimal(salaryComponent.path("maxValue"));
        if (min == null && max == null) {
            return null;
        }
        return new double[]{min == null ? max : min, max == null ? min : max};
    }

    private static String salaryCurrency(JsonNode compensation) {
        JsonNode salaryComponent = salaryComponent(compensation);
        return salaryComponent == null ? null : text(salaryComponent.path("currencyCode"));
    }

    /** Finds the {@code Salary} entry within {@code compensation.summaryComponents}. */
    private static JsonNode salaryComponent(JsonNode compensation) {
        JsonNode components = compensation.path("summaryComponents");
        if (!components.isArray()) {
            return null;
        }
        for (JsonNode component : components) {
            if ("Salary".equalsIgnoreCase(text(component.path("compensationType")))) {
                return component;
            }
        }
        return null;
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

