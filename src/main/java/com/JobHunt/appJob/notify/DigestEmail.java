package com.JobHunt.appJob.notify;

import com.JobHunt.appJob.model.Job;

import java.util.List;
import java.util.Map;

/**
 * Represents a digest email ready to send, with jobs grouped by profile and additional metadata.
 */
public record DigestEmail(
        String subject,
        String htmlBody,
        String textBody,
        List<Job> jobs,
        Map<String, Integer> jobsByProfile,
        String sourceHealthLine
) {
    /**
     * Convenience factory for building a digest with jobs grouped by profile and a health line.
     */
    public static DigestEmail of(
            String subject,
            String htmlBody,
            String textBody,
            List<Job> jobs,
            Map<String, Integer> jobsByProfile,
            String sourceHealthLine) {
        return new DigestEmail(subject, htmlBody, textBody, jobs, jobsByProfile, sourceHealthLine);
    }
}

