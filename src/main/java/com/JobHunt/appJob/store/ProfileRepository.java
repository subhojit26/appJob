package com.JobHunt.appJob.store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JDBC repository for the user-editable {@code profiles} table (Phase 10 increment).
 * Lists are stored comma-separated.
 */
@Repository
public class ProfileRepository {

    private final JdbcClient jdbc;

    public ProfileRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public boolean isEmpty() {
        Long n = jdbc.sql("SELECT COUNT(*) FROM profiles").query(Long.class).single();
        return n == null || n == 0;
    }

    public List<ProfileRow> findAll() {
        return jdbc.sql("SELECT * FROM profiles ORDER BY name").query(ProfileRepository::mapRow).list();
    }

    public List<ProfileRow> findAllEnabled() {
        return jdbc.sql("SELECT * FROM profiles WHERE enabled = 1 ORDER BY name")
                .query(ProfileRepository::mapRow).list();
    }

    public Optional<ProfileRow> findById(long id) {
        return jdbc.sql("SELECT * FROM profiles WHERE id = :id")
                .param("id", id).query(ProfileRepository::mapRow).optional();
    }

    /** Inserts a new profile and returns the generated id. */
    public long insert(ProfileRow p) {
        String now = Instant.now().toString();
        jdbc.sql("""
                INSERT INTO profiles (name, include_title, stack, exclude_title, companies,
                                      exp_min, exp_max, enabled, created_at, updated_at)
                VALUES (:name, :include, :stack, :exclude, :companies, :expMin, :expMax, :enabled, :now, :now)
                """)
                .param("name", p.name())
                .param("include", join(p.includeTitle()))
                .param("stack", join(p.stack()))
                .param("exclude", join(p.excludeTitle()))
                .param("companies", join(p.companies()))
                .param("expMin", p.expMin())
                .param("expMax", p.expMax())
                .param("enabled", p.enabled() ? 1 : 0)
                .param("now", now)
                .update();
        Long id = jdbc.sql("SELECT id FROM profiles WHERE name = :name")
                .param("name", p.name()).query(Long.class).single();
        return id == null ? -1 : id;
    }

    /** Updates an existing profile by id. */
    public void update(long id, ProfileRow p) {
        jdbc.sql("""
                UPDATE profiles SET name = :name, include_title = :include, stack = :stack,
                       exclude_title = :exclude, companies = :companies, exp_min = :expMin,
                       exp_max = :expMax, enabled = :enabled, updated_at = :now
                 WHERE id = :id
                """)
                .param("name", p.name())
                .param("include", join(p.includeTitle()))
                .param("stack", join(p.stack()))
                .param("exclude", join(p.excludeTitle()))
                .param("companies", join(p.companies()))
                .param("expMin", p.expMin())
                .param("expMax", p.expMax())
                .param("enabled", p.enabled() ? 1 : 0)
                .param("now", Instant.now().toString())
                .param("id", id)
                .update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM profiles WHERE id = :id").param("id", id).update();
    }

    /**
     * Makes exactly one profile active (FR-39a): clears {@code is_active} on all rows, then sets it
     * on the given id. Only the active profile's jobs are emailed (see the digest gate).
     */
    public void setActive(long id) {
        jdbc.sql("UPDATE profiles SET is_active = 0").update();
        jdbc.sql("UPDATE profiles SET is_active = 1 WHERE id = :id").param("id", id).update();
    }

    /**
     * Toggles the active flag on a single profile without affecting others, so the user can keep
     * <strong>multiple</strong> profiles active at once. Only active profiles' jobs are shown and
     * emailed (see the digest gate).
     */
    public void toggleActive(long id) {
        jdbc.sql("UPDATE profiles SET is_active = CASE WHEN is_active = 1 THEN 0 ELSE 1 END WHERE id = :id")
                .param("id", id).update();
    }

    /** The id of the active profile, or {@code null} if none is active. */
    public Long activeProfileId() {
        return jdbc.sql("SELECT id FROM profiles WHERE is_active = 1 LIMIT 1")
                .query(Long.class).optional().orElse(null);
    }

    /** All active profile ids (may be several — multi-select). */
    public List<Long> activeProfileIds() {
        return jdbc.sql("SELECT id FROM profiles WHERE is_active = 1").query(Long.class).list();
    }

    /** The name of the active profile, or {@code null} if none is active. */
    public String activeProfileName() {
        return jdbc.sql("SELECT name FROM profiles WHERE is_active = 1 LIMIT 1")
                .query(String.class).optional().orElse(null);
    }

    /** All active profile names (may be several — multi-select). */
    public List<String> activeProfileNames() {
        return jdbc.sql("SELECT name FROM profiles WHERE is_active = 1 ORDER BY name")
                .query(String.class).list();
    }

    /**
     * Guarantees at most/at least one active profile: if none is active yet but profiles exist,
     * the first (alphabetical) enabled profile is activated as a sensible default.
     */
    public void ensureOneActive() {
        if (activeProfileId() != null) {
            return;
        }
        Long first = jdbc.sql("SELECT id FROM profiles WHERE enabled = 1 ORDER BY name LIMIT 1")
                .query(Long.class).optional().orElse(null);
        if (first != null) {
            setActive(first);
        }
    }

    private static String join(List<String> list) {
        return (list == null || list.isEmpty()) ? null : String.join(",", list);
    }

    private static List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private static ProfileRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Integer expMin = (Integer) rs.getObject("exp_min");
        Integer expMax = (Integer) rs.getObject("exp_max");
        return new ProfileRow(
                rs.getLong("id"),
                rs.getString("name"),
                split(rs.getString("include_title")),
                split(rs.getString("stack")),
                split(rs.getString("exclude_title")),
                split(rs.getString("companies")),
                expMin,
                expMax,
                rs.getInt("enabled") == 1
        );
    }
}

