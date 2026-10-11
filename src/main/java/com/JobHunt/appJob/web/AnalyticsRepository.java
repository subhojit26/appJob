package com.JobHunt.appJob.web;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

/**
 * Read-only analytics queries over the {@code jobs} table for the dashboard (Phase 9 / §7.11).
 */
@Repository
public class AnalyticsRepository {

    /** A label with an associated count (per-profile, per-source, per-day, etc.). */
    public record LabelCount(String label, long count) {}

    /** A compact job row for the recent-jobs table. */
    public record JobRow(long id, String title, String company, String location, boolean remote,
                         String source, String profile, Integer score, String applyUrl,
                         String when, String postedAt, boolean applied) {}

    private final JdbcClient jdbc;
    private final com.JobHunt.appJob.store.ProfileRepository profiles;

    public AnalyticsRepository(DataSource dataSource,
                               com.JobHunt.appJob.store.ProfileRepository profiles) {
        this.jdbc = JdbcClient.create(dataSource);
        this.profiles = profiles;
    }

    public long totalJobs() {
        return count("SELECT COUNT(*) FROM jobs");
    }

    public long totalEmailed() {
        return count("SELECT COUNT(*) FROM jobs WHERE notified_at IS NOT NULL");
    }

    public long totalApplied() {
        return count("SELECT COUNT(*) FROM jobs WHERE applied_at IS NOT NULL");
    }

    public long totalMatched() {
        return count("SELECT COUNT(*) FROM jobs WHERE profile IS NOT NULL");
    }

    /** Openings seen since {@code cutoff}, by posted date (falling back to fetched date). */
    public long seenSince(Instant cutoff) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM jobs
                WHERE COALESCE(posted_at, fetched_at) >= :cutoff
                """)
                .param("cutoff", cutoff.toString())
                .query(Long.class)
                .single();
    }

    public List<LabelCount> countByProfile() {
        return jdbc.sql("""
                SELECT COALESCE(profile, '(unmatched)') AS label, COUNT(*) AS c
                FROM jobs GROUP BY profile ORDER BY c DESC
                """)
                .query((rs, n) -> new LabelCount(rs.getString("label"), rs.getLong("c")))
                .list();
    }

    public List<LabelCount> countBySource() {
        return jdbc.sql("SELECT source AS label, COUNT(*) AS c FROM jobs GROUP BY source ORDER BY c DESC")
                .query((rs, n) -> new LabelCount(rs.getString("label"), rs.getLong("c")))
                .list();
    }

    public List<LabelCount> topCompanies(int limit) {
        return jdbc.sql("""
                SELECT COALESCE(company, '(unknown)') AS label, COUNT(*) AS c
                FROM jobs GROUP BY company ORDER BY c DESC LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, n) -> new LabelCount(rs.getString("label"), rs.getLong("c")))
                .list();
    }

    /** Jobs emailed per calendar day (UTC), most recent last, limited to {@code days} back. */
    public List<LabelCount> emailedPerDay(int days) {
        return jdbc.sql("""
                SELECT substr(notified_at, 1, 10) AS label, COUNT(*) AS c
                FROM jobs
                WHERE notified_at IS NOT NULL
                GROUP BY substr(notified_at, 1, 10)
                ORDER BY label DESC
                LIMIT :days
                """)
                .param("days", days)
                .query((rs, n) -> new LabelCount(rs.getString("label"), rs.getLong("c")))
                .list();
    }

    /** New openings seen per calendar day (UTC), most recent last, limited to {@code days} back. */
    public List<LabelCount> seenPerDay(int days) {
        return jdbc.sql("""
                SELECT substr(COALESCE(posted_at, fetched_at), 1, 10) AS label, COUNT(*) AS c
                FROM jobs
                WHERE COALESCE(posted_at, fetched_at) IS NOT NULL
                GROUP BY label
                ORDER BY label DESC
                LIMIT :days
                """)
                .param("days", days)
                .query((rs, n) -> new LabelCount(rs.getString("label"), rs.getLong("c")))
                .list();
    }

    /** Most recent matched jobs for the dashboard table. */
    public List<JobRow> recentMatched(int limit) {
        return jdbc.sql("""
                SELECT id, title, company, location, remote, source, profile, score, apply_url,
                       COALESCE(posted_at, fetched_at) AS whn, posted_at, applied_at
                FROM jobs
                WHERE profile IS NOT NULL
                ORDER BY COALESCE(posted_at, fetched_at) DESC, id DESC
                LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, n) -> new JobRow(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("company"),
                        rs.getString("location"),
                        rs.getInt("remote") != 0,
                        rs.getString("source"),
                        rs.getString("profile"),
                        (Integer) rs.getObject("score"),
                        rs.getString("apply_url"),
                        rs.getString("whn"),
                        rs.getString("posted_at"),
                        rs.getString("applied_at") != null))
                .list();
    }

    /** Marks a job applied/un-applied (the dashboard's only write action — FR-37). */
    public void setApplied(long jobId, boolean applied) {
        jdbc.sql("UPDATE jobs SET applied_at = :ts WHERE id = :id")
                .param("ts", applied ? Instant.now().toString() : null)
                .param("id", jobId)
                .update();
    }

    // ---------------------------------------------------------------------------------------------
    // Emailed-jobs table with faceted filters (FR-39b)
    // ---------------------------------------------------------------------------------------------

    /** Selected filters for the emailed-jobs table. Any field may be null (= not filtered). */
    public record JobsFilter(String location, Boolean remote, String profile, String source,
                             Boolean applied) {
        public static JobsFilter of(String location, Boolean remote, String profile,
                                    String source, Boolean applied) {
            String loc = (location == null || location.isBlank()) ? null : location;
            String prof = (profile == null || profile.isBlank()) ? null : profile;
            String src = (source == null || source.isBlank()) ? null : source;
            return new JobsFilter(loc, remote, prof, src, applied);
        }
        public boolean isEmpty() {
            return location == null && remote == null && profile == null && source == null && applied == null;
        }
    }

    /**
     * Builds a WHERE clause over emailed jobs for the given filter, optionally skipping one
     * dimension (used when counting that dimension's own facet). Params are added to {@code params}.
     */
    private String buildWhere(JobsFilter f, String skip, java.util.Map<String, Object> params) {
        StringBuilder sb = new StringBuilder("WHERE notified_at IS NOT NULL");
        // Freshness window: hide stale postings (older than FRESH_DAYS) from the dashboard too.
        sb.append(" AND datetime(COALESCE(posted_at, fetched_at)) >= datetime(:freshCutoff)");
        params.put("freshCutoff",
                java.time.Instant.now().minus(java.time.Duration.ofDays(
                        com.JobHunt.appJob.store.JobRepository.FRESH_DAYS)).toString());
        // Active-profile gate: only show jobs matching the user's currently active profile(s).
        List<String> activeNames = profiles.activeProfileNames();
        if (!activeNames.isEmpty()) {
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < activeNames.size(); i++) {
                String key = "activeP" + i;
                if (i > 0) in.append(", ");
                in.append(":").append(key);
                params.put(key, activeNames.get(i));
            }
            sb.append(" AND profile IN (").append(in).append(")");
        }
        if (f.location() != null && !"location".equals(skip)) {
            sb.append(" AND location = :location");
            params.put("location", f.location());
        }
        if (f.remote() != null && !"remote".equals(skip)) {
            sb.append(" AND remote = :remote");
            params.put("remote", f.remote() ? 1 : 0);
        }
        if (f.profile() != null && !"profile".equals(skip)) {
            sb.append(" AND profile = :profile");
            params.put("profile", f.profile());
        }
        if (f.source() != null && !"source".equals(skip)) {
            sb.append(" AND source = :source");
            params.put("source", f.source());
        }
        if (f.applied() != null && !"applied".equals(skip)) {
            sb.append(f.applied() ? " AND applied_at IS NOT NULL" : " AND applied_at IS NULL");
        }
        return sb.toString();
    }

    private org.springframework.jdbc.core.simple.JdbcClient.StatementSpec bind(
            org.springframework.jdbc.core.simple.JdbcClient.StatementSpec spec,
            java.util.Map<String, Object> params) {
        for (var e : params.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        return spec;
    }

    /** Emailed jobs matching the filter, newest first, limited. */
    public List<JobRow> filteredEmailed(JobsFilter f, int limit) {
        var params = new java.util.LinkedHashMap<String, Object>();
        String where = buildWhere(f, null, params);
        var spec = jdbc.sql("""
                SELECT id, title, company, location, remote, source, profile, score, apply_url,
                       COALESCE(notified_at, posted_at, fetched_at) AS whn, posted_at, applied_at
                FROM jobs
                """ + where + """
                 ORDER BY datetime(COALESCE(notified_at, posted_at, fetched_at)) DESC, id DESC
                 LIMIT :limit
                """);
        params.put("limit", limit);
        return bind(spec, params)
                .query((rs, n) -> new JobRow(
                        rs.getLong("id"), rs.getString("title"), rs.getString("company"),
                        rs.getString("location"), rs.getInt("remote") != 0, rs.getString("source"),
                        rs.getString("profile"), (Integer) rs.getObject("score"),
                        rs.getString("apply_url"), rs.getString("whn"), rs.getString("posted_at"),
                        rs.getString("applied_at") != null))
                .list();
    }

    /** Count of emailed jobs matching the full filter. */
    public long filteredCount(JobsFilter f) {
        var params = new java.util.LinkedHashMap<String, Object>();
        String where = buildWhere(f, null, params);
        Long n = bind(jdbc.sql("SELECT COUNT(*) FROM jobs " + where), params)
                .query(Long.class).single();
        return n == null ? 0 : n;
    }

    /** Facet counts for a text column (location/profile/source), honoring the other active filters. */
    public List<LabelCount> facet(String column, JobsFilter f) {
        var params = new java.util.LinkedHashMap<String, Object>();
        String where = buildWhere(f, column, params);
        String expr = "location".equals(column) ? "COALESCE(location, '(unknown)')"
                : "source".equals(column) ? "source"
                : "COALESCE(profile, '(unmatched)')";
        var spec = jdbc.sql("SELECT " + expr + " AS label, COUNT(*) AS c FROM jobs "
                + where + " GROUP BY label ORDER BY c DESC LIMIT 30");
        return bind(spec, params)
                .query((rs, n) -> new LabelCount(rs.getString("label"), rs.getLong("c")))
                .list();
    }

    /** Remote-vs-onsite facet counts, honoring the other active filters. */
    public List<LabelCount> remoteFacet(JobsFilter f) {
        var params = new java.util.LinkedHashMap<String, Object>();
        String where = buildWhere(f, "remote", params);
        var spec = jdbc.sql("""
                SELECT CASE WHEN remote != 0 THEN 'Remote' ELSE 'On-site' END AS label,
                       COUNT(*) AS c FROM jobs
                """ + where + " GROUP BY label ORDER BY c DESC");
        return bind(spec, params)
                .query((rs, n) -> new LabelCount(rs.getString("label"), rs.getLong("c")))
                .list();
    }

    private long count(String sql) {
        Long n = jdbc.sql(sql).query(Long.class).single();
        return n == null ? 0 : n;
    }
}

