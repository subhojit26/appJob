package com.JobHunt.appJob.pipeline;

import com.JobHunt.appJob.config.AppProperties;
import com.JobHunt.appJob.dedupe.JobDeduplicator;
import com.JobHunt.appJob.filter.JobMatch;
import com.JobHunt.appJob.model.Job;
import com.JobHunt.appJob.notify.DigestEmail;
import com.JobHunt.appJob.notify.DigestNotificationService;
import com.JobHunt.appJob.notify.DigestBuilder;
import com.JobHunt.appJob.notify.EmailSender;
import com.JobHunt.appJob.orchestration.RunOrchestrator;
import com.JobHunt.appJob.source.JobSource;
import com.JobHunt.appJob.store.JobRepository;
import com.JobHunt.appJob.store.ProfileRepository;
import com.JobHunt.appJob.store.SourceRunRepository;
import com.JobHunt.appJob.store.StoredJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.JobHunt.appJob.store.SqliteTestSupport.dataSource;
import static com.JobHunt.appJob.store.SqliteTestSupport.initSchema;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test: stub sources → pipeline → SQLite → digest.
 *
 * <p>Verifies the wiring per PRD Phase 4 & 5 acceptance criteria:
 * <ul>
 *   <li>Run 1 seeds the DB, new jobs are pending.</li>
 *   <li>Run 2 finds nothing new (idempotent).</li>
 *   <li>Adding a new job makes exactly that job show up pending.</li>
 *   <li>Cross-source duplicate is dropped by dedupe.</li>
 *   <li>After digest, jobs are marked notified.</li>
 *   <li>SMTP failure leaves jobs pending (retry semantics).</li>
 * </ul>
 * </p>
 */
class PipelineIntegrationTest {

    @TempDir Path tempDir;

    private JobRepository jobRepo;
    private SourceRunRepository sourceRunRepo;
    private JobWatchPipeline pipeline;
    private DigestNotificationService digestService;
    private ProfileRepository profileRepo;

    private static final Instant RECENT = Instant.now().minusSeconds(1800); // 30 min ago

    // Mutable stub source list — tests add/replace entries
    private final List<Job> stubJobsA = new ArrayList<>();
    private final List<Job> stubJobsB = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        DataSource ds = dataSource(tempDir.resolve("pipeline.db"));
        initSchema(ds);
        jobRepo = new JobRepository(ds);
        sourceRunRepo = new SourceRunRepository(ds);
        profileRepo = new ProfileRepository(ds);

        FirstRunSeeder seeder = new FirstRunSeeder(sourceRunRepo, jobRepo);
        RunOrchestrator orchestrator = new RunOrchestrator();
        JobDeduplicator deduplicator = new JobDeduplicator();

        // Two stub sources
        JobSource sourceA = new StubSource("source-a", stubJobsA);
        JobSource sourceB = new StubSource("source-b", stubJobsB);

        AppProperties props = minimalAppProperties();

        pipeline = new JobWatchPipeline(
                orchestrator, deduplicator, jobRepo, sourceRunRepo,
                seeder, List.of(sourceA, sourceB), props);

        // Wire DigestNotificationService with a no-render DigestBuilder stub + capturing sender
        DigestBuilder stubBuilder = new StubDigestBuilder();
        EmailSender successSender = new CapturingEmailSender(true);
        digestService = new DigestNotificationService(jobRepo, stubBuilder, successSender,
                profileRepo);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void run1SeedsDb_run2FindsNothing() {
        stubJobsA.add(job("a-1", "source-a", "Java Developer", RECENT));

        // Run 1: first run for source-a → seeds the job as pending
        RunSummary s1 = pipeline.run();
        assertEquals(1, s1.newJobs(), "run 1 should produce 1 new job");

        List<StoredJob> pending1 = jobRepo.findPendingNotifications(50);
        assertEquals(1, pending1.size());

        // Run 2: same job — no new inserts
        RunSummary s2 = pipeline.run();
        assertEquals(0, s2.newJobs(), "run 2 must produce 0 new jobs");
    }

    @Test
    void newJobOnSecondRunAppearsAsPending() {
        stubJobsA.add(job("a-1", "source-a", "Java Developer", RECENT));
        pipeline.run(); // run 1

        // Add a new job before run 2
        stubJobsA.add(job("a-2", "source-a", "Spring Boot Engineer", RECENT));
        RunSummary s2 = pipeline.run();

        assertEquals(1, s2.newJobs());
        List<StoredJob> pending = jobRepo.findPendingNotifications(50);
        // a-1 was pending from run 1 + a-2 now pending = 2
        assertEquals(2, pending.size());
    }

    @Test
    void crossSourceDuplicateIsDropped() {
        // Same logical job from two different sources
        Job fromA = job("a-10", "source-a", "Java Developer at Acme Corp", RECENT);
        Job fromB = job("b-10", "source-b", "Java Developer at Acme Corp", RECENT);
        // Same company+title → dedupe key matches
        stubJobsA.add(fromA);
        stubJobsB.add(fromB);

        RunSummary s1 = pipeline.run();
        // Only one should survive dedupe (the first one kept)
        assertEquals(1, s1.newJobs(), "cross-source duplicate must be deduplicated to 1");
    }

    @Test
    void digestMarksPendingJobsAsNotified() {
        stubJobsA.add(job("a-1", "source-a", "Java Developer", RECENT));
        pipeline.run();

        List<StoredJob> before = jobRepo.findPendingNotifications(50);
        assertEquals(1, before.size());

        boolean sent = digestService.sendPendingDigest(null);
        assertTrue(sent, "digest send should succeed");

        List<StoredJob> after = jobRepo.findPendingNotifications(50);
        assertEquals(0, after.size(), "jobs must be marked notified after successful send");
    }

    @Test
    void smtpFailureLeavesJobsPending() {
        // Re-wire with a failing sender
        DigestBuilder stubBuilder = new StubDigestBuilder();
        EmailSender failSender = new CapturingEmailSender(false);
        DigestNotificationService failingDigestService =
                new DigestNotificationService(jobRepo, stubBuilder, failSender, profileRepo);

        stubJobsA.add(job("a-1", "source-a", "Java Developer", RECENT));
        pipeline.run();

        boolean sent = failingDigestService.sendPendingDigest(null);
        assertFalse(sent);

        List<StoredJob> stillPending = jobRepo.findPendingNotifications(50);
        assertEquals(1, stillPending.size(), "jobs must remain pending on SMTP failure");
    }

    @Test
    void noPendingJobsReturnsTrue() {
        // Nothing fetched → no pending → should return true (nothing to do)
        boolean result = digestService.sendPendingDigest(null);
        assertTrue(result);
    }

    // ── stubs & helpers ───────────────────────────────────────────────────────

    private static Job job(String id, String source, String title, Instant postedAt) {
        return Job.builder()
                .source(source).sourceJobId(id)
                .title(title).company("Acme Corp")
                .location("india").remote(false)
                .applyUrl("https://example.com/" + id)
                .description("Java and Spring Boot experience required.")
                .postedAt(postedAt).fetchedAt(Instant.now())
                .build();
    }

    /** Minimal AppProperties with one matching profile and permissive filters. */
    private static AppProperties minimalAppProperties() {
        var profile = new AppProperties.ProfileConfig();
        profile.setName("backend-java");
        profile.setIncludeTitle(List.of("java", "spring", "engineer"));
        profile.setStack(List.of("java", "spring boot", "kafka"));
        profile.setExcludeTitle(List.of("intern"));
        profile.setCompanyTypes(List.of("product", "fintech"));

        var sources = new AppProperties.SourcesConfig();
        sources.getGreenhouse().setEnabled(false);
        sources.getLever().setEnabled(false);
        sources.getAshby().setEnabled(false);
        sources.getAdzuna().setEnabled(false);

        var props = new AppProperties();
        props.setProfiles(List.of(profile));
        props.setSources(sources);
        return props;
    }

    /** Stub DigestBuilder that skips Thymeleaf rendering — only tests the pipeline wiring. */
    private static class StubDigestBuilder extends DigestBuilder {
        StubDigestBuilder() { super(null); }

        @Override
        public DigestEmail build(List<JobMatch> jobMatches, String sourceHealthLine) {
            if (jobMatches == null || jobMatches.isEmpty()) return null;
            List<Job> jobs = jobMatches.stream().map(JobMatch::job).toList();
            return DigestEmail.of("stub subject", "<html/>", "stub text",
                    jobs, Map.of("all", jobs.size()), sourceHealthLine);
        }
    }

    /** Email sender stub that either always succeeds or always fails. */
    private static class CapturingEmailSender extends EmailSender {
        private final boolean shouldSucceed;
        DigestEmail lastDigest;

        CapturingEmailSender(boolean shouldSucceed) {
            super(null, new com.JobHunt.appJob.notify.EmailProperties(null, null));
            this.shouldSucceed = shouldSucceed;
        }

        @Override
        public boolean send(DigestEmail digest) {
            this.lastDigest = digest;
            return shouldSucceed;
        }
    }

    /** Stub {@link JobSource} backed by a mutable list. */
    private record StubSource(String name, List<Job> jobs) implements JobSource {
        @Override public List<Job> fetchJobs() { return List.copyOf(jobs); }
    }
}





