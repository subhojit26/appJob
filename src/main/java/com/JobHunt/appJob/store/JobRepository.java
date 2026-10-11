package com.JobHunt.appJob.store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JDBC repository for the {@code jobs} table (explicit SQL, no JPA).
 *
 * <p>Timestamps are stored as ISO-8601 text; {@code remote}/{@code salary_estimated} as 0/1.
 * Inserts use {@code INSERT OR IGNORE} so re-seeing the same {@code (source, source_job_id)} is a
 * no-op — a database-level guard complementing the application-level dedupe.</p>
 */
@Repository
public class JobRepository {

    /** Freshness window: only jobs posted/seen within this many days are emailed or shown. */
    public static final int FRESH_DAYS = 3;

    private final JdbcClient jdbc;

    public JobRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** ISO-8601 cutoff instant; jobs older than this are considered stale. */
    private static String freshnessCutoff() {
        return Instant.now().minus(Duration.ofDays(FRESH_DAYS)).toString();
    }

    /**
     * Inserts a job if new. Returns {@code true} if a new row was created, {@code false} if the
     * {@code (source, source_job_id)} already existed.
     */
    public boolean insertIfNew(StoredJob job) {
        Instant createdAt = job.createdAt() != null ? job.createdAt() : Instant.now();
        int rows = jdbc.sql("""
                INSERT OR IGNORE INTO jobs
                    (source, source_job_id, title, company, location, remote, workplace_type,
                     apply_url, job_url, description, posted_at, min_experience_years,
                     max_experience_years, salary_min, salary_max, salary_currency, salary_estimated,
                     employment_type, fetched_at, notified_at, created_at, updated_at, stack,
                    profile, score, applied_at)
                VALUES
                    (:source, :sourceJobId, :title, :company, :location, :remote, :workplaceType,
                     :applyUrl, :jobUrl, :description, :postedAt, :minExp,
                     :maxExp, :salaryMin, :salaryMax, :salaryCurrency, :salaryEstimated,
                     :employmentType, :fetchedAt, :notifiedAt, :createdAt, :updatedAt, :stack,
                     :profile, :score, :appliedAt)
                """)
                .param("source", job.source())
                .param("sourceJobId", job.sourceJobId())
                .param("title", job.title())
                .param("company", job.company())
                .param("location", job.location())
                .param("remote", job.remote() ? 1 : 0)
                .param("workplaceType", job.workplaceType())
                .param("applyUrl", job.applyUrl())
                .param("jobUrl", job.jobUrl())
                .param("description", job.description())
                .param("postedAt", toText(job.postedAt()))
                .param("minExp", job.minExperienceYears())
                .param("maxExp", job.maxExperienceYears())
                .param("salaryMin", job.salaryMin())
                .param("salaryMax", job.salaryMax())
                .param("salaryCurrency", job.salaryCurrency())
                .param("salaryEstimated", job.salaryEstimated() ? 1 : 0)
                .param("employmentType", job.employmentType())
                .param("fetchedAt", toText(job.fetchedAt()))
                .param("notifiedAt", toText(job.notifiedAt()))
                .param("createdAt", toText(createdAt))
                .param("updatedAt", toText(job.updatedAt()))
                .param("stack", joinStack(job.stack()))
                .param("profile", job.profile())
                .param("score", job.score())
                .param("appliedAt", toText(job.appliedAt()))
                .update();
        return rows > 0;
    }

    /**
     * Jobs not yet notified, newest first (by posted/fetched time), limited to {@code limit}.
     *
     * <p>Only "fresh" jobs are returned: those whose effective posted time
     * (COALESCE(posted_at, fetched_at)) is within the last {@link #FRESH_DAYS} days. Stale
     * postings are never emailed (user requirement: no stale jobs).</p>
     *
     * @param limit maximum rows to return (the digest cap)
     */
    public List<StoredJob> findPendingNotifications(int limit) {
        return jdbc.sql("""
                SELECT * FROM jobs
                WHERE notified_at IS NULL
                  AND datetime(COALESCE(posted_at, fetched_at)) >= datetime(:cutoff)
                ORDER BY datetime(COALESCE(posted_at, fetched_at)) DESC, id DESC
                LIMIT :limit
                """)
                .param("cutoff", freshnessCutoff())
                .param("limit", limit)
                .query(JobRepository::mapRow)
                .list();
    }

    /**
     * Pending jobs restricted to the active profile (FR-39a). When {@code activeProfile} is null or
     * blank the gate is disabled and this behaves like {@link #findPendingNotifications(int)}.
     *
     * <p>Applies the same {@link #FRESH_DAYS}-day freshness window as the no-arg variant.</p>
     *
     * @param limit         maximum rows (the digest cap)
     * @param activeProfile the currently active profile name, or null to email all profiles
     */
    public List<StoredJob> findPendingNotifications(int limit, String activeProfile) {
        if (activeProfile == null || activeProfile.isBlank()) {
            return findPendingNotifications(limit);
        }
        return jdbc.sql("""
                SELECT * FROM jobs
                WHERE notified_at IS NULL AND profile = :profile
                  AND datetime(COALESCE(posted_at, fetched_at)) >= datetime(:cutoff)
                ORDER BY datetime(COALESCE(posted_at, fetched_at)) DESC, id DESC
                LIMIT :limit
                """)
                .param("profile", activeProfile)
                .param("cutoff", freshnessCutoff())
                .param("limit", limit)
                .query(JobRepository::mapRow)
                .list();
    }

    /**
     * Pending jobs restricted to several active profiles (multi-select). When the list is null or
     * empty the gate is disabled and this behaves like {@link #findPendingNotifications(int)}.
     *
     * @param limit          maximum rows (the digest cap)
     * @param activeProfiles the currently active profile names, or empty to email all profiles
     */
    public List<StoredJob> findPendingNotifications(int limit, java.util.List<String> activeProfiles) {
        if (activeProfiles == null || activeProfiles.isEmpty()) {
            return findPendingNotifications(limit);
        }
        if (activeProfiles.size() == 1) {
            return findPendingNotifications(limit, activeProfiles.get(0));
        }
        return jdbc.sql("""
                SELECT * FROM jobs
                WHERE notified_at IS NULL AND profile IN (:profiles)
                  AND datetime(COALESCE(posted_at, fetched_at)) >= datetime(:cutoff)
                ORDER BY datetime(COALESCE(posted_at, fetched_at)) DESC, id DESC
                LIMIT :limit
                """)
                .param("profiles", activeProfiles)
                .param("cutoff", freshnessCutoff())
                .param("limit", limit)
                .query(JobRepository::mapRow)
                .list();
    }

    /** Marks exactly the given job ids as notified at {@code when}, also stamping updated_at. */
    public int markNotified(Collection<Long> ids, Instant when) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String ts = toText(when);
        return jdbc.sql("UPDATE jobs SET notified_at = :when, updated_at = :when WHERE id IN (:ids)")
                .param("when", ts)
                .param("ids", ids)
                .update();
    }

    /** Looks up a job by its natural key. */
    public Optional<StoredJob> findBySourceAndId(String source, String sourceJobId) {
        return jdbc.sql("SELECT * FROM jobs WHERE source = :source AND source_job_id = :id")
                .param("source", source)
                .param("id", sourceJobId)
                .query(JobRepository::mapRow)
                .optional();
    }

    private static Instant fromText(String text) {
        return (text == null || text.isBlank()) ? null : Instant.parse(text);
    }

    private static String toText(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Double doubleOrNull(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static StoredJob mapRow(ResultSet rs, int rowNum) throws SQLException {
        return StoredJob.builder()
                .id(rs.getLong("id"))
                .source(rs.getString("source"))
                .sourceJobId(rs.getString("source_job_id"))
                .title(rs.getString("title"))
                .company(rs.getString("company"))
                .location(rs.getString("location"))
                .remote(rs.getInt("remote") != 0)
                .workplaceType(rs.getString("workplace_type"))
                .applyUrl(rs.getString("apply_url"))
                .jobUrl(rs.getString("job_url"))
                .description(rs.getString("description"))
                .postedAt(fromText(rs.getString("posted_at")))
                .minExperienceYears(intOrNull(rs, "min_experience_years"))
                .maxExperienceYears(intOrNull(rs, "max_experience_years"))
                .salaryMin(doubleOrNull(rs, "salary_min"))
                .salaryMax(doubleOrNull(rs, "salary_max"))
                .salaryCurrency(rs.getString("salary_currency"))
                .salaryEstimated(rs.getInt("salary_estimated") != 0)
                .employmentType(rs.getString("employment_type"))
                .fetchedAt(fromText(rs.getString("fetched_at")))
                .notifiedAt(fromText(rs.getString("notified_at")))
                .createdAt(fromText(rs.getString("created_at")))
                .updatedAt(fromText(rs.getString("updated_at")))
                .stack(splitStack(rs.getString("stack")))
                .profile(rs.getString("profile"))
                .score(intOrNull(rs, "score"))
                .appliedAt(fromText(rs.getString("applied_at")))
                .build();
    }

    /** Joins stack tags into a comma-separated string for storage (null if empty). */
    private static String joinStack(java.util.Set<String> stack) {
        return (stack == null || stack.isEmpty()) ? null : String.join(",", stack);
    }

    /** Parses a comma-separated stack string back into a set (empty if blank). */
    private static java.util.Set<String> splitStack(String text) {
        if (text == null || text.isBlank()) {
            return java.util.Set.of();
        }
        return new java.util.LinkedHashSet<>(java.util.Arrays.asList(text.split(",")));
    }
}

