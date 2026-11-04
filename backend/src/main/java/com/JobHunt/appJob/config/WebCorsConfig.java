package com.JobHunt.appJob.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS policy for dashboard API endpoints.
 * Allows the Vercel frontend (and local dev UI) to call /api/*.
 */
@Configuration
@ConditionalOnProperty(name = "jobwatch.dashboard.enabled", havingValue = "true")
public class WebCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

    public WebCorsConfig(@Value("${jobwatch.cors.allowed-origins:}") String allowedOriginsCsv,
                         @Value("${jobwatch.cors.allowed-origin-patterns:}") String allowedOriginPatternsCsv) {
        this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        this.allowedOriginPatterns = Arrays.stream(allowedOriginPatternsCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0 && allowedOriginPatterns.length == 0) {
            return;
        }

        var mapping = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        if (allowedOrigins.length > 0) {
            mapping.allowedOrigins(allowedOrigins);
        }
        if (allowedOriginPatterns.length > 0) {
            mapping.allowedOriginPatterns(allowedOriginPatterns);
        }
    }
}

