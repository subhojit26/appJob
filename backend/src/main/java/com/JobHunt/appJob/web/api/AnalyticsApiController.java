package com.JobHunt.appJob.web.api;
import com.JobHunt.appJob.pipeline.JobWatchScheduler;
import com.JobHunt.appJob.pipeline.RunSummary;
import com.JobHunt.appJob.web.AnalyticsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "jobwatch.dashboard.enabled", havingValue = "true")
public class AnalyticsApiController {
    private final AnalyticsRepository analytics;
    private final JobWatchScheduler scheduler;
    public AnalyticsApiController(AnalyticsRepository analytics, JobWatchScheduler scheduler) {
        this.analytics = analytics;
        this.scheduler = scheduler;
    }
    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        Instant now = Instant.now();
        List<AnalyticsRepository.LabelCount> seen = analytics.seenPerDay(30).reversed();
        List<AnalyticsRepository.LabelCount> emailed = analytics.emailedPerDay(30).reversed();
        return Map.ofEntries(
                Map.entry("totalJobs", analytics.totalJobs()),
                Map.entry("totalMatched", analytics.totalMatched()),
                Map.entry("totalEmailed", analytics.totalEmailed()),
                Map.entry("totalApplied", analytics.totalApplied()),
                Map.entry("seenToday", analytics.seenSince(now.minus(Duration.ofDays(1)))),
                Map.entry("seenWeek", analytics.seenSince(now.minus(Duration.ofDays(7)))),
                Map.entry("seenMonth", analytics.seenSince(now.minus(Duration.ofDays(30)))),
                Map.entry("byProfile", analytics.countByProfile()),
                Map.entry("bySource", analytics.countBySource()),
                Map.entry("topCompanies", analytics.topCompanies(10)),
                Map.entry("seenSeries", seen),
                Map.entry("emailedSeries", emailed));
    }
    @GetMapping("/jobs")
    public Map<String, Object> jobs(@RequestParam(required = false) String location,
                                    @RequestParam(required = false) Boolean remote,
                                    @RequestParam(required = false) String profile,
                                    @RequestParam(required = false) String source,
                                    @RequestParam(required = false) Boolean applied) {
        AnalyticsRepository.JobsFilter filter =
                AnalyticsRepository.JobsFilter.of(location, remote, profile, source, applied);
        return Map.of(
                "filter", filter,
                "total", analytics.filteredCount(filter),
                "jobs", analytics.filteredEmailed(filter, 500),
                "locationFacet", analytics.facet("location", filter),
                "profileFacet", analytics.facet("profile", filter),
                "remoteFacet", analytics.remoteFacet(filter));
    }
    @PostMapping("/jobs/{id}/applied")
    public Map<String, Object> toggleApplied(@PathVariable long id, @RequestParam boolean applied) {
        analytics.setApplied(id, applied);
        return Map.of("id", id, "applied", applied);
    }

    @PostMapping("/jobs/reload")
    public ResponseEntity<Map<String, Object>> reloadJobsNow() {
        boolean started = scheduler.triggerManualRunAsync();
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("started", false, "running", true, "message", "A pipeline run is already in progress."));
        }

        return ResponseEntity.accepted().body(Map.of(
                "started", true,
                "running", true,
                "message", "Reload started. Fetching fresh jobs now."));
    }

    @GetMapping("/jobs/reload/status")
    public Map<String, Object> reloadJobsStatus() {
        AnalyticsRepository.JobsFilter filter = AnalyticsRepository.JobsFilter.of(null, null, null, null, null);
        long total = analytics.filteredCount(filter);
        JobWatchScheduler.TriggerResult last = scheduler.lastManualResult();
        RunSummary summary = last.summary();

        return Map.of(
                "running", scheduler.isRunning(),
                "success", last.success(),
                "total", total,
                "rawFetched", summary == null ? 0 : summary.rawFetched(),
                "afterDedupe", summary == null ? 0 : summary.afterDedupe(),
                "eligible", summary == null ? 0 : summary.eligible(),
                "matched", summary == null ? 0 : summary.matched(),
                "newJobs", summary == null ? 0 : summary.newJobs(),
                "error", last.error() == null ? "" : last.error());
    }
}