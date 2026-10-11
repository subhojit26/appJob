package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.config.AppProperties;
import com.JobHunt.appJob.store.ProfileRepository;
import com.JobHunt.appJob.store.ProfileRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time seed of the DB-backed {@code profiles} table from the {@code application.yml} profiles
 * (Phase 10 increment). Runs only when the table is empty, so existing behaviour is preserved and
 * the DB becomes the editable source of truth thereafter.
 *
 * <p>Ordered before {@link RunNowRunner} (Order 1) so a one-shot run uses the seeded profiles.</p>
 */
@Component
@Order(0)
public class ProfileSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfileSeeder.class);

    private final ProfileRepository profileRepository;
    private final AppProperties appProperties;

    public ProfileSeeder(ProfileRepository profileRepository, AppProperties appProperties) {
        this.profileRepository = profileRepository;
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!profileRepository.isEmpty()) {
            return;
        }
        List<AppProperties.ProfileConfig> configs = appProperties.getProfiles();
        if (configs == null || configs.isEmpty()) {
            log.info("No application.yml profiles to seed into the DB");
            return;
        }
        int seeded = 0;
        for (AppProperties.ProfileConfig c : configs) {
            ProfileRow row = new ProfileRow(
                    null,
                    c.getName(),
                    c.getIncludeTitle(),
                    c.getStack(),
                    c.getExcludeTitle(),
                    List.of(),              // preferred companies: user fills these in via the UI
                    c.getExpMin(),
                    c.getExpMax(),
                    true);
            try {
                profileRepository.insert(row);
                seeded++;
            } catch (Exception e) {
                log.warn("Could not seed profile '{}': {}", c.getName(), e.toString());
            }
        }
        log.info("Seeded {} role profile(s) from application.yml into the DB", seeded);
    }
}

