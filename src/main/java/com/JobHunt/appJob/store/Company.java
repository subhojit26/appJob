package com.JobHunt.appJob.store;

import java.time.Instant;

/**
 * A row in the {@code companies} table: a discovered ATS board (Phase 6 / FR-1b).
 *
 * @param id              surrogate key (null before insert)
 * @param source          ATS source name: {@code greenhouse}, {@code lever}, or {@code ashby}
 * @param slug            board slug used in the ATS endpoint
 * @param displayName     human-readable company name (nullable; defaults to slug)
 * @param discoveredAt    when this board was first discovered
 * @param lastOkAt        last time the board returned ≥1 posting (nullable)
 * @param consecutive404  consecutive not-found/empty probes (for pruning)
 * @param enabled         whether the fetch loop should include this board
 */
public record Company(
        Long id,
        String source,
        String slug,
        String displayName,
        Instant discoveredAt,
        Instant lastOkAt,
        int consecutive404,
        boolean enabled
) {
}

