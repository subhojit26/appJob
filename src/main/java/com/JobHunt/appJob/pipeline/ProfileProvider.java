package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.store.ProfileRepository;
import com.JobHunt.appJob.store.ProfileRow;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Supplies the active role profiles for each pipeline run from the DB-backed {@code profiles}
 * table (Phase 10 increment), so edits made in the dashboard take effect on the next run without
 * a restart.
 */
@Component
public class ProfileProvider {

    private final ProfileRepository profileRepository;

    public ProfileProvider(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /** Currently enabled profiles. */
    public List<ProfileRow> activeProfiles() {
        return profileRepository.findAllEnabled();
    }
}

