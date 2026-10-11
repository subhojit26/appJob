package com.JobHunt.appJob.web.api;

import com.JobHunt.appJob.store.ProfileRepository;
import com.JobHunt.appJob.store.ProfileRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * JSON REST API backing the React dashboard's profile management screens.
 * Loopback-only, single-user; active only under the {@code dashboard} run profile.
 */
@RestController
@RequestMapping("/api/profiles")
@ConditionalOnProperty(name = "jobwatch.dashboard.enabled", havingValue = "true")
public class ProfileApiController {

    private final ProfileRepository profiles;

    public ProfileApiController(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    /** All profiles plus the ids of the currently active ones (multi-select). */
    @GetMapping
    public Map<String, Object> list() {
        profiles.ensureOneActive();
        return Map.of(
                "profiles", profiles.findAll(),
                "activeIds", profiles.activeProfileIds());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileRow> get(@PathVariable long id) {
        return profiles.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ProfileRow create(@Valid @RequestBody ProfilePayload body) {
        long id = profiles.insert(body.toRow(null));
        return profiles.findById(id).orElseThrow();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileRow> update(@PathVariable long id, @Valid @RequestBody ProfilePayload body) {
        if (profiles.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        profiles.update(id, body.toRow(id));
        return ResponseEntity.of(profiles.findById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        profiles.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Toggles a profile's active state (multi-select). */
    @PostMapping("/{id}/toggle-active")
    public Map<String, Object> toggleActive(@PathVariable long id) {
        profiles.toggleActive(id);
        return Map.of("activeIds", profiles.activeProfileIds());
    }

    /** Request body for create/update. List fields accept arrays or comma-separated strings. */
    public record ProfilePayload(
            @NotBlank String name,
            List<String> includeTitle,
            List<String> stack,
            List<String> excludeTitle,
            List<String> companies,
            Integer expMin,
            Integer expMax,
            Boolean enabled) {

        ProfileRow toRow(Long id) {
            return new ProfileRow(
                    id,
                    name == null ? "" : name.trim(),
                    clean(includeTitle),
                    clean(stack),
                    clean(excludeTitle),
                    clean(companies),
                    expMin,
                    expMax,
                    enabled == null || enabled);
        }

        private static List<String> clean(List<String> in) {
            if (in == null) {
                return List.of();
            }
            return in.stream()
                    .flatMap(s -> Arrays.stream(s == null ? new String[0] : s.split(",")))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }
}

