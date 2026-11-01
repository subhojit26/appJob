package com.JobHunt.appJob.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Canonicalizes free-text job locations so synonyms collapse into one facet value
 * (e.g. "Bangalore"/"Bengaluru, Karnataka, India" → "Bengaluru"; "US"/"USA"/"United States of
 * America" → "United States"). Used by the dashboard facets and filtering.
 */
public final class LocationNormalizer {

    private LocationNormalizer() {}

    /** Canonical city/country keyed by a lowercase keyword that may appear in the raw location. */
    private static final Map<String, String> KEYWORDS = Map.ofEntries(
            // India cities
            Map.entry("bengaluru", "Bengaluru"),
            Map.entry("bangalore", "Bengaluru"),
            Map.entry("gurugram", "Gurugram"),
            Map.entry("gurgaon", "Gurugram"),
            Map.entry("mumbai", "Mumbai"),
            Map.entry("bombay", "Mumbai"),
            Map.entry("new delhi", "Delhi"),
            Map.entry("delhi", "Delhi"),
            Map.entry("noida", "Noida"),
            Map.entry("pune", "Pune"),
            Map.entry("hyderabad", "Hyderabad"),
            Map.entry("chennai", "Chennai"),
            Map.entry("kolkata", "Kolkata"),
            Map.entry("ahmedabad", "Ahmedabad"),
            Map.entry("india", "India"),
            // Countries / regions
            Map.entry("united states of america", "United States"),
            Map.entry("united states", "United States"),
            Map.entry("u.s.a", "United States"),
            Map.entry("usa", "United States"),
            Map.entry("u.s.", "United States"),
            Map.entry("united kingdom", "United Kingdom"),
            Map.entry("u.k.", "United Kingdom"),
            Map.entry("germany", "Germany"),
            Map.entry("canada", "Canada")
    );

    /** Keyword rules checked in order (longest/most-specific first) for substring matches. */
    private static final List<Map.Entry<String, String>> ORDERED = KEYWORDS.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
            .toList();

    /**
     * Returns a canonical location label for a raw location string.
     *
     * <ul>
     *   <li>Remote-ish text → {@code "Remote"}.</li>
     *   <li>Known synonyms (bangalore/bengaluru, us/usa/united states, …) → a single canonical name.</li>
     *   <li>Otherwise the first comma-separated segment, title-cased.</li>
     *   <li>Blank/unknown → {@code "(unknown)"}.</li>
     * </ul>
     */
    public static String canonical(String raw) {
        if (raw == null || raw.isBlank()) {
            return "(unknown)";
        }
        String lower = raw.toLowerCase(Locale.ROOT).trim();

        if (lower.contains("remote") || lower.contains("anywhere")
                || lower.contains("work from home") || lower.equals("wfh")) {
            return "Remote";
        }
        // Special-case bare "us" token (avoid matching words like "august").
        for (String tok : lower.split("[,/|\\s]+")) {
            if (tok.equals("us")) {
                return "United States";
            }
        }
        for (Map.Entry<String, String> e : ORDERED) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        // Fallback: first segment before a comma, title-cased.
        String first = raw.split(",")[0].trim();
        if (first.isEmpty()) {
            return "(unknown)";
        }
        return titleCase(first);
    }

    private static String titleCase(String s) {
        String[] parts = s.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}

