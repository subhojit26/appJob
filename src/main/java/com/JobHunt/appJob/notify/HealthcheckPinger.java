package com.JobHunt.appJob.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Pings a healthchecks.io URL (or compatible endpoint) to signal successful run completion.
 *
 * <p>Separate success and failure pings allow healthchecks.io to alert if a run fails.</p>
 */
@Service
public class HealthcheckPinger {

    private static final Logger log = LoggerFactory.getLogger(HealthcheckPinger.class);

    private final HttpClient httpClient;
    private final String healthcheckUrl;
    private final boolean enabled;

    public HealthcheckPinger(@Autowired(required = false) HttpClient httpClient,
                           HealthcheckProperties properties) {
        this.httpClient = httpClient;
        this.healthcheckUrl = properties.url();
        this.enabled = httpClient != null && healthcheckUrl != null && !healthcheckUrl.isBlank();
    }

    /**
     * Pings the healthcheck URL to signal a successful run.
     *
     * @return true if the ping succeeded or healthcheck is not configured, false on HTTP error
     */
    public boolean pingSuccess() {
        return ping("");
    }

    /**
     * Pings the healthcheck URL with "/fail" to signal a failed run.
     *
     * @return true if the ping succeeded or healthcheck is not configured, false on HTTP error
     */
    public boolean pingFail() {
        return ping("/fail");
    }

    private boolean ping(String suffix) {
        if (!enabled) {
            log.debug("Healthcheck not configured; skipping ping");
            return true;
        }

        try {
            String url = healthcheckUrl.endsWith("/")
                    ? healthcheckUrl.substring(0, healthcheckUrl.length() - 1)
                    : healthcheckUrl;
            url = url + suffix;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Healthcheck ping succeeded ({})", response.statusCode());
                return true;
            } else {
                log.warn("Healthcheck ping failed with status {}", response.statusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to ping healthcheck URL", e);
            return false;
        }
    }
}

