package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.model.Job;

import java.util.Set;

/**
 * A {@link Job} with signals extracted from free text: experience range and stack tags.
 *
 * <p>Keeps {@link Job} as a pure normalized source record while layering pipeline-computed
 * fields on top without modifying the core model.</p>
 *
 * @param job    the original normalized job
 * @param expMin extracted minimum experience years (null = unknown)
 * @param expMax extracted maximum experience years (null = unknown)
 * @param stack  matched tech-stack tags (may be empty, never null)
 */
public record EnrichedJob(
        Job job,
        Integer expMin,
        Integer expMax,
        Set<String> stack
) {
    public EnrichedJob {
        stack = stack == null ? Set.of() : Set.copyOf(stack);
    }
}

