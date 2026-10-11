package com.JobHunt.appJob.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TimeAgo} formatting utility.
 */
class TimeAgoTest {

    private static final Instant BASE = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void nullInstantReturnsEmptyString() {
        assertEquals("", TimeAgo.ago(null));
        assertEquals("", TimeAgo.ago(null, BASE));
    }

    @Test
    void secondsAgo() {
        Instant t = BASE.minus(Duration.ofSeconds(30));
        assertEquals("30 sec ago", TimeAgo.ago(t, BASE));
    }

    @Test
    void minutesAgo() {
        Instant t = BASE.minus(Duration.ofMinutes(12));
        assertEquals("12 min ago", TimeAgo.ago(t, BASE));
    }

    @Test
    void hoursAgo() {
        Instant t = BASE.minus(Duration.ofHours(3));
        assertEquals("3h ago", TimeAgo.ago(t, BASE));
    }

    @Test
    void daysAgo() {
        Instant t = BASE.minus(Duration.ofDays(2));
        assertEquals("2 days ago", TimeAgo.ago(t, BASE));
    }

    @Test
    void daysAgoSingular() {
        Instant t = BASE.minus(Duration.ofDays(1));
        assertEquals("1 day ago", TimeAgo.ago(t, BASE));
    }

    @Test
    void weeksAgo() {
        Instant t = BASE.minus(Duration.ofDays(10));
        assertEquals("1 week ago", TimeAgo.ago(t, BASE));
    }

    @Test
    void monthsAgo() {
        Instant t = BASE.minus(Duration.ofDays(60));
        assertEquals("2 months ago", TimeAgo.ago(t, BASE));
    }

    @Test
    void yearsAgo() {
        Instant t = BASE.minus(Duration.ofDays(400));
        assertEquals("1 year ago", TimeAgo.ago(t, BASE));
    }

    @Test
    void futureTime() {
        Instant t = BASE.plus(Duration.ofHours(1));
        assertEquals("in the future", TimeAgo.ago(t, BASE));
    }
}

