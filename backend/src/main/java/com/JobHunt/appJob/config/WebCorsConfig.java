package com.JobHunt.appJob.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS policy for the public dashboard API.
 * Allows the Vercel frontend and local dev UI to call /api/*.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private static final String DEFAULT_ALLOWED_ORIGINS =
            "https://app-job-frontend-nine.vercel.app,https://appjob.fly.dev,https://*.fly.dev,http://localhost:5173,http://localhost:3000";
    private static final String DEFAULT_ALLOWED_ORIGIN_PATTERNS =
            "https://*.vercel.app,https://*.fly.dev,http://localhost:*";

    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

    public WebCorsConfig(@Value("${jobwatch.cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}") String allowedOriginsCsv,
                         @Value("${jobwatch.cors.allowed-origin-patterns:" + DEFAULT_ALLOWED_ORIGIN_PATTERNS + "}") String allowedOriginPatternsCsv) {
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
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        if (allowedOrigins.length > 0) {
            mapping.allowedOrigins(allowedOrigins);
        }
        if (allowedOriginPatterns.length > 0) {
            mapping.allowedOriginPatterns(allowedOriginPatterns);
        }
    }
}
