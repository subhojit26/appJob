package com.JobHunt.appJob.web.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight API-only root/health endpoints for uptime checks.
 */
@RestController
@ConditionalOnProperty(name = "jobwatch.dashboard.enabled", havingValue = "true")
public class SystemApiController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "service", "appjob-backend",
                "status", "ok",
                "time", Instant.now().toString(),
                "apiBase", "/api");
    }

    @GetMapping("/healthz")
    public Map<String, Object> healthz() {
        return Map.of(
                "status", "UP",
                "time", Instant.now().toString());
    }
}

