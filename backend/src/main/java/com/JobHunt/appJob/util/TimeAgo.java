package com.JobHunt.appJob.util;

import java.time.Duration;
import java.time.Instant;

/**
 * Formats {@link Instant} values as human-readable relative time strings like "Posted 3h ago".
 */
public final class TimeAgo {

    private TimeAgo() {}

    /**
     * Converts an {@link Instant} to a relative time string using the current time.
     *
     * <p>Examples: "12 min ago", "3h ago", "2 days ago".</p>
     *
     * @param instant the time to format
     * @return a human-readable relative time string, or an empty string if {@code instant} is null
     */
    public static String ago(Instant instant) {
        if (instant == null) {
            return "";
        }
        return ago(instant, Instant.now());
    }

    /**
     * Converts an {@link Instant} to a relative time string, measured from a reference point.
     *
     * @param instant the time to format
     * @param referenceTime the time to measure relative to
     * @return a human-readable relative time string
     */
    public static String ago(Instant instant, Instant referenceTime) {
        if (instant == null) {
            return "";
        }
        Duration d = Duration.between(instant, referenceTime);
        long seconds = d.getSeconds();

        if (seconds < 0) {
            // In the future; this shouldn't normally happen
            return "in the future";
        }

        if (seconds < 60) {
            return seconds + " sec ago";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min ago";
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }

        long days = hours / 24;
        if (days < 7) {
            return days + " day" + (days == 1 ? "" : "s") + " ago";
        }

        long weeks = days / 7;
        if (weeks < 4) {
            return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";
        }

        long months = days / 30;
        if (months < 12) {
            return months + " month" + (months == 1 ? "" : "s") + " ago";
        }

        long years = days / 365;
        return years + " year" + (years == 1 ? "" : "s") + " ago";
    }
}

