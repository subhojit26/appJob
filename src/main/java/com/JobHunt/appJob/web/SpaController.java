package com.JobHunt.appJob.web;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
/**
 * Serves the React single-page app. Any non-API, non-asset route is forwarded to the SPA's
 * index.html so client-side routing (React Router) can take over. Active only under the
 * dashboard run profile.
 */
@Controller
@ConditionalOnProperty(name = "jobwatch.dashboard.enabled", havingValue = "true")
public class SpaController {
    @GetMapping({
            "/",
            "/analytics",
            "/jobs",
            "/profiles",
            "/profiles/new",
            "/profiles/{id}/edit"
    })
    public String spa() {
        return "forward:/index.html";
    }
}